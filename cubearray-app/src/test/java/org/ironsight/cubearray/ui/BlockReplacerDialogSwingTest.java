package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.swing.edt.GuiActionRunner;
import javax.swing.JButton;
import org.junit.Test;

public class BlockReplacerDialogSwingTest extends SwingTestSupport {
  private static final Set<String> PALETTE =
      Set.of("minecraft:oak_planks", "minecraft:stone", "minecraft:dirt");
  private static final Set<String> AVAILABLE =
      Set.of("minecraft:oak_planks", "minecraft:stone", "minecraft:birch_planks");

  @Test
  public void switchesBetweenBlockAndDetailModesAndFiltersRows() throws Exception {
    Optional<BlockReplacerDialog.ReplaceResult> result =
        showDialog(
            () -> BlockReplacerDialog.show(null, PALETTE, AVAILABLE, Map.of()),
            "Replace blocks",
            dialog -> {
              dialog.button("modeButton").requireText("Blocks");
              GuiActionRunner.execute(
                  () -> ((JButton) dialog.button("modeButton").target()).doClick());
              dialog.button("modeButton").requireText("Details");
              dialog.textBox("searchField").enterText("oak");
              dialog.button("cancelButton").click();
            });

    assertThat(result).isEmpty();
  }

  @Test
  public void cancelDoesNotProduceReplacement() throws Exception {
    Optional<BlockReplacerDialog.ReplaceResult> result =
        showDialog(
            () ->
                BlockReplacerDialog.show(
                    null, Set.of("minecraft:stone"), Set.of("minecraft:dirt"), Map.of()),
            "Replace blocks",
            dialog -> dialog.button("cancelButton").click());

    assertThat(result).isEmpty();
  }
}
