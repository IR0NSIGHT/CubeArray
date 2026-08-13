package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Optional;
import org.assertj.swing.fixture.DialogFixture;
import org.junit.Test;

public class OutputOptionsDialogSwingTest extends SwingTestSupport {
  @Test
  public void confirmsSuffixOptionsAndUpdatesExample() throws Exception {
    File source = new File("source.schem");

    Optional<OutputOptionsDialog.Options> options =
        showDialog(
            () -> OutputOptionsDialog.show(null, source),
            "Output options",
            dialog -> confirmSuffix(dialog));

    assertThat(options).isPresent();
    assertThat(options.orElseThrow().affix()).isEqualTo("_copy");
    assertThat(options.orElseThrow().isPrefix()).isFalse();
    assertThat(options.orElseThrow().outputFileFor(source))
        .isEqualTo(new File("source_copy.schem"));
  }

  private static void confirmSuffix(DialogFixture dialog) {
    dialog.textBox("postfixField").deleteText().enterText("_copy");
    dialog.label("exampleLabel").requireText("source_copy.schem");
    dialog.button("okButton").click();
  }

  @Test
  public void cancelReturnsEmpty() throws Exception {
    Optional<OutputOptionsDialog.Options> options =
        showDialog(
            () -> OutputOptionsDialog.show(null, new File("source.schem")),
            "Output options",
            dialog -> dialog.button("cancelButton").click());

    assertThat(options).isEmpty();
  }
}
