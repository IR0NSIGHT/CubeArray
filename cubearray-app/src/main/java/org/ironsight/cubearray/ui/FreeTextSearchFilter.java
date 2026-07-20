package org.ironsight.cubearray.ui;

class FreeTextSearchFilter extends AbstractSearchFilter {
    private volatile String searchText = "";

    FreeTextSearchFilter(FileTableModel model, AppContext context) {
        super(model, context);
    }

    void setSearchText(String text) {
        System.out.println("[free-text-search] setSearchText \"" + text + "\"");
        searchText = text.toLowerCase();
        markAllDirty();
    }

    @Override
    protected boolean isActive() {
        return !searchText.isEmpty();
    }

    @Override
    protected boolean rowMatches(int row) {
        String text = searchText;
        for (CaColumn c : context.columnContext().displayedColumns()) {
            Object raw = model.getValueAt(row, c.ordinal());
            if (raw == null) continue;
            String val = c.renderer.convertToString(raw).toLowerCase();
            if (val.contains(text)) return true;
        }
        return false;
    }

    @Override
    protected String filterThreadName() {
        return "free-text-search";
    }
}
