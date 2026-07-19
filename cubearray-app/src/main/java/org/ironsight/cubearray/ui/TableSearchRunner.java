package org.ironsight.cubearray.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class TableSearchRunner {
  private TableSearchRunner() {}

  static Set<Integer> computeMatchingRows(
      FileTableModel model,
      AppContext context,
      List<ChipSearchManager.SearchCondition> conditions,
      String plainText)
      throws InterruptedException {
    boolean hasPlainText = !plainText.isEmpty();
    boolean hasChips = !conditions.isEmpty();

    if (!hasPlainText && !hasChips) {
      return Set.of();
    }

    Set<Integer> matchingRows = new HashSet<>();
    int rowCount = model.getRowCount();
    for (int row = 0; row < rowCount; row++) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

      if (hasPlainText) {
        boolean found = false;
        for (CaColumn c : context.columnContext().displayedColumns()) {
          if (c.renderer
              .convertToString(model.getValueAt(row, c.ordinal()))
              .toLowerCase()
              .contains(plainText)) {
            found = true;
            break;
          }
        }
        if (!found) continue;
      }

      boolean matchesChips = true;
      for (ChipSearchManager.SearchCondition cond : conditions) {
        String cellValue =
            cond.column()
                .renderer
                .convertToString(model.getValueAt(row, cond.column().ordinal()))
                .toLowerCase();
        if (!cellValue.contains(cond.searchTerm().toLowerCase())) {
          matchesChips = false;
          break;
        }
      }
      if (!matchesChips) continue;

      matchingRows.add(row);
    }
    return matchingRows;
  }
}
