package org.ironsight.CubeArray.swing;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class FileTableModelTest {

    @Test
    public void getRowCount() {
    }

    @Test
    public void getColumnCount() {
    }

    @Test
    public void getColumnName() {
    }

    @Test
    public void getValueAt() {
    }

    @Test
    public void getFileAt() {
    }

    @Test
    public void addFile() {
        final var model = new FileTableModel();
        var myFile = new File("~/myFile.txt");
        model.addFile(myFile);
        assertEquals(1,model.getRowCount());
        assertEquals(new File("~/myFile.txt"),model.getFileAt(0));

        // model ignores value-equal file being added again
        model.addFile(new File("~/myFile.txt"));
        model.addFile(new File("~/myFile.txt"));
        model.addFile(new File("~/myFile.txt"));

        assertEquals(1,model.getRowCount());
        assertEquals(new File("~/myFile.txt"),model.getFileAt(0));

        // add 2 more, total = 3
        model.addFile(new File("~/myFile_2.txt"));
        model.addFile(new File("~/myFile_3.txt"));

        assertEquals(3,model.getRowCount());
        assertEquals(new File("~/myFile.txt"),model.getFileAt(0));
        assertEquals(new File("~/myFile_2.txt"),model.getFileAt(1));
        assertEquals(new File("~/myFile_3.txt"),model.getFileAt(2));

    }

    @Test
    public void removeFile() {
        {   //3 items, delete 1
            final var model = new FileTableModel();
            var myFile = new File("~/myFile.txt");
            model.addFile(myFile);
            model.addFile(new File("~/myFile_2.txt"));
            model.addFile(new File("~/myFile_3.txt"));

            assertEquals(3, model.getRowCount());

            // delete a single row
            model.removeFile(new File("~/myFile_2.txt"));
            assertEquals(2, model.getRowCount());
        }
        {   //3 items, delete all
            final var model = new FileTableModel();
            model.addFile(new File("~/myFile.txt"));
            model.addFile(new File("~/myFile_2.txt"));
            model.addFile(new File("~/myFile_3.txt"));

            assertEquals(3, model.getRowCount());

            // delete a single row
            model.removeFile(new File("~/myFile_2.txt"),new File("~/myFile_3.txt"), new File("~/myFile.txt"));
            assertEquals(0, model.getRowCount());
        }
    }

    @Test
    public void getFiles() {
    }
}