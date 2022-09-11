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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
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
import test.functions.TFunctionUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import one.util.streamex.StreamEx;
import one.util.streamex.StreamEx.Emitter;
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
import xyz.columnal.data.Table.FullSaver;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.gui.MainWindow;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.table.TableDisplay;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.jellytype.JellyType.JellyTypeVisitorEx;
import xyz.columnal.jellytype.JellyTypeRecord.Field;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.StringLiteral;
import xyz.columnal.transformations.expression.TypeLiteralExpression;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.FunctionList;
import test.gui.trait.PopupTrait;
import xyz.columnal.utility.*;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility.ListEx;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public static final InitialLoadDetails ILD = new InitialLoadDetails(null, null, null, null);

    private static @LocalizableKey String makeEmptyKey()
    {
        // I cannot seem to get the checker to suppress warnings, so instead give a key that is valid:
        return "menu.exit";
    }

    public static void assertEqualList(List<?> a, List<?> b)
    {
        if (a.size() != b.size())
            System.err.println("Different lengths");
        try
        {
            assertEquals(a, b);
        }
        catch (AssertionError e)
        {
            System.err.println("Content:");
            for (int i = 0; i < a.size(); i++)
            {
                System.err.println(((a.get(i) == null ? b.get(i) == null : a.get(i).equals(b.get(i))) ? "    " : "!!  ") + a.get(i) + "  " + b.get(i));
            }
            throw e;
        }

    }

    public static TableId generateTableId(SourceOfRandomness sourceOfRandomness)
    {
        return new TableId(IdentifierUtility.fixExpressionIdentifier(TBasicUtil.generateIdent(sourceOfRandomness), "Table"));
    }

    // Generates a pair of different ids
    public static Pair<TableId, TableId> generateTableIdPair(SourceOfRandomness r)
    {
        TableId us = generateTableId(r);
        TableId src;
        do
        {
            src = generateTableId(r);
        }
        while (src.equals(us));
        return new Pair<>(us, src);
    }

    public static ColumnId generateColumnId(SourceOfRandomness sourceOfRandomness)
    {
        return new ColumnId(IdentifierUtility.fixExpressionIdentifier(TBasicUtil.generateIdent(sourceOfRandomness), "Column"));
    }


    public static <K, V> Map<K, V> makeMap(SourceOfRandomness r, int minSizeIncl, int maxSizeIncl, Supplier<K> makeKey, Supplier<V> makeValue)
    {
        int size = r.nextInt(minSizeIncl, maxSizeIncl);
        HashMap<K, V> list = new HashMap<>();
        for (int i = 0; i < size; i++)
            list.put(makeKey.get(), makeValue.get());
        return list;
    }

    @OnThread(Tag.Simulation)
    public static StreamEx<Pair<Integer, List<@Value Object>>> streamFlattened(RecordSet src)
    {
        return new StreamEx.Emitter<Pair<Integer, List<@Value Object>>>()
        {
            int nextIndex = 0;
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Nullable Emitter<Pair<Integer, List<@Value Object>>> next(Consumer<? super Pair<Integer, List<Object>>> consumer)
            {
                try
                {
                    if (src.indexValid(nextIndex))
                    {
                        List<@Value Object> collapsed = src.getColumns().stream()/*.sorted(Comparator.comparing(Column::getName))*/.map(c ->
                        {
                            try
                            {
                                return c.getType().getCollapsed(nextIndex);
                            }
                            catch (UserException | InternalException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }).collect(Collectors.toList());
                        consumer.accept(new Pair<>(nextIndex, collapsed));
                        nextIndex += 1;
                        return this;
                    }
                    else
                        return null; // No more elements
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }.stream();
    }

    @OnThread(Tag.Simulation)
    public static StreamEx<@Value Object> streamFlattened(Column column)
    {
        return new StreamEx.Emitter<@Value Object>()
        {
            int nextIndex = 0;
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Nullable Emitter<@Value Object> next(Consumer<? super @Value Object> consumer)
            {
                try
                {
                    if (column.indexValid(nextIndex))
                    {
                        Object collapsed = column.getType().getCollapsed(nextIndex);
                        consumer.accept(collapsed);
                        nextIndex += 1;
                        return this;
                    }
                    else
                        return null; // No more elements
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);                }
            }
        }.stream();
    }

    @OnThread(Tag.Simulation)
    @SuppressWarnings("nullness")
    public static Map<List<@Value Object>, Long> getRowFreq(RecordSet src)
    {
        return getRowFreq(streamFlattened(src).<List<@Value Object>>map(p -> p.getSecond()));
    }

    @OnThread(Tag.Simulation)
    @SuppressWarnings("nullness")
    public static Map<List<@Value Object>, Long> getRowFreq(Stream<List<@Value Object>> src)
    {
        SortedMap<List<@Value Object>, Long> r = new TreeMap<>((Comparator<List<@Value Object>>)(List<@Value Object> a, List<@Value Object> b) -> {
            if (a.size() != b.size())
                return Integer.compare(a.size(), b.size());
            for (int i = 0; i < a.size(); i++)
            {
                try
                {
                    int cmp = Utility.compareValues(a.get(i), b.get(i));
                    if (cmp != 0)
                        return cmp;
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return 0;
        });
        src.forEach(new Consumer<List<@Value Object>>()
        {
            @Override
            public void accept(List<@Value Object> row)
            {
                r.compute(row, new BiFunction<List<@Value Object>, Long, Long>()
                {
                    @Override
                    public Long apply(List<@Value Object> k, Long v)
                    {
                        return v == null ? 1 : v + 1;
                    }
                });
            }
        });
        return r;
    }

    public static String makeNonEmptyString(SourceOfRandomness r, GenerationStatus gs)
    {
        String s;
        do
        {
            s = TBasicUtil.makeString(r, gs);
        }
        while (s.trim().isEmpty());
        return s;
    }

    public static List<ColumnId> generateColumnIds(SourceOfRandomness r, int numColumns)
    {
        List<ColumnId> columnIds = new ArrayList<>();
        while (columnIds.size() < numColumns)
        {
            ColumnId c = generateColumnId(r);
            if (!columnIds.contains(c))
                columnIds.add(c);
        }
        return columnIds;
    }

    private static Pair<@Nullable String, DataType> t(DataType type)
    {
        return new Pair<>(null, type);
    }

    @SuppressWarnings("nullness")
    public static TypeState typeState()
    {
        try
        {
            UnitManager unitManager = new UnitManager();
            TypeManager typeManager = new TypeManager(unitManager);
            /*
            List<DataType> taggedTypes = distinctTypes.stream().filter(p -> p.isTagged()).collect(Collectors.toList());
            for (DataType t : taggedTypes)
            {
                typeManager.registerTaggedType(t.getTaggedTypeName().getRaw(), ImmutableList.of(), Utility.mapListInt(t.getTagTypes(), t2 -> t2.mapInt(JellyType::fromConcrete)));
            }
            */
            return TFunctionUtil.createTypeState(typeManager);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static @ExpressionIdentifier String generateVarName(SourceOfRandomness r)
    {
        @ExpressionIdentifier String s;
        do
        {
            s = IdentifierUtility.asExpressionIdentifier(TBasicUtil.generateIdent(r));
        }
        while (s == null);
        return s;
    }

    @OnThread(Tag.Simulation)
    public static String toString(@Value Object value)
    {
        if (value instanceof Object[])
        {
            return "(" + Arrays.stream((@Value Object[])value).map(TestUtil::toString).collect(Collectors.joining(",")) + ")";
        }
        else if (value instanceof ListEx)
        {
            StringBuilder sb = new StringBuilder("[");
            ListEx list = (ListEx) value;
            try
            {
                for (int i = 0; i < list.size(); i++)
                {
                    if (i != 0)
                        sb.append(", ");
                    sb.append(toString(list.get(i)));
                }
            }
            catch (InternalException | UserException e)
            {
                sb.append("ERROR...");
            }
            sb.append("]");
            return sb.toString();
        }
        else if (value instanceof TaggedValue)
        {
            @Value TaggedValue t = ((TaggedValue)value);
            return t.getTagIndex() + (t.getInner() == null ? "" : ":" + toString(t.getInner()));
        }
        else
            return value.toString();
    }

    @OnThread(Tag.Simulation)
    public static String toString(Column c) throws UserException, InternalException
    {
        StringBuilder sb = new StringBuilder("[");
        DataTypeValue t = c.getType();
        for (int i = 0; i < c.getLength(); i++)
        {
            if (i != 0)
                sb.append(", ");
            sb.append(toString(t.getCollapsed(i)));
        }
        sb.append("]");
        return sb.toString();
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

    @OnThread(Tag.Simulation)
    public static String save(TableManager tableManager) throws ExecutionException, InterruptedException, InvocationTargetException
    {
        // This thread is only pretend running on FXPlatform, but sets off some
        // code which actually runs on the fx platform thread:
        CompletableFuture<String> f = new CompletableFuture<>();

        try
        {
            FullSaver saver = new FullSaver(null);
            tableManager.save(null, saver);
            f.complete(saver.getCompleteFile());
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            f.complete("");
        }
        return f.get();
    }

    public static <T> T checkedToRuntime(ExSupplier<T> supplier)
    {
        try
        {
            return supplier.get();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void checkedToRuntime_(ExRunnable runnable)
    {
        try
        {
            runnable.run();
        }
        catch (InternalException | UserException e)
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
     * Removes a random item from the given list and returns it.
     * Note that the list is modified!
     */
    public static <T> T removeRandom(Random r, List<T> list)
    {
        int index = r.nextInt(list.size());
        return list.remove(index);
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
        String saved = save(mgr);
        //System.out.println("Saving: {{{" + saved + "}}}");
        AtomicReference<MainWindowActions> tableManagerAtomicReference = new AtomicReference<>();
        FXUtility.runFX(() -> checkedToRuntime_(() -> {
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

    // Makes something which could be an unfinished expression.  Can't have operators, can't start with a number.
    public static String makeUnfinished(SourceOfRandomness r)
    {
        StringBuilder s = new StringBuilder();
        s.append(r.nextChar('a', 'z'));
        int len = r.nextInt(0, 10);
        for (int i = 0; i < len; i++)
        {
            s.append(r.nextBoolean() ? r.nextChar('a', 'z') : r.nextChar('0', '9'));
        }
        return s.toString();
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

    // If null, assertion failure.  Otherwise returns as non-null.
    @SuppressWarnings("nullness")
    public static <T> @NonNull T checkNonNull(@Nullable T t)
    {
        assertNotNull(t);
        return t;
    }

    public @OnThread(Tag.Any)
    static CellPosition tablePosition(TableManager tableManager, TableId srcId) throws UserException
    {
        Table table = tableManager.getSingleTableOrThrow(srcId);
        return checkNonNull(fx(() -> table.getDisplay())).getMostRecentPosition();
    }

    public static KeyCode ctrlCmd()
    {
        return SystemUtils.IS_OS_MAC_OSX ? KeyCode.COMMAND : KeyCode.CONTROL;
    }


    // Used for testing
    // Creates a call to a tag constructor
    @SuppressWarnings("recorded")
    public static Expression tagged(UnitManager unitManager, TagInfo constructor, @Nullable Expression arg, DataType destType, boolean canAddAsType) throws InternalException
    {
        IdentExpression constructorExpression = IdentExpression.tag(constructor.getTypeName().getRaw(), constructor.getTagInfo().getName());
        Expression r;
        if (arg == null)
        {
            r = constructorExpression;
        }
        else
        {
            r = new CallExpression(constructorExpression, ImmutableList.of(arg));
        }
        
        if (!canAddAsType)
            return r;
        
        // Need to avoid having an ambiguous type:
        TaggedTypeDefinition wholeType = constructor.wholeType;
        for (Pair<TypeVariableKind, String> var : wholeType.getTypeArguments())
        {
            // If any type variables aren't mentioned, wrap in asType:
            if (!containsTypeVar(wholeType.getTags().get(constructor.tagIndex).getInner(), var))
            {
                FunctionDefinition asType = FunctionList.lookup(unitManager, "as type");
                if (asType == null)
                    throw new RuntimeException("Could not find as type");
                return new CallExpression(IdentExpression.function(asType.getFullName()),ImmutableList.of(new TypeLiteralExpression(TypeExpression.fromDataType(destType)), r));
            }
        }
        return r;
    }

    private static boolean containsTypeVar(@Nullable JellyType jellyType, Pair<TypeVariableKind, String> var)
    {
        if (jellyType == null)
            return false;

        try
        {
            return jellyType.apply(new JellyTypeVisitorEx<Boolean, InternalException>()
            {
                @Override
                public Boolean number(JellyUnit unit) throws InternalException
                {
                    return containsTypeVar(unit, var);
                }
    
                @Override
                public Boolean text() throws InternalException
                {
                    return false;
                }
    
                @Override
                public Boolean date(DateTimeInfo dateTimeInfo) throws InternalException
                {
                    return false;
                }
    
                @Override
                public Boolean bool() throws InternalException
                {
                    return false;
                }
    
                @Override
                public Boolean applyTagged(TypeId typeName, ImmutableList<Either<JellyUnit, JellyType>> typeParams) throws InternalException
                {
                    return typeParams.stream().anyMatch(p -> p.<Boolean>either(u -> containsTypeVar(u, var), t -> containsTypeVar(t, var)));
                }

                @Override
                public Boolean record(ImmutableMap<@ExpressionIdentifier String, Field> fields, boolean complete) throws InternalException, InternalException
                {
                    return fields.values().stream().anyMatch(t -> containsTypeVar(t.getJellyType(), var));
                }
    
                @Override
                public Boolean array(JellyType inner) throws InternalException
                {
                    return containsTypeVar(inner, var);
                }
    
                @Override
                public Boolean function(ImmutableList<JellyType> argTypes, JellyType resultType) throws InternalException
                {
                    return argTypes.stream().anyMatch(a -> containsTypeVar(a, var)) || containsTypeVar(resultType, var);
                }
    
                @Override
                public Boolean ident(String name) throws InternalException
                {
                    return var.equals(new Pair<>(TypeVariableKind.TYPE, name));
                }
            });
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static boolean containsTypeVar(JellyUnit unit, Pair<TypeVariableKind, String> var)
    {
        if (var.getFirst() == TypeVariableKind.UNIT)
            return unit.getDetails().containsKey(ComparableEither.left(var.getSecond()));
        return false;
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

    public static interface TestRunnable
    {
        public void run() throws Exception;
    }
    
    // Needed until IntelliJ bug IDEA-198613 is fixed
    public static void printSeedOnFail(TestRunnable r) throws Exception
    {
        try
        {
            r.run();
        }
        catch (AssertionError assertionError)
        {
            String message = assertionError.getMessage();
            message = message == null ? "" :
                    message.replace("expected:", "should be").replace("but was:", "alas found");
            throw new AssertionError(message, assertionError);
        }
    }

    public static interface FXPlatformSupplierEx<T> extends Callable<T>
    {
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public T call() throws InternalException, UserException;
    }

    public static class Transformation_Mgr
    {
        public final TableManager mgr;
        public final Transformation transformation;

        @OnThread(Tag.Simulation)
        public Transformation_Mgr(TableManager mgr, Transformation transformation)
        {
            this.mgr = mgr;
            this.transformation = transformation;
            mgr.record(transformation);
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true) // Only for testing anyway
        public String toString()
        {
            return transformation.toString();
        }
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

    public static class SingleTableLookup implements ColumnLookup
    {
        private final TableId tableId;
        private final RecordSet srcTable;

        public SingleTableLookup(TableId tableId, RecordSet srcTable)
        {
            this.tableId = tableId;
            this.srcTable = srcTable;
        }

        @Override
        public Stream<Pair<@Nullable TableId, ColumnId>> getAvailableColumnReferences()
        {
            return srcTable.getColumns().stream().map(c -> new Pair<>(null, c.getName()));
        }

        @Override
        public Stream<TableId> getAvailableTableReferences()
        {
            return Stream.of(tableId);
        }

        @Override
        public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
        {
            return Stream.empty(); // Not used in testing
        }

        @Override
        public @Nullable FoundColumn getColumn(Expression expression, @Nullable TableId refTableId, ColumnId refColumnId)
        {
            try
            {
                if (refTableId == null || refTableId.equals(tableId))
                {
                    Column column = srcTable.getColumnOrNull(refColumnId);
                    if (column != null)
                        return new FoundColumn(tableId, true, column.getType(), null);
                }
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
            }
            return null;
        }

        @Override
        public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
        {
            if (!tableId.equals(tableName))
                return null;
            
            return new FoundTable()
            {
                @Override
                public TableId getTableId()
                {
                    return tableId;
                }

                @Override
                public ImmutableMap<ColumnId, DataTypeValue> getColumnTypes() throws InternalException, UserException
                {
                    ImmutableMap.Builder<ColumnId, DataTypeValue> columns = ImmutableMap.builder();
                    for (Column column : srcTable.getColumns())
                    {
                        columns.put(column.getName(), column.getType());
                    }
                    return columns.build();
                }

                @Override
                public int getRowCount() throws InternalException, UserException
                {
                    return srcTable.getLength();
                }
            };
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
    
    public static StringLiteral makeStringLiteral(String target, SourceOfRandomness r)
    {
        StringBuilder b = new StringBuilder();
        
        target.codePoints().forEach(n -> {
            if (r.nextInt(8) == 1)
                b.append("^{" + Integer.toHexString(n) + "}");
            else
                b.append(GrammarUtility.escapeChars(Utility.codePointToString(n)));
        });
        return new StringLiteral(b.toString());
    }
}
