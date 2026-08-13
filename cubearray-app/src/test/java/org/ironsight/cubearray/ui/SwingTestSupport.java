package org.ironsight.cubearray.ui;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.DialogFixture;
import org.junit.After;
import org.junit.Before;

abstract class SwingTestSupport {
  private Robot robot;

  @Before
  public void setUpSwing() {
    // NewHierarchy references the removed java.applet API on Java 21. The application
    // starts with no pre-existing windows, so the current hierarchy is equivalent here.
    robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock();
  }

  @After
  public void tearDownSwing() {
    if (robot != null) robot.cleanUp();
  }

  protected Robot robot() {
    return robot;
  }

  protected <T> Optional<T> showDialog(
      Supplier<Optional<T>> show, String title, DialogFixtureConsumer<T> test) throws Exception {
    AtomicReference<Optional<T>> result = new AtomicReference<>();
    Thread dialogThread = new Thread(() -> result.set(show.get()), "swing-dialog-test");
    dialogThread.start();

    DialogFixture dialog = WindowFinder.findDialog(findDialogClass(title)).using(robot());
    try {
      test.accept(dialog);
    } finally {
      dialogThread.join(5000);
      if (dialogThread.isAlive()) {
        dialog.close();
        dialogThread.join(5000);
      }
    }

    if (dialogThread.isAlive()) {
      throw new AssertionError("Dialog did not close: " + title);
    }
    return result.get();
  }

  private static Class<? extends java.awt.Dialog> findDialogClass(String title) {
    return switch (title) {
      case "Output options" -> OutputOptionsDialog.class;
      case "Replace blocks" -> BlockReplacerDialog.class;
      case "Block Gradient" -> BlockTableDialog.class;
      default -> throw new IllegalArgumentException("Unknown test dialog: " + title);
    };
  }

  @FunctionalInterface
  protected interface DialogFixtureConsumer<T> {
    void accept(DialogFixture dialog) throws Exception;
  }
}
