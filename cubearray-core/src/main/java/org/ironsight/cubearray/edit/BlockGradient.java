package org.ironsight.cubearray.edit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class BlockGradient {

    public record GradientRow(String block, int yPos, int chance) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<GradientRow> sortedRows;
    private final Random random = new Random();

    public BlockGradient(List<GradientRow> rows) {
        this.sortedRows = rows.stream()
            .sorted(Comparator.comparingInt(GradientRow::yPos))
            .toList();
    }

    public String getBlock(int y) {
        if (sortedRows.isEmpty()) return "minecraft:air";

        if (y <= sortedRows.getFirst().yPos()) {
            return sortedRows.getFirst().block();
        }
        if (y >= sortedRows.getLast().yPos()) {
            return sortedRows.getLast().block();
        }

        GradientRow low = null, high = null;
        for (int i = 0; i < sortedRows.size(); i++) {
            GradientRow row = sortedRows.get(i);
            if (row.yPos() <= y) {
                low = row;
            } else if (high == null) {
                high = row;
                break;
            }
        }

        if (low == null || high == null) {
            return sortedRows.getFirst().block();
        }

        float t = (y - low.yPos()) / (float) (high.yPos() - low.yPos());
        float lowWeight = (1.0f - t) * low.chance();
        float highWeight = t * high.chance();
        float total = lowWeight + highWeight;

        if (total == 0) return low.block();

        return random.nextFloat() * total < lowWeight ? low.block() : high.block();
    }

    public static void save(Path path, List<GradientRow> rows) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), rows);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save gradient to " + path, e);
        }
    }

    public static List<GradientRow> load(Path path) {
        if (!path.toFile().exists()) return List.of();
        try {
            return MAPPER.readValue(path.toFile(), new TypeReference<List<GradientRow>>() {});
        } catch (IOException e) {
            return List.of();
        }
    }
}
