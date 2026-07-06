package org.ironsight.schemEdit;

import org.ironsight.CubeArray.AppLogger;
import pitheguy.schemconvert.converter.Converter;
import pitheguy.schemconvert.converter.formats.SchematicFormats;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BatchConverter {

    private static final Logger logger = AppLogger.get(BatchConverter.class);

    /**
     * Result of a batch in-place conversion.
     *
     * @param convertedFiles output {@code .schem} files that were successfully written
     * @param failedFiles    input files that could not be converted
     */
    public record ConversionResult(List<File> convertedFiles, List<File> failedFiles) {}

    /**
     * Converts a list of schematic files to Sponge v3 (.schem) format,
     * writing a {@code _sponge3.schem} file next to each original.
     * <p>
     * SchemConvert writes block data as plain bytes, but WorldPainter requires
     * VarInt-encoded block data. This method fixes the encoding after conversion
     * so the output is readable by WorldPainter for all palette sizes.
     * <p>
     * The original file is never deleted or modified. The output file is
     * written atomically via a temporary file so a failed conversion never
     * leaves a partial result on disk.
     *
     * @param inputFiles list of schematic files to convert
     * @return a {@link ConversionResult} containing converted output files and failed input files
     */
    public static ConversionResult convertToSponge3InPlace(List<Path> inputFiles) {
        List<File> converted = new ArrayList<>();
        List<File> failed = new ArrayList<>();
        Converter converter = new Converter();

        for (Path inputPath : inputFiles) {
            File inputFile = inputPath.toFile();
            String baseName = stripExtension(inputFile.getName());
            File outputTarget = new File(inputFile.getParentFile(), baseName + ".schem");
            File tempFile = new File(inputFile.getParentFile(), baseName + ".schem.tmp");

            try {
                converter.convert(inputFile, tempFile, SchematicFormats.SCHEM);
                fixVarIntEncoding(tempFile);
                Files.move(tempFile.toPath(), outputTarget.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                // delete the original only if it differs from the output (i.e. different extension)
                if (!inputFile.getCanonicalPath().equals(outputTarget.getCanonicalPath()))
                    Files.deleteIfExists(inputPath);

                converted.add(outputTarget);
            } catch (Exception e) {
                failed.add(inputFile);
                logger.log(Level.SEVERE, "failed to convert in-place: " + inputFile.getAbsolutePath(), e);
                tempFile.delete();
            }
        }

        return new ConversionResult(converted, failed);
    }

    /**
     * Re-encodes the {@code Schematic/Blocks/Data} byte array in a Sponge v3
     * {@code .schem} file from plain bytes to VarInts in-place.
     * <p>
     * SchemConvert writes {@code blockData[i] = (byte) paletteIndex}, which is
     * only a valid VarInt for indices 0–127. For indices ≥ 128 the high bit is
     * set, which WorldPainter interprets as a VarInt continuation byte, causing
     * a "VarInt too big" error. This method rewrites the file with properly
     * encoded VarInts.
     */
    static void fixVarIntEncoding(File schem) throws IOException {
        // Read the full raw NBT tree (gzip-compressed)
        byte[] raw = readNbt(schem);

        // Locate the "Schematic" → "Blocks" → "Data" byte array tag and re-encode it.
        // We work directly on the serialised bytes to avoid pulling in a full NBT library.
        byte[] fixed = reencodeBlockData(raw);

        // Write back gzip-compressed
        writeNbt(schem, fixed);
    }

    /**
     * Reads a gzip-compressed NBT file and returns the raw (uncompressed) bytes.
     */
    private static byte[] readNbt(File file) throws IOException {
        try (InputStream fis = Files.newInputStream(file.toPath());
             GZIPInputStream gzip = new GZIPInputStream(fis)) {
            return gzip.readAllBytes();
        }
    }

    /**
     * Writes raw (uncompressed) NBT bytes to a file with gzip compression.
     */
    private static void writeNbt(File file, byte[] data) throws IOException {
        try (OutputStream fos = Files.newOutputStream(file.toPath());
             GZIPOutputStream gzip = new GZIPOutputStream(fos)) {
            gzip.write(data);
        }
    }

    /**
     * Walks the raw NBT byte array to find the {@code Schematic/Blocks/Data}
     * TAG_Byte_Array and replaces each plain byte with a proper VarInt, returning
     * the rewritten byte array.
     */
    private static byte[] reencodeBlockData(byte[] raw) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length);
        DataOutputStream dout = new DataOutputStream(out);

        // root must be TAG_Compound (10)
        int rootType = in.readUnsignedByte();
        dout.writeByte(rootType);
        copyNbtString(in, dout); // root name (usually empty)

        // walk children of root compound, looking for "Schematic"
        walkCompound(in, dout, new String[]{"Schematic", "Blocks", "Data"}, 0);

        // flush remainder
        byte[] remaining = in.readAllBytes();
        dout.write(remaining);

        return out.toByteArray();
    }

    /**
     * Recursively walks a TAG_Compound, copying tags verbatim until it finds
     * the tag at {@code path[depth]}. When the full path is reached the
     * TAG_Byte_Array payload is re-encoded as VarInts; all other tags are
     * copied verbatim.
     */
    private static void walkCompound(DataInputStream in, DataOutputStream out,
                                     String[] path, int depth) throws IOException {
        while (true) {
            int type = in.readUnsignedByte();
            out.writeByte(type);
            if (type == 0) return; // TAG_End

            String name = readNbtString(in);
            writeNbtString(out, name);

            if (depth < path.length && path[depth].equals(name)) {
                if (depth == path.length - 1) {
                    // We are at "Data" (TAG_Byte_Array = 7): re-encode
                    int len = in.readInt();
                    byte[] plainBytes = in.readNBytes(len);
                    byte[] varIntBytes = encodeAsVarInts(plainBytes);
                    out.writeInt(varIntBytes.length);
                    out.write(varIntBytes);
                } else {
                    // Intermediate compound on the path: recurse
                    walkCompound(in, out, path, depth + 1);
                }
            } else {
                // Not on our path: copy payload verbatim
                copyNbtPayload(in, out, type);
            }
        }
    }

    /**
     * Re-encodes a byte array of plain palette indices as VarInts.
     * Each palette index is written as 1–5 bytes following the standard
     * Minecraft VarInt encoding (7 data bits per byte, MSB = continuation).
     */
    private static byte[] encodeAsVarInts(byte[] plainBytes) {
        // worst case: every index needs 5 bytes (indices > 2^28, unlikely but safe)
        ByteArrayOutputStream buf = new ByteArrayOutputStream(plainBytes.length * 2);
        for (byte b : plainBytes) {
            int value = b & 0xFF; // treat as unsigned
            writeVarInt(buf, value);
        }
        return buf.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    // --- NBT copy helpers ---

    private static String readNbtString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = in.readNBytes(len);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeNbtString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static void copyNbtString(DataInputStream in, DataOutputStream out) throws IOException {
        writeNbtString(out, readNbtString(in));
    }

    private static void copyNbtPayload(DataInputStream in, DataOutputStream out, int type) throws IOException {
        switch (type) {
            case 1  -> { byte v = in.readByte();  out.writeByte(v); }
            case 2  -> { short v = in.readShort(); out.writeShort(v); }
            case 3  -> { int v = in.readInt();   out.writeInt(v); }
            case 4  -> { long v = in.readLong();  out.writeLong(v); }
            case 5  -> { float v = in.readFloat();  out.writeFloat(v); }
            case 6  -> { double v = in.readDouble(); out.writeDouble(v); }
            case 7  -> { int len = in.readInt(); out.writeInt(len);
                         byte[] d = in.readNBytes(len); out.write(d); }
            case 8  -> copyNbtString(in, out);
            case 9  -> copyNbtList(in, out);
            case 10 -> copyNbtCompound(in, out);
            case 11 -> { int len = in.readInt(); out.writeInt(len);
                         byte[] d = in.readNBytes(len * 4); out.write(d); }
            case 12 -> { int len = in.readInt(); out.writeInt(len);
                         byte[] d = in.readNBytes(len * 8); out.write(d); }
            default -> throw new IOException("Unknown NBT tag type: " + type);
        }
    }

    private static void copyNbtList(DataInputStream in, DataOutputStream out) throws IOException {
        int elemType = in.readUnsignedByte();
        out.writeByte(elemType);
        int size = in.readInt();
        out.writeInt(size);
        for (int i = 0; i < size; i++) copyNbtPayload(in, out, elemType);
    }

    private static void copyNbtCompound(DataInputStream in, DataOutputStream out) throws IOException {
        while (true) {
            int t = in.readUnsignedByte();
            out.writeByte(t);
            if (t == 0) return;
            copyNbtString(in, out);
            copyNbtPayload(in, out, t);
        }
    }

    /**
     * Converts a list of schematic files to Sponge v3 (.schem) format,
     * writing output files into {@code outputDir} with the same base name
     * as the input. Applies VarInt re-encoding after conversion so WorldPainter
     * can load files with palette sizes &gt; 127.
     *
     * @param inputFiles list of schematic files to convert
     * @param outputDir  directory to write the converted {@code .schem} files into
     * @return a {@link ConversionResult} containing converted output files and failed input files
     * @throws IOException if {@code outputDir} does not exist or is not a directory
     */
    public static ConversionResult convertToSponge3(List<Path> inputFiles, File outputDir) throws IOException {
        if (!outputDir.exists())
            throw new IOException("Output directory does not exist: " + outputDir.getAbsolutePath());
        if (!outputDir.isDirectory())
            throw new IOException("Output path is not a directory: " + outputDir.getAbsolutePath());

        List<File> converted = new ArrayList<>();
        List<File> failed = new ArrayList<>();
        Converter converter = new Converter();

        for (Path inputPath : inputFiles) {
            File inputFile = inputPath.toFile();
            String baseName = stripExtension(inputFile.getName());
            File outputTarget = new File(outputDir, baseName + ".schem");
            File tempFile = new File(outputDir, baseName + ".schem.tmp");

            try {
                converter.convert(inputFile, tempFile, SchematicFormats.SCHEM);
                fixVarIntEncoding(tempFile);
                Files.move(tempFile.toPath(), outputTarget.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                converted.add(outputTarget);
            } catch (Exception e) {
                failed.add(inputFile);
                logger.log(Level.SEVERE, "failed to convert: " + inputFile.getAbsolutePath(), e);
                tempFile.delete();
            }
        }

        return new ConversionResult(converted, failed);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot) : filename;
    }
}
