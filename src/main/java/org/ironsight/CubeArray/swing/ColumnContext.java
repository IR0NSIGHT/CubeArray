package org.ironsight.CubeArray.swing;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public record ColumnContext(
        List<CaColumn> displayedColumns,
        List<Integer> columnWidths,
        CaColumn orderedColumn,
        boolean orderAscending
) implements Serializable {

    public ColumnContext {
        // Defensive copy + immutable wrapper
        displayedColumns = List.copyOf(displayedColumns);
        columnWidths = List.copyOf(columnWidths);
    }

    public ColumnContext() {
        this(
                List.of(CaColumn.values()),
                Arrays.stream(CaColumn.values())
                        .map(c -> c.defaultWidth)
                        .toList(),
                CaColumn.FILE,
                false
        );
    }

    public ColumnContext copy() {
        // No need to copy lists anymore — they're already immutable
        return new ColumnContext(
                this.displayedColumns,
                this.columnWidths,
                this.orderedColumn,
                this.orderAscending
        );
    }
}