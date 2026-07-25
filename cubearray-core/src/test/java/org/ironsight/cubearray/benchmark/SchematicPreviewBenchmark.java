package org.ironsight.cubearray.benchmark;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.preview.SchematicPreviewGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SchematicPreviewBenchmark {

    @Param({
        "src/test/resources/schematics/Dannypan/house_1.schem",
        "src/test/resources/schematics/Dannypan/smithy.schem",
        "src/test/resources/schematics/Ir0nsight/jerusalem_wall_arch_gates.schem"
    })
    public String schematicPath;

    private SchematicPreviewGenerator previewGenerator;
    private File schematicFile;
    private File placeholderFile;
    private File thumbnailFile;
    private File singleRenderFile;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

        previewGenerator = SchematicPreviewGenerator.getInstance();

        schematicFile = Path.of(schematicPath).toFile();

        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        placeholderFile = tmp.resolve("cubearray-bench-placeholder.schem").toFile();
        thumbnailFile = tmp.resolve("cubearray-bench-thumbnail.schem").toFile();
        singleRenderFile = tmp.resolve("cubearray-bench-single-render.schem").toFile();

        createAngleRenders(schematicFile, 4);
        createThumbnail(schematicFile);

        createThumbnail(thumbnailFile);
        createRender(singleRenderFile);

        previewGenerator.invalidateIcon(schematicFile);
        previewGenerator.invalidateIcon(placeholderFile);
        previewGenerator.invalidateIcon(thumbnailFile);
        previewGenerator.invalidateIcon(singleRenderFile);

        previewGenerator.getIcon(schematicFile);
    }

    @Benchmark
    public Icon cachedIcon() {
        return previewGenerator.getIcon(schematicFile);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 3)
    public Icon coldMultiAngleComposite() {
        previewGenerator.invalidateIcon(schematicFile);
        return previewGenerator.getIcon(schematicFile);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 3)
    public Icon coldThumbnailFallback() {
        previewGenerator.invalidateIcon(thumbnailFile);
        return previewGenerator.getIcon(thumbnailFile);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 3)
    public Icon coldSingleRenderFallback() {
        previewGenerator.invalidateIcon(singleRenderFile);
        return previewGenerator.getIcon(singleRenderFile);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 3)
    public Icon coldPlaceholder() {
        previewGenerator.invalidateIcon(placeholderFile);
        return previewGenerator.getIcon(placeholderFile);
    }

    private static void createAngleRenders(File file, int count) throws Exception {
        Path renderPath = ResourceUtils.getRenderPathForFile(file);
        Files.createDirectories(renderPath.getParent());
        for (int i = 0; i < count; i++) {
            Path p = renderPath.resolveSibling(
                insertSuffix(renderPath.getFileName().toString(), "_" + i));
            BufferedImage img = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0x22, 0x44, 0x66 + i * 16));
            g.fillRect(0, 0, 640, 640);
            g.dispose();
            ImageIO.write(img, "PNG", p.toFile());

            Path tp = ResourceUtils.getThumbPathForFile(file).resolveSibling(
                insertSuffix(ResourceUtils.getThumbPathForFile(file).getFileName().toString(), "_" + i));
            BufferedImage t = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            g = t.createGraphics();
            g.setColor(new Color(0x22, 0x44, 0x66 + i * 24));
            g.fillRect(0, 0, 64, 64);
            g.dispose();
            ImageIO.write(t, "PNG", tp.toFile());
        }
    }

    private static void createThumbnail(File file) throws Exception {
        Path thumbPath = ResourceUtils.getThumbPathForFile(file);
        Files.createDirectories(thumbPath.getParent());
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x22, 0x44, 0x66));
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        ImageIO.write(img, "PNG", thumbPath.toFile());
    }

    private static void createRender(File file) throws Exception {
        Path renderPath = ResourceUtils.getRenderPathForFile(file);
        Files.createDirectories(renderPath.getParent());
        BufferedImage img = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x22, 0x44, 0x66));
        g.fillRect(0, 0, 640, 640);
        g.dispose();
        ImageIO.write(img, "PNG", renderPath.toFile());
    }

    private static String insertSuffix(String filename, String suffix) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? filename + suffix
                         : filename.substring(0, dot) + suffix + filename.substring(dot);
    }
}
