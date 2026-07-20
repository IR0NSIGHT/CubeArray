package org.ironsight.cubearray.ui;

class ChipSearchFilter extends AbstractSearchFilter {
    private final CaColumn column;
    private final String searchTerm;

    ChipSearchFilter(FileTableModel model, CaColumn column, String searchTerm) {
        super(model, null);
        this.column = column;
        this.searchTerm = searchTerm.toLowerCase();
        System.out.println("[chip-search-" + column.name() + "] created term=\"" + searchTerm + "\"");
    }

    @Override
    protected boolean isActive() {
        return true;
    }

    @Override
    protected boolean rowMatches(int row) {
        Object raw = model.getValueAt(row, column.ordinal());
        if (raw == null) return false;
        String cellValue = column.renderer.convertToString(raw).toLowerCase();
        return cellValue.contains(searchTerm);
    }

    @Override
    protected String filterThreadName() {
        return "chip-search-" + column.name();
    }
}
