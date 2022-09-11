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

package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.sun.javafx.PlatformUtil;
import com.sun.javafx.tk.Toolkit;
import javafx.application.Platform;
import javafx.css.Styleable;
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
import javafx.stage.Stage;
import javafx.util.Duration;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.gui.MainWindow;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.table.TableDisplay;
import xyz.columnal.transformations.expression.Expression;
import test.gui.trait.PopupTrait;
import xyz.columnal.utility.*;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 05/11/2016.
 */
public class TestUtil
{
    public static final @LocalizableKey String EMPTY_KEY = makeEmptyKey();

    private static @LocalizableKey String makeEmptyKey()
    {
        // I cannot seem to get the checker to suppress warnings, so instead give a key that is valid:
        return "menu.exit";
    }

    public static <K, V> Map<K, V> makeMap(SourceOfRandomness r, int minSizeIncl, int maxSizeIncl, Supplier<K> makeKey, Supplier<V> makeValue)
    {
        int size = r.nextInt(minSizeIncl, maxSizeIncl);
        HashMap<K, V> list = new HashMap<>();
        for (int i = 0; i < size; i++)
            list.put(makeKey.get(), makeValue.get());
        return list;
    }

    private static Pair<@Nullable String, DataType> t(DataType type)
    {
        return new Pair<>(null, type);
    }

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
            Workers.onWorkerThread("Test.sim " + Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber(), Priority.FETCH, () -> {
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
            Workers.onWorkerThread("Test.sim_ " + Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber(), Priority.FETCH, () -> {
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

    /**
     * IMPORTANT: we say Simulation thread to satisfy thread-checker, but don't call it from the actual
     * simultation thread or it will time out!  Just tag yours as simulation, too.
     *
     * Returns a runnable which will wait for the table to load
     *
     * @param windowToUse
     * @param mgr
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws InvocationTargetException
     */
    @OnThread(Tag.Simulation)
    public static Supplier<MainWindowActions> openDataAsTable(Stage windowToUse, TableManager mgr) throws Exception
    {
        File temp = File.createTempFile("srcdata", "tables");
        temp.deleteOnExit();
        String saved = TTableUtil.save(mgr);
        //System.out.println("Saving: {{{" + saved + "}}}");
        AtomicReference<MainWindowActions> tableManagerAtomicReference = new AtomicReference<>();
        FXUtility.runFX(() -> TBasicUtil.checkedToRuntime_(() -> {
            MainWindowActions mainWindowActions = MainWindow.show(windowToUse, temp, new Pair<>(temp, saved), null);
            tableManagerAtomicReference.set(mainWindowActions);
        }));
        // Wait until individual tables are actually loaded:
        return () -> {
            int count = 0;
            do
            {
                //System.err.println("Waiting for main window");
                sleep(1000);
                count += 1;
            }
            while (fx(() -> windowToUse.getScene().lookup(".virt-grid-line")) == null && count < 30);
            if (count >= 30)
                throw new RuntimeException("Could not load table data");
            return tableManagerAtomicReference.get();
        };
    }

    // WOuld be nice to get this working, but doesn't currently work
    public static void writePaste_doesntwork(FxRobotInterface robot, String string)
    {
        fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, string)));
        robot.push(PlatformUtil.isMac() ? KeyCode.COMMAND : KeyCode.CONTROL, KeyCode.V);
    }

    /*public static interface TablesMaker
    {
        public List<Table>
    }*/

    @OnThread(Tag.Simulation)
    public static MainWindowActions openDataAsTable(Stage windowToUse, @Nullable TypeManager typeManager, RecordSet data) throws Exception
    {
        TableManager manager = new DummyManager();
        Table t = new ImmediateDataSource(manager, new InitialLoadDetails(new TableId("Table1"), null, CellPosition.ORIGIN.offsetByRowCols(1, 1), null), new EditableRecordSet(data));
        manager.record(t);
        if (typeManager != null)
        {
            manager.getTypeManager()._test_copyTaggedTypesFrom(typeManager);
        }
        return openDataAsTable(windowToUse, manager).get();
    }

    // Wait.  Useful to stop multiple consecutive clicks turning into double clicks
    public static void delay(int millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {

        }
    }

    public static void assertEqualsText(String prefix, String expected, String actual)
    {
        if (!expected.equals(actual))
        {
            String[] expectedLines = expected.split("\n");
            String[] actualLines = actual.split("\n");
            for (int i = 0; i < Math.max(expectedLines.length, actualLines.length); i++)
            {
                String expectedLine = i < expectedLines.length ? expectedLines[i] : null;
                String actualLine = i < actualLines.length ? actualLines[i] : null;
                assertEquals(prefix + "\nExpected line " + i + ": " + (expectedLine == null ? "null" : stringAsHexChars(expectedLine)) + "\nActual: " + (actualLine == null ? "null" : stringAsHexChars(actualLine)), expectedLine, actualLine);
            }
        }
    }

    public static String stringAsHexChars(String str)
    {
        return str.chars().mapToObj(c -> Integer.toHexString(c) + (c == 10 ? "\n" : "")).collect(Collectors.joining(" "));
    }

    public @OnThread(Tag.Any)
    static CellPosition tablePosition(TableManager tableManager, TableId srcId) throws UserException
    {
        Table table = tableManager.getSingleTableOrThrow(srcId);
        return TBasicUtil.checkNonNull(fx(() -> table.getDisplay())).getMostRecentPosition();
    }

    public static KeyCode ctrlCmd()
    {
        return SystemUtils.IS_OS_MAC_OSX ? KeyCode.COMMAND : KeyCode.CONTROL;
    }


    @OnThread(Tag.FXPlatform)
    public static void copySnapshotToClipboard(Node node)
    {
        WritableImage img = node.snapshot(null, null);
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putImage(img);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
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

    // Applies Matcher to the result of an extraction function:
    public static <@NonNull S, @NonNull T> Matcher<S> matcherOn(Matcher<T> withExtracted, Function<S, @NonNull T> extract)
    {
        return new BaseMatcher<S>()
        {
            @Override
            public void describeTo(Description description)
            {
                withExtracted.describeTo(description);
            }

            @SuppressWarnings("unchecked")
            @Override
            public boolean matches(Object o)
            {
                return withExtracted.matches(extract.apply((S)o));
            }
        };
    }

    public static <T extends Styleable> Matcher<T> matcherHasStyleClass(String styleClass)
    {
        return TestUtil.<T, Iterable<? extends String>>matcherOn(Matchers.contains(styleClass), (T s) -> fx(() -> ImmutableList.copyOf(s.getStyleClass())));
    }

    @OnThread(Tag.Simulation)
    public static Either<String, @Value Object> getSingleCollapsedData(DataTypeValue type, int index) throws UserException, InternalException
    {
        try
        {
            return Either.right(type.getCollapsed(index));
        }
        catch (InvalidImmediateValueException e)
        {
            return Either.left(e.getInvalid());
        }
    }

    // Adds event filters on all nodes under the target location,
    // and tracks which if any receive the given event type
    // while executing during.
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

    public static interface FXPlatformSupplierEx<T> extends Callable<T>
    {
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public T call() throws InternalException, UserException;
    }

    public static class Expression_Mgr
    {
        public final TableManager mgr;
        public final Expression expression;

        public Expression_Mgr(TableManager mgr, Expression expression)
        {
            this.mgr = mgr;
            this.expression = expression;
        }
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
    
    public static void doubleOk(PopupTrait robot)
    {
        robot.moveAndDismissPopupsAtPos(robot.point(".ok-button"));
        robot.clickOn(".ok-button");
        sleep(300);
        if (robot.lookup(".ok-button").tryQuery().isPresent())
            robot.clickOn(".ok-button");
    }

}
