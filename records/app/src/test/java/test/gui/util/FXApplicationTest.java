package test.gui.util;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.units.qual.UnknownUnits;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.testfx.api.FxRobot;
import org.testfx.api.FxRobotInterface;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import test.TestUtil;
import test.gui.FocusOwnerTrait;
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

public class FXApplicationTest extends ApplicationTest implements FocusOwnerTrait
{
    @Rule
    public TestWatcher screenshotOnFail = new TestWatcher()
    {
        @Override
        protected void failed(Throwable e, Description description)
        {
            super.failed(e, description);
            System.err.println("Screenshot of failure: ");
            //TestUtil.fx_(() -> dumpScreenshot(targetWindow()));
            e.printStackTrace();
            if (e.getCause() != null)
                e.getCause().printStackTrace();
            System.err.println("Current windows: " + getWindowList());
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
    protected final static void dumpScreenshot(Window target)
    {
        if (target.getScene() == null)
        {
            System.err.println("Window " + target + " does not have a scene");
            return;
        }
        // From https://stackoverflow.com/questions/31407382/javafx-chart-to-image-to-base64-string-use-in-php
        WritableImage image = target.getScene().snapshot(null);
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
        Scene scene = TestUtil.fx(() -> getRealFocusedWindow().getScene());
        text.chars().forEach(c -> {
            robotContext().getBaseRobot().typeKeyboard(scene, KeyCode.UNDEFINED, Utility.codePointToString(c));
            WaitForAsyncUtils.waitForFxEvents();
        });
        return this;
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

    /**
     * Monocle doesn't seem to show context menu when right-clicking, so we use
     * this work-around for headless mode.
     */
    @SuppressWarnings({"all"})
    public FxRobotInterface showContextMenu(String nodeQuery)
    {
        if (GraphicsEnvironment.isHeadless())
        {
            @NonNull Node node = lookup(nodeQuery).<@NonNull Node>query();
            TestUtil.fx_(() -> {
                Bounds local = node.getBoundsInLocal();
                Bounds screen = node.localToScreen(local);
                Point2D localMid = Utility.middle(local);
                Point2D screenMid = Utility.middle(screen);
                ContextMenuEvent contextMenuEvent = new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        localMid.getX(), localMid.getY(),
                        screenMid.getX(), screenMid.getY(), false, null);
                node.getOnContextMenuRequested().handle(contextMenuEvent);
            });
            return this;
        }
        else
        {
            return clickOn(nodeQuery, MouseButton.SECONDARY);
        }
    }
}
