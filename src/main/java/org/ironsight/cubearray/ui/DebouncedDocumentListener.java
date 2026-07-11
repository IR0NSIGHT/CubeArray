package org.ironsight.cubearray.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class DebouncedDocumentListener implements DocumentListener {
  public static final String PROP_SEARCHING = "searching";

  private final Timer timer;
  private final Runnable action;
  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private boolean searching;

  public DebouncedDocumentListener(int delayMs, Runnable action) {
    this.action = action;
    timer = new Timer(delayMs, e -> fire());
    timer.setRepeats(false);
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

  private void setSearching(boolean v) {
    boolean old = searching;
    searching = v;
    pcs.firePropertyChange(PROP_SEARCHING, old, v);
  }

  private void restart() {
    setSearching(true);
    timer.restart();
  }

  private void fire() {
    try {
      action.run();
    } finally {
      setSearching(false);
    }
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
