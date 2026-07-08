package org.ironsight.CubeArray.swing;

import static org.junit.Assert.*;

import javax.swing.Icon;
import org.junit.Test;

public class BlockIconProviderTest {

  private final BlockIconProvider provider = new BlockIconProvider(32, 20);

  @Test
  public void findsIconByBareId() {
    Icon icon = provider.getIcon("oak_planks");
    assertNotNull(icon);
    assertEquals(32, icon.getIconWidth());
    assertEquals(32, icon.getIconHeight());
  }

  @Test
  public void findsIconByNamespacedId() {
    Icon icon = provider.getIcon("minecraft:oak_planks");
    assertNotNull(icon);
    assertEquals(32, icon.getIconWidth());
    assertEquals(32, icon.getIconHeight());
  }

  @Test
  public void findsIconByFullBlockState() {
    Icon icon = provider.getIcon("minecraft:oak_planks[beep=boop,ding=dong]");
    assertNotNull(icon);
    assertEquals(32, icon.getIconWidth());
    assertEquals(32, icon.getIconHeight());
  }

  @Test
  public void sameKeyNormalizationReturnsCachedInstance() {
    Icon a = provider.getIcon("oak_planks");
    Icon b = provider.getIcon("minecraft:oak_planks");
    Icon c = provider.getIcon("minecraft:oak_planks[beep=boop,ding=dong]");
    assertSame(a, b);
    assertSame(a, c);
  }

  @Test
  public void unknownBlockReturnsUnknownIcon() {
    Icon icon = provider.getIcon("this_does_not_exist");
    assertNotNull(icon);
    assertEquals(32, icon.getIconWidth());
    assertEquals(32, icon.getIconHeight());
    // Unknown icon should be a different instance from a real one
    Icon oak = provider.getIcon("oak_planks");
    assertNotNull(oak);
    assertNotSame(oak, icon);
  }

  @Test
  public void unknownIconIsCached() {
    Icon a = provider.getIcon("this_does_not_exist");
    Icon b = provider.getIcon("some_other_missing_block");
    assertSame(a, b);
  }

  @Test
  public void getIconSmallReturnsNullForMissing() {
    assertNull(provider.getIconSmall("this_does_not_exist"));
  }

  @Test
  public void getIconSmallReturnsCorrectSize() {
    Icon icon = provider.getIconSmall("oak_planks");
    assertNotNull(icon);
    assertEquals(20, icon.getIconWidth());
    assertEquals(20, icon.getIconHeight());
  }
}
