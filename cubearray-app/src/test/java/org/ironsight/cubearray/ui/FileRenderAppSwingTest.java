package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import java.io.File;
import java.util.HashMap;
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
    window.textBox("fileSearchField").requireText("Search");

    assertThat(app.frame.isVisible()).isTrue();
  }
}
