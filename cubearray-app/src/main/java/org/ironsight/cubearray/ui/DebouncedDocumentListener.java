package org.ironsight.cubearray.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class DebouncedDocumentListener implements DocumentListener {
  public static final String PROP_SEARCHING = "searching";

  private final Timer timer;
  private final Runnable action;
  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private final ExecutorService executor;
  private final AtomicInteger generation = new AtomicInteger(0);
  private volatile boolean searching;

  public DebouncedDocumentListener(int delayMs, Runnable action) {
    this.action = action;
    timer = new Timer(delayMs, e -> fire());
    timer.setRepeats(false);
    executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "search-worker");
      t.setDaemon(true);
      return t;
    });
  }

  public static DebouncedDocumentListener create(int delayMs, Runnable action) {
    return new DebouncedDocumentListener(delayMs, action);
  }

  public boolean isSearching() {
    return searching;
  }

  public void addPropertyChangeListener(String property, PropertyChangeListener listener) {
    pcs.addPropertyChangeListener(property, listener);
  }

  public void removePropertyChangeListener(String property, PropertyChangeListener listener) {
    pcs.removePropertyChangeListener(property, listener);
  }

  public void shutdown() {
    executor.shutdownNow();
  }

  private void setSearching(boolean v) {
    boolean old = searching;
    searching = v;
    pcs.firePropertyChange(PROP_SEARCHING, old, v);
  }

  private void restart() {
    generation.incrementAndGet();
    setSearching(true);
    timer.restart();
  }

  private void fire() {
    int gen = generation.get();
    executor.submit(() -> {
      try {
        action.run();
      } finally {
        SwingUtilities.invokeLater(() -> {
          if (gen == generation.get()) {
            setSearching(false);
          }
        });
      }
    });
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    restart();
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    restart();
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    restart();
  }
}
