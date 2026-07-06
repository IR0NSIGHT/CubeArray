package org.ironsight.schemEdit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Replacer {

  private final Map<String, String> blockToCategory;
  private final Map<String, List<String>> categoryToBlocks;

  public Replacer(List<BlockListUtil.CategoryEntry> categories) {
    blockToCategory = new LinkedHashMap<>();
    categoryToBlocks = new LinkedHashMap<>();

    for (var entry : categories) {
      categoryToBlocks.put(entry.id(), entry.blocks());
      for (String block : entry.blocks()) {
        blockToCategory.put(block, entry.id());
      }
    }
  }

  /**
   * Expands a category-level mapping into a bare block ID → bare block ID map.
   *
   * <p>For each entry {@code srcCat → tgtCat}, every block whose category is {@code srcCat} is
   * mapped to its equivalent block in {@code tgtCat}. Blocks with no equivalent in the target
   * category are silently skipped. Identity mappings (where {@code srcCat == tgtCat}) are also
   * skipped.
   *
   * <p>Blocks whose bare ID equals the source category ID (i.e. the category base block) are mapped
   * directly to the target category ID.
   *
   * @param categoryMapping bare source category → bare target category
   * @return unmodifiable map of bare block ID → bare block ID
   */
  public Map<String, String> expandCategoryMapping(Map<String, String> categoryMapping) {
    Map<String, String> result = new LinkedHashMap<>();
    for (var entry : categoryMapping.entrySet()) {
      String srcCat = entry.getKey();
      String tgtCat = entry.getValue();
      if (srcCat.equals(tgtCat)) continue;
      for (var be : blockToCategory.entrySet()) {
        String block = be.getKey();
        String cat = be.getValue();
        if (!cat.equals(srcCat)) continue;
        // Category base block → target category base block directly
        if (block.equals(srcCat)) {
          result.put(block, tgtCat);
          continue;
        }
        try {
          result.put(block, replaceBlockByCategory(block, tgtCat));
        } catch (NotFoundExc e) {
          // No equivalent in target category — skip silently
        }
      }
    }
    return Collections.unmodifiableMap(result);
  }

  public String replaceBlockByCategory(String blockIn, String categoryOut) throws NotFoundExc {
    String srcCategory = blockToCategory.get(blockIn);
    if (srcCategory == null) {
      throw new NotFoundExc("Block '" + blockIn + "' is not assigned to any category");
    }

    // Category base block (block equals its own category ID) → target category ID directly
    if (blockIn.equals(srcCategory)) {
      return categoryOut;
    }

    List<String> targetBlocks = categoryToBlocks.get(categoryOut);
    if (targetBlocks == null) {
      throw new NotFoundExc("Category '" + categoryOut + "' is not defined");
    }

    // Phase 1: compare source (raw) against raw targets.
    // Keeps decorating prefixes for tiebreaking.
    String best = findBestRawMatch(blockIn, targetBlocks);
    if (best != null) return best;

    // Phase 2: also strip decorating prefixes from targets.
    String srcStripped = stripPrefixes(blockIn);
    best = findBestPrefixedMatch(srcStripped, targetBlocks);
    if (best != null) return best;

    // Phase 3: strip prefixes AND suffixes from both, match bases.
    // Only used when nothing else works (e.g. copper_block → exposed_copper).
    best = findBestStrippedMatch(srcStripped, targetBlocks);
    if (best != null) return best;

    throw new NotFoundExc(
        "No equivalent block found for '" + blockIn + "' in category '" + categoryOut + "'");
  }

  /** Phase 1: compare raw source against raw targets. */
  private String findBestRawMatch(String source, List<String> targetBlocks) {
    String best = null;
    int bestSuffixLen = -1;
    int bestRemPrefixLen = -1;
    int bestRemSum = Integer.MAX_VALUE;

    for (String target : targetBlocks) {
      int suffixLen = longestCommonSuffixLen(source, target);
      if (suffixLen < 3) continue;

      String aRem = source.substring(0, source.length() - suffixLen);
      String bRem = target.substring(0, target.length() - suffixLen);
      int remPrefixLen = longestCommonPrefixLen(aRem, bRem);
      int remSum = aRem.length() + bRem.length();

      boolean better =
          suffixLen > bestSuffixLen
              || (suffixLen == bestSuffixLen && remPrefixLen > bestRemPrefixLen)
              || (suffixLen == bestSuffixLen
                  && remPrefixLen == bestRemPrefixLen
                  && remSum < bestRemSum);

      if (better) {
        bestSuffixLen = suffixLen;
        bestRemPrefixLen = remPrefixLen;
        bestRemSum = remSum;
        best = target;
      }
    }
    return best;
  }

  /** Phase 2: strip decorating prefixes from targets only. */
  private String findBestPrefixedMatch(String srcStripped, List<String> targetBlocks) {
    String best = null;
    int bestSuffixLen = -1;
    int bestRemPrefixLen = -1;
    int bestRemSum = Integer.MAX_VALUE;

    for (String target : targetBlocks) {
      String tgtStripped = stripPrefixes(target);

      int suffixLen = longestCommonSuffixLen(srcStripped, tgtStripped);
      if (suffixLen < 3) continue;

      String aRem = srcStripped.substring(0, srcStripped.length() - suffixLen);
      String bRem = tgtStripped.substring(0, tgtStripped.length() - suffixLen);
      int remPrefixLen = longestCommonPrefixLen(aRem, bRem);
      int remSum = aRem.length() + bRem.length();

      boolean better =
          suffixLen > bestSuffixLen
              || (suffixLen == bestSuffixLen && remPrefixLen > bestRemPrefixLen)
              || (suffixLen == bestSuffixLen
                  && remPrefixLen == bestRemPrefixLen
                  && remSum < bestRemSum);

      if (better) {
        bestSuffixLen = suffixLen;
        bestRemPrefixLen = remPrefixLen;
        bestRemSum = remSum;
        best = target;
      }
    }
    return best;
  }

  /**
   * Phase 3: strip prefixes AND suffixes from both. Exact base match required when suffixes differ.
   */
  private String findBestStrippedMatch(String srcStripped, List<String> targetBlocks) {
    String srcBase = stripSuffix(srcStripped);
    String srcSfx = srcStripped.equals(srcBase) ? "" : srcStripped.substring(srcBase.length());

    String best = null;
    int bestBaseLen = -1;
    int bestSfxMatch = -1;
    int bestPrefMatch = -1;
    int bestPrefCount = Integer.MAX_VALUE;

    for (String target : targetBlocks) {
      String tgtStripped = stripPrefixes(target);
      String tgtBase = stripSuffix(tgtStripped);
      String tgtSfx = tgtStripped.equals(tgtBase) ? "" : tgtStripped.substring(tgtBase.length());

      int baseSuffixLen = longestCommonSuffixLen(srcBase, tgtBase);
      if (baseSuffixLen < 3) continue;

      boolean sameSfx = srcSfx.equals(tgtSfx);
      boolean exactBase = srcBase.equals(tgtBase);
      if (!sameSfx && !exactBase) continue;

      var tgtPref = extractPrefixes(target);
      boolean samePref = extractPrefixes(srcStripped).equals(tgtPref);
      int prefCount = tgtPref.size();

      boolean better =
          baseSuffixLen > bestBaseLen
              || (baseSuffixLen == bestBaseLen && boolToInt(sameSfx) > bestSfxMatch)
              || (baseSuffixLen == bestBaseLen
                  && boolToInt(sameSfx) == bestSfxMatch
                  && boolToInt(samePref) > bestPrefMatch)
              || (baseSuffixLen == bestBaseLen
                  && boolToInt(sameSfx) == bestSfxMatch
                  && boolToInt(samePref) == bestPrefMatch
                  && prefCount < bestPrefCount);

      if (better) {
        bestBaseLen = baseSuffixLen;
        bestSfxMatch = boolToInt(sameSfx);
        bestPrefMatch = boolToInt(samePref);
        bestPrefCount = prefCount;
        best = target;
      }
    }
    return best;
  }

  private static int boolToInt(boolean b) {
    return b ? 1 : 0;
  }

  /** Strips consecutive decorating prefixes, returns the remaining string. */
  private static String stripPrefixes(String s) {
    boolean found;
    do {
      found = false;
      for (String p : BlockListUtil.DECORATING_PREFIXES) {
        if (s.startsWith(p)) {
          s = s.substring(p.length());
          found = true;
          break;
        }
      }
    } while (found);
    return s;
  }

  /** Strips the longest matching functional suffix. */
  private static String stripSuffix(String s) {
    for (String sfx : BlockListUtil.FUNCTIONAL_SUFFIXES) {
      if (s.endsWith(sfx)) {
        return s.substring(0, s.length() - sfx.length());
      }
    }
    return s;
  }

  /** Returns (prefixes, suffix, base) after stripping both. */
  private record Stripped(List<String> prefixes, String suffix, String base) {}

  private static Stripped stripPrefixesAndSuffix(String s) {
    List<String> prefixes = extractPrefixes(s);
    for (String p : prefixes) {
      s = s.substring(p.length());
    }
    String base = stripSuffix(s);
    String suffix = s.equals(base) ? "" : s.substring(base.length());
    return new Stripped(prefixes, suffix, base);
  }

  /** Returns the sorted list of decorating prefixes stripped from the start of s. */
  private static List<String> extractPrefixes(String s) {
    List<String> prefixes = new ArrayList<>();
    boolean found;
    do {
      found = false;
      for (String p : BlockListUtil.DECORATING_PREFIXES) {
        if (s.startsWith(p)) {
          prefixes.add(p);
          s = s.substring(p.length());
          found = true;
          break;
        }
      }
    } while (found);
    Collections.sort(prefixes);
    return prefixes;
  }

  private static int longestCommonSuffixLen(String a, String b) {
    int i = a.length() - 1;
    int j = b.length() - 1;
    int len = 0;
    while (i >= 0 && j >= 0 && a.charAt(i) == b.charAt(j)) {
      len++;
      i--;
      j--;
    }
    return len;
  }

  private static int longestCommonPrefixLen(String a, String b) {
    int len = 0;
    int min = Math.min(a.length(), b.length());
    while (len < min && a.charAt(len) == b.charAt(len)) {
      len++;
    }
    return len;
  }
}
