/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui.util;

import com.sun.javafx.application.ParametersImpl;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.testjavafx.FxRobot;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.QueryTrait;
import test.gui.trait.ScreenshotTrait;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.error.InternalException;
import xyz.columnal.gui.Main;
import xyz.columnal.log.Log;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;

import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNull;

public class FXApplicationTest extends org.testjavafx.junit4.ApplicationTest implements FocusOwnerTrait, ScreenshotTrait, QueryTrait
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
            System.err.println("Screenshot of failure, " + TFXUtil.fx(() -> focusedWindows()).toString() + ":");
            TFXUtil.fx_(() -> dumpScreenshot());
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
        return TFXUtil.fx(() -> listWindows()).stream().map(w -> "  " + showWindow(w)).collect(Collectors.joining("\n"));
    }

    private static String showWindow(Window w)
    {
        return w.toString() + TFXUtil.fx(() -> (w instanceof Stage ? ((Stage)w).getTitle() : ""));
    }

    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    protected Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage _stage) throws Exception
    {
        // Don't use the stage TestFX gives us as it re-uses it across tests.  Although
        // it's a bit slower, use a fresh one as it avoids issue with listeners etc
        // that are still attached to the old Stage:
        Stage stage = new Stage();
        // There seems to be a problem with a memory leak via ParametersImpl
        // having a static map of Application to parameters.  We never need
        // the parameters for testing so let's blank them.  Do it after
        // to prevent it interfering with timeout:
        FXUtility.runAfter(() -> {
            try
            {
                Field field = ParametersImpl.class.getDeclaredField("params");
                field.setAccessible(true);
                ((Map) field.get(null)).clear();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
        
        windowToUse = stage;
        // Don't run now because can upset the loading timeout:
        FXUtility.runAfter(Main::initialise);
        FXUtility._test_setTestingMode();
        //targetWindow(stage);
        
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
    public final void dumpScreenshot()
    {
        // Seems to throw an exception under JFX 13 and Monocle 12:
        //printBase64(new Robot().getScreenCapture(null, Screen.getPrimary().getBounds()));
        WritableImage whole = new WritableImage((int)Screen.getPrimary().getBounds().getWidth(), (int)Screen.getPrimary().getBounds().getHeight());
        ObservableList<Window> windows = Window.getWindows();
        if (!windows.isEmpty() && windows.containsAll(focusedWindows()))
        {
            windows.forEach(w -> {
                WritableImage ws = w.getScene().snapshot(null);
                for (int y = 0; y < ((int) ws.getHeight()); y++)
                {
                    for (int x = 0; x < ((int) ws.getWidth()); x++)
                    {
                        whole.getPixelWriter().setArgb((int) w.getX() + x, (int) w.getY() + y, ws.getPixelReader().getArgb(x, y));
                    }
                }
            });
            printBase64(whole);
        }
        else
        {
            // So capture window instead:
            List<Window> focusedWindows = focusedWindows();
            if (!focusedWindows.isEmpty())
                focusedWindows.forEach(this::dumpScreenshot);
        }
    }
    
    @OnThread(Tag.FXPlatform)
    public final void dumpScreenshot(Window target)
    {
        if (target.getScene() == null)
        {
            System.err.println("Window " + target + " does not have a scene");
            return;
        }
        System.out.println("Screenshot of window at position " + target.getX() + ", " + target.getY());
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

    private KeyCode determineKeyCode(int character)
    {
        KeyCode key = KeyCode.UNDEFINED;
        key = (character == '\n') ? KeyCode.ENTER : key;
        key = (character == '\t') ? KeyCode.TAB : key;
        return key;
    }

    @Override
    public FxRobot push(KeyCode... combination)
    {
        Log.debug("Pushing: " + Utility.listToString(Arrays.asList(combination)));
        Log.debugDuration("Pushed: "  + Utility.listToString(Arrays.asList(combination)), () -> super.push(combination));
        return this;
    }

    public FxRobotInterface showContextMenu(String nodeQuery)
    {
        return showContextMenu(waitForOne(nodeQuery), null);
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
            TFXUtil.fx_(() -> {
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
                else
                {
                    Log.error("No context menu to trigger for " + node);
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

    @OnThread(Tag.Any)
    protected final void waitForSuccess(Runnable r)
    {
        for (int i = 0; i < 80; i++)
        {
            try
            {
                r.run();
            }
            catch (AssertionError e)
            {
                // Go round again while we're failing
            }
            TFXUtil.sleep(100);
        }
        // One last time for the real success/failure:
        r.run();
    }
}
