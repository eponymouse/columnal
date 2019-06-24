package test.gui.util;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.testfx.api.FxRobot;
import org.testfx.api.FxRobotInterface;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.error.InternalException;
import test.TestUtil;
import test.gui.trait.FocusOwnerTrait;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.FXUtility;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNull;

public class FXApplicationTest extends ApplicationTest implements FocusOwnerTrait
{
    @Rule
    public TestWatcher screenshotOnFail = new TestWatcher()
    {
        @OnThread(value = Tag.Any, requireSynchronized = true)
        private @Nullable InternalException seenInternalException;
        
        @Override
        protected void failed(Throwable e, Description description)
        {
            super.failed(e, description);
            System.err.println("Screenshot of failure, " + targetWindow().toString() + ":");
            TestUtil.fx_(() -> dumpScreenshot());
            e.printStackTrace();
            if (e.getCause() != null)
                e.getCause().printStackTrace();
            System.err.println("Current windows: " + getWindowList());
        }

        @Override
        protected void starting(Description description)
        {
            super.starting(description);
            seenInternalException = null;
            Log.setInternalExceptionHandler(e -> {seenInternalException = e;});
        }

        @Override
        protected void finished(Description description)
        {
            super.finished(description);
            assertNull("Internal exception monitor", seenInternalException);
        }
    };

    protected String getWindowList()
    {
        return listWindows().stream().map(w -> "  " + showWindow(w)).collect(Collectors.joining("\n"));
    }

    private static String showWindow(Window w)
    {
        return w.toString() + TestUtil.fx(() -> (w instanceof Stage ? ((Stage)w).getTitle() : ""));
    }

    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    protected Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        windowToUse = stage;
        FXUtility._test_setTestingMode();
        targetWindow(stage);
        
        Timeline timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(60),e -> {
            if (stage.isShowing())
            {
                dumpScreenshot(stage);
            }
            else
            {
                System.out.println("Window no longer showing, stopping screenshots");
                timeline.stop();
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.setAutoReverse(false);
        //timeline.play();
    }

    @OnThread(Tag.FXPlatform)
    protected final void dumpScreenshot()
    {
        printBase64(capture(Screen.getPrimary().getBounds()).getImage());
    }
    
    @OnThread(Tag.FXPlatform)
    protected static void dumpScreenshot(Window target)
    {
        if (target.getScene() == null)
        {
            System.err.println("Window " + target + " does not have a scene");
            return;
        }
        // From https://stackoverflow.com/questions/31407382/javafx-chart-to-image-to-base64-string-use-in-php
        WritableImage image = target.getScene().snapshot(null);
        printBase64(image);
    }

    private static void printBase64(Image image)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", baos);
        }
        catch (IOException e)
        {
            System.err.println("Cannot write screenshot: " + e.getLocalizedMessage());
            return;
        }
        String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
        System.out.println("<img src=\"data:image/png;base64, " + base64Image + "\">");
    }

    // Because of the bug in TestFX+monocle where multiple windows
    // return true from isFocused(), write can write to the wrong
    // window.  So we override the methods and use our own
    // getRealFocusedWindow() method to find the right window.
    
    @Override
    public FxRobot write(String text, int sleepMillis)
    {
        Log.debug("Writing: " + text + " to " + TestUtil.fx(() -> {
            Window window = getRealFocusedWindow();
            Node focusNode = window.getScene().getFocusOwner();
            return window.toString() + (window instanceof Stage ? " " + ((Stage)window).getTitle() : "") + " @ " + focusNode;
        }));
        Scene scene = TestUtil.fx(() -> getRealFocusedWindow().getScene());
        text.chars().forEach(c -> {
            robotContext().getBaseRobot().typeKeyboard(scene, determineKeyCode(c), Utility.codePointToString(c));
            WaitForAsyncUtils.waitForFxEvents();
        });
        return this;
    }

    private KeyCode determineKeyCode(int character)
    {
        KeyCode key = KeyCode.UNDEFINED;
        key = (character == '\n') ? KeyCode.ENTER : key;
        key = (character == '\t') ? KeyCode.TAB : key;
        return key;
    }

    @Override
    public FxRobot write(String text)
    {
        return write(text, 0);
    }

    @Override
    public FxRobot write(char character)
    {
        return write(Character.toString(character));
    }

    public FxRobotInterface showContextMenu(String nodeQuery)
    {
        return showContextMenu(lookup(nodeQuery).query(), null);
    }
    
    /**
     * Monocle doesn't seem to show context menu when right-clicking, so we use
     * this work-around for headless mode.
     */
    @SuppressWarnings({"all"})
    public FxRobotInterface showContextMenu(Node node, @Nullable Point2D pointOnScreen)
    {
        if (GraphicsEnvironment.isHeadless())
        {
            TestUtil.fx_(() -> {
                Bounds local = node.getBoundsInLocal();
                Bounds screen = node.localToScreen(local);
                Point2D localMid = Utility.middle(local);
                Point2D screenMid = Utility.middle(screen);
                if (node.getOnContextMenuRequested() != null)
                {
                    ContextMenuEvent contextMenuEvent = new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                            localMid.getX(), localMid.getY(),
                            screenMid.getX(), screenMid.getY(), false, null);
                    node.getOnContextMenuRequested().handle(contextMenuEvent);
                }
                else if (node instanceof Control && ((Control)node).getContextMenu() != null)
                {
                    ((Control)node).getContextMenu().show(node, screenMid.getX(), screenMid.getY());
                }
            });
            return this;
        }
        else
        {
            if (pointOnScreen == null)
                return clickOn(node, MouseButton.SECONDARY);
            else
                return clickOn(pointOnScreen, MouseButton.SECONDARY);
        }
    }
}
