package test.gui.transformation;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.Column;
import records.data.datatype.DataTypeUtility;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
import records.transformations.Filter;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.NumericLiteral;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Utility;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestFilter extends FXApplicationTest implements ListUtilTrait, ScrollToTrait, PopupTrait, ClickTableLocationTrait
{
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propNumberFilter(
            @NumTables(maxTables = 1) @MustIncludeNumber @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original,
            @From(GenRandom.class) Random r) throws Exception
    {
        // Save the table, then open GUI and load it, then add a filter transformation (rename to keeprows)
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, original.mgr).get();
        TestUtil.sleep(5000);
        CellPosition targetPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        clickOn(".id-transform-filter");
        TestUtil.delay(100);
        write(original.data().getId().getRaw());
        push(KeyCode.ENTER);
        TestUtil.sleep(200);
        // Then enter filter condition.
        // Find numeric column:
        Column srcColumn = original.data().getData().getColumns().stream().filter(c -> TestUtil.checkedToRuntime(() -> DataTypeUtility.isNumber(c.getType().getType()))).findFirst().orElseGet((Supplier<Column>)(() -> {throw new AssertionError("No numeric column");}));
        // Pick arbitrary value as cut-off:
        @Value Number cutOff;
        if (srcColumn.getLength() == 0)
            cutOff = DataTypeUtility.value(42);
        else
            cutOff = (Number)srcColumn.getType().getCollapsed(r.nextInt(srcColumn.getLength()));
        
        push(TestUtil.ctrlCmd(), KeyCode.A);
        push(KeyCode.DELETE);
        // Select column in auto complete:
        write(srcColumn.getName().getRaw());
        push(KeyCode.TAB);
        write(">");
        write(DataTypeUtility._test_valueToString(cutOff));
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");

        // Now check output values by getting them from clipboard:
        TestUtil.sleep(500);
        showContextMenu(".table-display-table-title.transformation-table-title")
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);

        @SuppressWarnings("recorded")
        ComparisonExpression expectedCmp = new ComparisonExpression(ImmutableList.of(IdentExpression.column(srcColumn.getName()), new NumericLiteral(cutOff, null)), ImmutableList.of(ComparisonOperator.GREATER_THAN));
        assertEquals(expectedCmp, Utility.filterClass(mainWindowActions._test_getTableManager().getAllTables().stream(), Filter.class).findFirst().get().getFilterExpression());
                
        Optional<ImmutableList<LoadedColumnInfo>> clip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(original.mgr.getTypeManager()));
        assertTrue(clip.isPresent());
        // Need to fish out first column from clip, then compare item:
        List<Either<String, @Value Object>> expected = IntStream.range(0, srcColumn.getLength()).mapToObj(i -> TestUtil.checkedToRuntime(() -> srcColumn.getType().getCollapsed(i))).filter(x -> Utility.compareNumbers((Number)x, cutOff) > 0).map(x -> Either.<String, Object>right(x)).collect(Collectors.toList());
        DataTestUtil.assertValueListEitherEqual("Filtered", expected, clip.get().stream().filter(c -> Objects.equals(c.columnName, srcColumn.getName())).findFirst().<ImmutableList<Either<String, @Value Object>>>map(c -> c.dataValues).orElse(null));
    }
}
