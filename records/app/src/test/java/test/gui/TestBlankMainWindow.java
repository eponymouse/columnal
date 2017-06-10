package test.gui;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.gui.MainWindow;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.gui.FXUtility;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Created by neil on 10/06/2017.
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
public class TestBlankMainWindow extends ApplicationTest
{
    @SuppressWarnings("nullness")
    private @NonNull Stage mainWindow;

    @Override
    public void start(Stage stage) throws Exception
    {
        File dest = File.createTempFile("blank", "rec");
        dest.deleteOnExit();
        MainWindow.show(stage, dest, null);
        mainWindow = stage;
    }

    @After
    @OnThread(Tag.Any)
    public void hide()
    {
        Platform.runLater(() -> {
            // Take a copy to avoid concurrent modification:
            new ArrayList<>(MainWindow._test_getViews().values()).forEach(Stage::hide);
        });
    }

    // Both a test, and used as utility method.
    @Test
    public void testStartState()
    {
        assertTrue(mainWindow.isShowing());
        assertEquals(1, MainWindow._test_getViews().size());
    }

    public void testNewClick()
    {
        testStartState();
        clickOn("#id-menu-project").clickOn(".id-menu-project-new");
        assertEquals(2, MainWindow._test_getViews().size());
        assertTrue(MainWindow._test_getViews().values().stream().allMatch(Stage::isShowing));
    }
}
