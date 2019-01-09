package test.gui;

import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import records.gui.InitialWindow;
import records.gui.MainWindow;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 10/06/2017.
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
public class TestInitialWindow extends FXApplicationTest
{
    private @MonotonicNonNull Stage initialWindow;

    @Override
    public void start(Stage stage) throws Exception
    {
        super.start(stage);
        InitialWindow.show(stage);
        initialWindow = stage;
    }

    @Test
    @RequiresNonNull("initialWindow")
    public void testNew()
    {
        assertTrue(initialWindow.isShowing());
        assertTrue(MainWindow._test_getViews().isEmpty());
        clickOn(".id-initial-new");
        assertFalse(initialWindow.isShowing());
        assertEquals(1, MainWindow._test_getViews().size());
        assertTrue(MainWindow._test_getViews().entrySet().iterator().next().getValue().isShowing());
    }
}
