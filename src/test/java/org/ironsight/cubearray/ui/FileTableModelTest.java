package org.ironsight.cubearray.ui;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;
import org.junit.Test;
import org.ironsight.cubearray.preview.SchematicPreviewHelper;

public class FileTableModelTest {

  @Test
  public void getRowCount() {}

  @Test
  public void getColumnCount() {}

  @Test
  public void getColumnName() {}

  @Test
  public void getValueAt() {}

  @Test
  public void getFileAt() {}

  @Test
  public void addFile() {
    final var model = new FileTableModel(null, SchematicPreviewHelper.getInstance());
    var myFile = new File("~/myFile.txt");
    model.addFile(myFile);
    assertEquals(1, model.getRowCount());
    assertEquals(new File("~/myFile.txt"), model.getFileAt(0));

    // model ignores value-equal file being added again
    model.addFile(new File("~/myFile.txt"));
    model.addFile(new File("~/myFile.txt"));
    model.addFile(new File("~/myFile.txt"));

    assertEquals(1, model.getRowCount());
    assertEquals(new File("~/myFile.txt"), model.getFileAt(0));

    // add 2 more, total = 3
    model.addFile(new File("~/myFile_2.txt"));
    model.addFile(new File("~/myFile_3.txt"));

    assertEquals(3, model.getRowCount());
    assertEquals(new File("~/myFile.txt"), model.getFileAt(0));
    assertEquals(new File("~/myFile_2.txt"), model.getFileAt(1));
    assertEquals(new File("~/myFile_3.txt"), model.getFileAt(2));
  }

  @Test
  public void addFiles() {
    final var model = new FileTableModel(null, SchematicPreviewHelper.getInstance());

    model.addFiles(List.of(new File("~/a.txt"), new File("~/b.txt"), new File("~/c.txt")));
    assertEquals(3, model.getRowCount());
    assertEquals(new File("~/a.txt"), model.getFileAt(0));
    assertEquals(new File("~/b.txt"), model.getFileAt(1));
    assertEquals(new File("~/c.txt"), model.getFileAt(2));

    // duplicates are ignored
    model.addFiles(List.of(new File("~/a.txt"), new File("~/d.txt"), new File("~/a.txt")));
    assertEquals(4, model.getRowCount());
    assertEquals(new File("~/d.txt"), model.getFileAt(3));
  }

  @Test
  public void removeFile() {
    { // 3 items, delete 1
      final var model = new FileTableModel(null, SchematicPreviewHelper.getInstance());
      var myFile = new File("~/myFile.txt");
      model.addFile(myFile);
      model.addFile(new File("~/myFile_2.txt"));
      model.addFile(new File("~/myFile_3.txt"));

      assertEquals(3, model.getRowCount());

      // delete a single row
      model.removeFile(new File("~/myFile_2.txt"));
      assertEquals(2, model.getRowCount());
    }
    { // 3 items, delete all
      final var model = new FileTableModel(null, SchematicPreviewHelper.getInstance());
      model.addFile(new File("~/myFile.txt"));
      model.addFile(new File("~/myFile_2.txt"));
      model.addFile(new File("~/myFile_3.txt"));

      assertEquals(3, model.getRowCount());

      // delete a single row
      model.removeFile(
          new File("~/myFile_2.txt"), new File("~/myFile_3.txt"), new File("~/myFile.txt"));
      assertEquals(0, model.getRowCount());
    }
  }

  @Test
  public void getFiles() {}
}
