package org.ironsight.cubearray.ui;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

interface SearchFilter {
    boolean contains(int modelRow);
    void startThread();
    void stop();
    void markAllDirty();
    void markDirty(int row);
    void setOnProgressCallback(IntConsumer callback);
    String getHighlightString(CaColumn column);
}

abstract class AbstractSearchFilter implements SearchFilter {
    protected final FileTableModel model;
    protected final AppContext context;
    private final AtomicBoolean allDirty = new AtomicBoolean(true);
    private final Set<Integer> matchingRows = ConcurrentHashMap.newKeySet();
    private final Set<Integer> dirtyRows = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private Thread thread;
    private IntConsumer onProgress;
    private long lastRowCount = -1;

    protected AbstractSearchFilter(FileTableModel model, AppContext context) {
        this.model = model;
        this.context = context;
    }

    @Override
    public void markAllDirty() {
        allDirty.set(true);
        System.out.println("[" + filterThreadName() + "] markAllDirty");
    }

    @Override
    public void markDirty(int row) {
        dirtyRows.add(row);
        System.out.println("[" + filterThreadName() + "] markDirty row=" + row);
    }

    @Override
    public boolean contains(int modelRow) {
        return !isActive() || matchingRows.contains(modelRow);
    }

    @Override
    public void startThread() {
        if (thread != null) return;
        running = true;
        thread = new Thread(this::loop, filterThreadName());
        thread.setDaemon(true);
        thread.start();
        System.out.println("[" + filterThreadName() + "] thread started");
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
            System.out.println("[" + filterThreadName() + "] thread stopped");
        }
    }

    @Override
    public void setOnProgressCallback(IntConsumer callback) {
        this.onProgress = callback;
    }

    @Override
    public String getHighlightString(CaColumn column) {
        return null;
    }

    private void loop() {
        while (running) {
            int rowCount = model.getRowCount();
            boolean dataChanged = rowCount != lastRowCount;
            lastRowCount = rowCount;
            if (allDirty.compareAndSet(true, false) || dataChanged) {
                recomputeFull(rowCount);
            } else if (!dirtyRows.isEmpty()) {
                recomputeDirtyRows();
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void recomputeFull(int rowCount) {
        System.out.println("[" + filterThreadName() + "] recomputeFull start rows=" + rowCount);
        matchingRows.clear();
        if (!isActive()) {
            reportProgress(0);
            System.out.println("[" + filterThreadName() + "] recomputeFull inactive, cleared");
            return;
        }
        for (int row = 0; row < rowCount; row++) {
            if (!running) {
                System.out.println("[" + filterThreadName() + "] recomputeFull aborted");
                return;
            }
            try {
                if (rowMatches(row)) {
                    matchingRows.add(row);
                }
            } catch (Exception e) {
            }
            if (row % 100 == 99 || row == rowCount - 1) {
                reportProgress(row + 1);
            }
        }
        System.out.println("[" + filterThreadName() + "] recomputeFull done matches=" + matchingRows.size());
    }

    private void recomputeDirtyRows() {
        Set<Integer> copy = new HashSet<>(dirtyRows);
        dirtyRows.clear();
        System.out.println("[" + filterThreadName() + "] recomputeDirtyRows start rows=" + copy);
        for (int row : copy) {
            if (!running) {
                System.out.println("[" + filterThreadName() + "] recomputeDirtyRows aborted");
                return;
            }
            try {
                if (rowMatches(row)) {
                    matchingRows.add(row);
                } else {
                    matchingRows.remove(row);
                }
            } catch (Exception e) {
            }
        }
        System.out.println("[" + filterThreadName() + "] recomputeDirtyRows done matches=" + matchingRows.size());
        reportProgress(0);
    }

    private void reportProgress(int n) {
        IntConsumer cb = onProgress;
        if (cb != null) {
            cb.accept(n);
        }
    }

    protected abstract boolean isActive();
    protected abstract boolean rowMatches(int row);
    protected abstract String filterThreadName();
}
