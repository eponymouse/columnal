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
package test.gui;

import com.sun.javafx.PlatformUtil;
import com.sun.javafx.tk.Toolkit;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
import test.gui.trait.PopupTrait;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.data.TableManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.table.app.TableDisplay;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TFXUtil
{
    @OnThread(Tag.Any)
    public static <T> T fx(FXPlatformSupplierEx<T> action)
    {
        try
        {
            return WaitForAsyncUtils.asyncFx(action).get(60, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // Note: also waits for the queue to be empty
    @OnThread(Tag.Any)
    public static void fx_(FXPlatformRunnable action)
    {
        try
        {
            WaitForAsyncUtils.asyncFx(action::run).get(60, TimeUnit.SECONDS);
            WaitForAsyncUtils.waitForFxEvents();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // Doesn't wait for action to complete
    @OnThread(Tag.Any)
    public static void asyncFx_(FXPlatformRunnable action)
    {
        WaitForAsyncUtils.asyncFx(action::run);
    }

    @OnThread(Tag.Any)
    public static void fxTest_(FXPlatformRunnable action)
    {
        try
        {
            WaitForAsyncUtils.<Optional<Throwable>>asyncFx(new Callable<Optional<Throwable>>()
            {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public Optional<Throwable> call() throws Exception
                {
                    try
                    {
                        action.run();
                        return Optional.empty();
                    }
                    catch (Throwable t)
                    {
                        return Optional.of(t);
                    }
                }
            }).get(5, TimeUnit.MINUTES).ifPresent(e -> {throw new RuntimeException(e);});
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static <T> T sim(SimulationSupplier<T> action)
    {
        try
        {
            CompletableFuture<Either<Throwable, T>> f = new CompletableFuture<>();
            Workers.onWorkerThread("Test.sim " + Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber(), Workers.Priority.FETCH, () -> {
                try
                {
                    f.complete(Either.right(action.get()));
                }
                catch (Throwable e)
                {
                    f.complete(Either.left(e));
                }
            });
            return f.get(60, TimeUnit.SECONDS).either(e -> {throw new RuntimeException(e);}, x -> x);
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(Workers._test_getCurrentTaskName(), e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static void sim_(SimulationRunnable action)
    {
        try
        {
            CompletableFuture<Either<Throwable, Object>> f = new CompletableFuture<>();
            Workers.onWorkerThread("Test.sim_ " + Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber(), Workers.Priority.FETCH, () -> {
                try
                {
                    action.run();
                    f.complete(Either.right(new Object()));
                }
                catch (Throwable e)
                {
                    f.complete(Either.left(e));
                }
            });
            f.get(60, TimeUnit.SECONDS).either_(e -> {throw new RuntimeException(e);}, x -> {});
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(Workers._test_getCurrentTaskName(), e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static void sleep(int millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {

        }
    }

    @OnThread(Tag.Any)
    public static KeyCode ctrlCmd()
    {
        return SystemUtils.IS_OS_MAC_OSX ? KeyCode.COMMAND : KeyCode.CONTROL;
    }

    public @OnThread(Tag.Any)
    static CellPosition tablePosition(TableManager tableManager, TableId srcId) throws UserException
    {
        Table table = tableManager.getSingleTableOrThrow(srcId);
        return TBasicUtil.checkNonNull(fx(() -> table.getDisplay())).getMostRecentPosition();
    }

    public static void doubleOk(PopupTrait robot)
    {
        robot.moveAndDismissPopupsAtPos(robot.point(".ok-button"));
        robot.clickOn(".ok-button");
        sleep(300);
        if (TFXUtil.fx(() -> robot.lookup(".ok-button").tryQuery().isPresent()))
            robot.clickOn(".ok-button");
    }

    // Finds the first parent (starting at given one and going upwards via getParent) that satisfies the given predicate
    @OnThread(Tag.FXPlatform)
    public static @Nullable Parent findParent(@Nullable Parent parent, Predicate<Node> check)
    {
        while (parent != null && !check.test(parent))
        {
            parent = parent.getParent();
        }
        return parent;
    }

    @OnThread(Tag.Simulation)
    public static void collapseAllTableHats(TableManager tableManager, VirtualGrid virtualGrid)
    {
        for (Table table : tableManager.getAllTables())
        {
            fx_(() -> {
                TableDisplayBase display = table.getDisplay();
                if (display instanceof TableDisplay)
                {
                    ((TableDisplay)display)._test_collapseTableHat();
                }
            });
        }
        fx_(() -> virtualGrid.redoLayoutAfterScroll());
    }

    /**
     * Adds event filters on all nodes under the target location,
     * and tracks which if any receive the given event type
     * while executing during.
     */
    @OnThread(Tag.Any)
    public static <E extends Event> void debugEventRecipient_(FxRobotInterface robot, @Nullable Point2D target, EventType<E> eventType, Runnable during)
    {
        Set<Node> allNodes = robot.lookup(n -> {
            Bounds screen = fx(() -> n.localToScreen(n.getBoundsInLocal()));
            return target == null || screen.contains(target);
        }).queryAll();

        List<Pair<Node, EventType<?>>> received = new ArrayList<>();
        Map<Node, EventHandler<E>> listeners = new HashMap<>(); 
        for (Node node : allNodes)
        {
            EventHandler<E> eventHandler = e -> {
                received.add(new Pair<>(node, e.getEventType()));
            };
            fx_(() -> node.addEventFilter(eventType, eventHandler));
            listeners.put(node, eventHandler);
        }
        
        during.run();

        listeners.forEach((n, l) ->
        {
            fx_(() -> n.removeEventFilter(eventType, l));
        });
        
        Log.normal("Events received:\n" + received.stream().map(n -> "  " + n.toString()).collect(Collectors.joining("\n")));
    }

    public static void fxYieldUntil(FXPlatformSupplier<Boolean> waitUntilTrue)
    {
        Object finish = new Object();
        FXPlatformRunnable repeat = new FXPlatformRunnable()
        {
            int attempts = 0;
            
            @Override
            public void run()
            {
                if (waitUntilTrue.get() || attempts >= 10)
                {
                    com.sun.javafx.tk.Toolkit.getToolkit().exitNestedEventLoop(finish, "");
                }
                else
                {
                    attempts += 1;
                    FXUtility.runAfterDelay(Duration.millis(300), this);
                }
            }
        };
        Platform.runLater(repeat::run);
        Toolkit.getToolkit().enterNestedEventLoop(finish);
    }

    @OnThread(Tag.FXPlatform)
    public static void copySnapshotToClipboard(Node node)
    {
        WritableImage img = node.snapshot(null, null);
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putImage(img);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    // WOuld be nice to get this working, but doesn't currently work
    public static void writePaste_doesntwork(FxRobotInterface robot, String string)
    {
        fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, string)));
        robot.push(PlatformUtil.isMac() ? KeyCode.COMMAND : KeyCode.CONTROL, KeyCode.V);
    }

    public static interface FXPlatformSupplierEx<T> extends Callable<T>
    {
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public T call() throws InternalException, UserException;
    }
}
