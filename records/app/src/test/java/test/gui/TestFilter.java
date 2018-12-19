package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.TableDisplay;
import records.gui.grid.RectangleBounds;
import records.importers.ClipboardUtils;
import records.transformations.Filter;
import records.transformations.TransformationInfo;
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
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
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
            @When(seed=6793627309703186619L) @NumTables(maxTables = 1) @MustIncludeNumber @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original,
            @When(seed=9064559552451687290L) @From(GenRandom.class) Random r) throws Exception
    {
        // Save the table, then open GUI and load it, then add a filter transformation (rename to keeprows)
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, original.mgr).get();
        TestUtil.sleep(1000);
        CellPosition targetPos = new CellPosition(CellPosition.row(1), CellPosition.col(TestUtil.fx(() -> Utility.filterOptional(mainWindowActions._test_getTableManager().getAllTables().stream().map(t -> Optional.ofNullable((TableDisplay) t.getDisplay()))).mapToInt(d -> d.getBottomRightIncl().columnIndex + 1).max().orElse(1))));
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
        Column srcColumn = original.data().getData().getColumns().stream().filter(c -> TestUtil.checkedToRuntime(() -> c.getType().isNumber())).findFirst().orElseGet((Supplier<Column>)(() -> {throw new AssertionError("No numeric column");}));
        // Pick arbitrary value as cut-off:
        @Value Number cutOff = (Number)srcColumn.getType().getCollapsed(r.nextInt(srcColumn.getLength()));
        
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
        
        assertEquals(new ComparisonExpression(ImmutableList.of(new ColumnReference(srcColumn.getName(), ColumnReferenceType.CORRESPONDING_ROW), new NumericLiteral(cutOff, null)), ImmutableList.of(ComparisonOperator.GREATER_THAN)), Utility.filterClass(mainWindowActions._test_getTableManager().streamAllTables(), Filter.class).findFirst().get().getFilterExpression());
                
        Optional<ImmutableList<Pair<ColumnId, List<@Value Object>>>> clip = TestUtil.<Optional<ImmutableList<Pair<ColumnId, List<@Value Object>>>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(original.mgr.getTypeManager()));
        assertTrue(clip.isPresent());
        // Need to fish out first column from clip, then compare item:
        List<@Value Object> expected = IntStream.range(0, srcColumn.getLength()).mapToObj(i -> TestUtil.checkedToRuntime(() -> srcColumn.getType().getCollapsed(i))).filter(x -> Utility.compareNumbers(x, cutOff) > 0).collect(Collectors.toList());
        TestUtil.assertValueListEqual("Filtered", expected, Utility.pairListToMap(clip.get()).get(srcColumn.getName()));
    }
}
