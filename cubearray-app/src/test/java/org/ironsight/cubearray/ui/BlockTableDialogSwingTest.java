package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.swing.fixture.DialogFixture;
import org.ironsight.cubearray.edit.BlockGradient;
import org.junit.Test;

public class BlockTableDialogSwingTest extends SwingTestSupport {
  @Test
  public void addsAndRemovesGradientRows() throws Exception {
    Optional<BlockGradient> gradient =
        showDialog(
            () ->
                BlockTableDialog.show(
                    null, List.of("minecraft:stone", "minecraft:dirt"), List.of(), Set.of()),
            "Block Gradient",
            this::changeRowsAndConfirm);

    assertThat(gradient).isPresent();
  }

  private void changeRowsAndConfirm(DialogFixture dialog) {
    int initialRows = dialog.table("gradientTable").rowCount();
    dialog.button("addRowButton").click();
    dialog.table("gradientTable").requireRowCount(initialRows + 1);
    dialog.table("gradientTable").selectRows(initialRows);
    dialog.button("removeRowButton").click();
    dialog.table("gradientTable").requireRowCount(initialRows);
    dialog.button("okButton").click();
  }

  @Test
  public void cancelReturnsEmpty() throws Exception {
    Optional<BlockGradient> gradient =
        showDialog(
            () ->
                BlockTableDialog.show(null, List.of("minecraft:stone"), List.<File>of(), Set.of()),
            "Block Gradient",
            dialog -> dialog.button("cancelButton").click());

    assertThat(gradient).isEmpty();
  }
}
