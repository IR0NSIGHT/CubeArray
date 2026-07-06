package org.ironsight.schemEdit;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class BlockListUtilCategoryValidationTest {

    @Test
    public void categories_haveUniquePrefixSuffixKeys() throws IOException {
        var categories = BlockListUtil.loadCategories();
        var errors = new ArrayList<String>();

        for (var cat : categories) {
            var keyToBlocks = new LinkedHashMap<List<String>, List<String>>();

            for (String block : cat.blocks()) {
                var key = extractCategoryBlockKey(block);
                keyToBlocks.computeIfAbsent(key, k -> new ArrayList<>()).add(block);
            }

            for (var entry : keyToBlocks.entrySet()) {
                if (entry.getValue().size() > 1) {
                    errors.add(String.format(
                            "Category '%s': key %s appears %d times: %s",
                            cat.id(), entry.getKey(), entry.getValue().size(), entry.getValue()));
                }
            }
        }

        assertTrue("Duplicate category keys found:\n" + String.join("\n", errors), errors.isEmpty());
    }

    private static List<String> extractCategoryBlockKey(String block) {
        List<String> prefixes = new ArrayList<>();
        String remaining = block;

        boolean found;
        do {
            found = false;
            for (String prefix : BlockListUtil.DECORATING_PREFIXES) {
                if (remaining.startsWith(prefix)) {
                    prefixes.add(prefix);
                    remaining = remaining.substring(prefix.length());
                    found = true;
                    break;
                }
            }
        } while (found);

        Collections.sort(prefixes);

        String suffix = "";
        for (String s : BlockListUtil.FUNCTIONAL_SUFFIXES) {
            if (remaining.endsWith(s)) {
                suffix = s;
                remaining = remaining.substring(0, remaining.length() - s.length());
                break;
            }
        }

        return List.of(String.join("+", prefixes), suffix);
    }
}
