package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.io.File;
import java.util.HashMap;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.After;
import org.junit.Test;

public class FileRenderAppSwingTest extends SwingTestSupport {
  private FileRenderApp app;

  @After
  public void disposeApp() {
    if (app != null) {
      GuiActionRunner.execute(() -> app.frame.dispose());
    }
  }

  @Test
  public void createsMainWindowWithIndependentFileAndFolderViews() {
    AppContext context =
        new AppContext(
            new HashMap<>(),
            new File(System.getProperty("java.io.tmpdir")),
            new Rectangle(0, 0, 900, 650),
            false,
            new ColumnContext(),
            null);

    app = GuiActionRunner.execute(() -> new FileRenderApp(context));
    FrameFixture window = new FrameFixture(robot(), app.frame);
    window.requireTitle("CubeArray");
    window.tabbedPane("mainTabs").requireVisible();
    window.table("fileTable").requireVisible();
    window.textBox("fileSearchField").requireText("");

    assertThat(app.frame.isVisible()).isTrue();
  }

  @Test
  public void headerColumnMenuControlsBothTables() {
    AppContext context =
        new AppContext(
            new HashMap<>(),
            new File(System.getProperty("java.io.tmpdir")),
            new Rectangle(0, 0, 900, 650),
            false,
            new ColumnContext(),
            null);

    app = GuiActionRunner.execute(() -> new FileRenderApp(context));
    FrameFixture window = new FrameFixture(robot(), app.frame);
    JTable fileTable = window.table("fileTable").target();
    JTable folderTable = GuiActionRunner.execute(() -> findTable(app.frame, "folderTable"));
    JPopupMenu fileMenu = fileTable.getTableHeader().getComponentPopupMenu();
    JPopupMenu folderMenu = folderTable.getTableHeader().getComponentPopupMenu();

    assertThat(fileMenu).isNotNull();
    assertThat(folderMenu).isNotNull();
    JCheckBoxMenuItem entities = menuItem(fileMenu, CaColumn.ENTITIES);
    assertThat(entities).isNotNull();
    assertThat(entities.isSelected()).isTrue();

    GuiActionRunner.execute(() -> entities.doClick());

    assertThat(fileTable.convertColumnIndexToView(CaColumn.ENTITIES.ordinal())).isEqualTo(-1);
    assertThat(folderTable.convertColumnIndexToView(CaColumn.ENTITIES.ordinal())).isEqualTo(-1);
  }

  private static JCheckBoxMenuItem menuItem(JPopupMenu menu, CaColumn column) {
    for (java.awt.Component component : menu.getComponents()) {
      if (component instanceof JCheckBoxMenuItem item
          && column.displayName.equals(item.getText())) {
        return item;
      }
    }
    return null;
  }

  private static JTable findTable(Component component, String name) {
    if (component instanceof JTable table && name.equals(table.getName())) return table;
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        JTable table = findTable(child, name);
        if (table != null) return table;
      }
    }
    return null;
  }
}
