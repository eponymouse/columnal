package test.gui;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.ComparableValue;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.table.TableDisplay;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
import records.transformations.Filter;
import records.transformations.ManualEdit;
import records.transformations.Sort;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.NumericLiteral;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestManualEdit extends FXApplicationTest implements ListUtilTrait, ScrollToTrait, PopupTrait, ClickTableLocationTrait, EnterStructuredValueTrait
{
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propManualEdit(
            @When(seed=1L) @From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker,
            @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        int length = 1 + r.nextInt(50);
        int numColumns = 1 + r.nextInt(5);
        List<DataTypeAndValueMaker> columnTypes = Utility.replicateM_Ex(numColumns, () -> typeMaker.makeType());
        List<SimulationFunction<RecordSet, EditableColumn>> makeColumns = Utility.<DataTypeAndValueMaker, SimulationFunction<RecordSet, EditableColumn>>mapListEx(columnTypes, columnType -> {
            ColumnId columnId = TestUtil.generateColumnId(new SourceOfRandomness(r));

            ImmutableList<Either<String, @Value Object>> values = Utility.<Either<String, @Value Object>>replicateM_Ex(length, () -> {
                if (r.nextInt(5) == 1)
                {
                    return Either.left("@" + r.nextInt());
                }
                else
                {
                    return Either.right(columnType.makeValue());
                }
            });
            
            return columnType.getDataType().makeImmediateColumn(columnId, values, columnType.makeValue());
        });
        RecordSet srcRS = new KnownLengthRecordSet(makeColumns, length);
        
        // Null means use row number
        @Nullable Column replaceKeyColumn = r.nextInt(5) == 1 ? null : srcRS.getColumns().get(r.nextInt(numColumns));
        
        // Save the table, then open GUI and load it, then add a filter transformation (rename to keeprows)
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, typeMaker.getTypeManager(), srcRS);
        TestUtil.sleep(5000);
        // Add a sort transformation:
        CellPosition targetSortPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetSortPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetSortPos, targetSortPos), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        clickOn(".id-transform-sort");
        TestUtil.delay(100);
        // Select the single listed table:
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        ColumnId initialSortBy = srcRS.getColumnIds().get(r.nextInt(numColumns)); 
        write(initialSortBy.getRaw());
        push(KeyCode.ENTER);
        
        @SuppressWarnings("units")
        Supplier<@TableDataRowIndex Integer> makeRowIndex = () -> r.nextInt(length);
        @SuppressWarnings("units")
        Supplier<@TableDataColIndex Integer> makeColIndex = () -> r.nextInt(numColumns);

        HashMap<ColumnId, TreeMap<ComparableValue, ComparableValue>> replacementsSoFar = new HashMap<>();

        TableId sortId = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Sort).map(t -> t.getId()).findFirst().orElseThrow(() -> new RuntimeException("Couldn't find sort"));
        
        // There's two ways to create a new manual edit.  One is to just create it using the menu.  The other is to try to edit an existing transformation, and follow the popup which appears
        if (r.nextBoolean())
        {
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), sortId, makeRowIndex.get(), makeColIndex.get());
        }
        else
        {
            // Let's create it directly.
            CellPosition targetPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));


            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            TestUtil.delay(100);
            clickOn(".id-new-transform");
            TestUtil.delay(100);
            clickOn(".id-transform-edit");
            TestUtil.delay(100);
            write(sortId.getRaw());
            push(KeyCode.ENTER);
            TestUtil.delay(1000);
        }

        ManualEdit manualEdit = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof ManualEdit).map(t -> (ManualEdit)t).findFirst().orElseThrow(() -> new RuntimeException("No edit"));
        TableDisplay manualEditDisplay = TestUtil.checkNonNull((TableDisplay)TestUtil.<@Nullable TableDisplayBase>fx(() -> manualEdit.getDisplay()));
        
        int numFurtherEdits = 1 + r.nextInt(10);
        for (int i = 0; i < numFurtherEdits; i++)
        {
            @TableDataRowIndex int row = makeRowIndex.get();
            @TableDataColIndex int col = makeColIndex.get();
            
            @Value Object replaceKey;
            if (replaceKeyColumn == null)
                replaceKey = DataTypeUtility.value(new BigDecimal(r.nextInt(length)));
            else
                replaceKey = replaceKeyColumn.getType().getCollapsed(r.nextInt(length));
            
            @Value Object value = columnTypes.get(col).makeValue();
            replacementsSoFar.computeIfAbsent(srcRS.getColumnIds().get(col), k -> new TreeMap<>())
                .put(new ComparableValue(replaceKey), new ComparableValue(value));
            
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), manualEdit.getId(), row, col);
            push(KeyCode.ENTER);
            enterStructuredValue(columnTypes.get(col).getDataType(), value, r, false);
            push(KeyCode.ENTER);
        }
        // TODO also change original data and/or sort order and check they updated.
            
        // Now check output values by getting them from clipboard:
        TestUtil.sleep(500);
        showContextMenu(".table-display-table-title.transformation-table-title")
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);

        Optional<ImmutableList<LoadedColumnInfo>> clip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(clip.isPresent());
        // Need to fish out first column from clip, then compare item:
        // TODO
        //List<Either<String, @Value Object>> expected = IntStream.range(0, srcColumn.getLength()).mapToObj(i -> TestUtil.checkedToRuntime(() -> srcColumn.getType().getCollapsed(i))).filter(x -> Utility.compareNumbers(x, cutOff) > 0).map(x -> Either.<String, Object>right(x)).collect(Collectors.toList());
        //TestUtil.assertValueListEitherEqual("Filtered", expected, clip.get().stream().filter(c -> Objects.equals(c.columnName, srcColumn.getName())).findFirst().<ImmutableList<Either<String, @Value Object>>>map(c -> c.dataValues).orElse(null));
    }
}
