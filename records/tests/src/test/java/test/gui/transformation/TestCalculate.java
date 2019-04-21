package test.gui.transformation;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.assertj.core.util.Arrays;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import org.testfx.service.query.NodeQuery;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryTemporalColumn;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import records.transformations.Calculate;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import test.TestUtil;
import test.gen.GenRandom;
import test.gui.trait.AutoCompleteTrait;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestCalculate extends FXApplicationTest implements ScrollToTrait, AutoCompleteTrait, PopupTrait, ClickTableLocationTrait
{
    private static enum Col
    {
        BOO, YM, BOOT_SIZE 
    }
    
    private @ExpressionIdentifier String getName(Col col)
    {
        switch (col)
        {
            case BOO: return "Boo";
            case YM: return "YM";
            case BOOT_SIZE: return "Boot Size";
        }
        throw new AssertionError("Unknown case: " + col);
    }
    
    @OnThread(Tag.Simulation)
    private EditableColumn makeColumn(Col column, RecordSet rs) throws InternalException, UserException
    {
        ColumnId columnId = new ColumnId(getName(column));
        switch (column)
        {
            case BOO:
                return new MemoryBooleanColumn(rs, columnId, ImmutableList.of(), false);
            case YM:
                return new MemoryTemporalColumn(rs, columnId, new DateTimeInfo(DateTimeType.YEARMONTH), ImmutableList.of(), TestUtil.checkNonNull(DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTH), DateTimeInfo.DEFAULT_VALUE)));
            case BOOT_SIZE:
                return new MemoryNumericColumn(rs, columnId, new NumberInfo(Unit.SCALAR), Stream.empty());
        }
        throw new AssertionError("Unknown case: " + column);
    }
    
    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void testCalculate(@From(GenRandom.class) Random r) throws Exception
    {
        RecordSet orig = new EditableRecordSet(Utility.<Col, SimulationFunction<RecordSet, EditableColumn>>mapListI(ImmutableList.copyOf(Col.values()), (Col c) -> (RecordSet rs) -> makeColumn(c, rs)), () -> 0);
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, null, orig);

        CellPosition calcPos = CellPosition.ORIGIN.offsetByRowCols(1, 5);
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), calcPos);
        
        List<Col> colList = new ArrayList<>(ImmutableList.copyOf(Col.values()));
        Collections.shuffle(colList, r);
        
        push(KeyCode.ENTER);
        clickOn(".id-new-transform");
        clickOn(".id-transform-calculate");
        write("Table1");
        push(KeyCode.ENTER);
        enterInfo(mainWindowActions, r, getName(colList.get(0)));
        
        if (r.nextBoolean())
        {
            // Add by adding new column with right name:
            showContextMenu(withItemInBounds(findColumnTitle(getName(colList.get(1))), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(calcPos, calcPos.offsetByRowCols(3, 3)), (n, p) -> {
            }), null);
            clickOn(r.nextBoolean() ? ".id-virtGrid-column-addBefore" : ".id-virtGrid-column-addAfter");
        }
        else
        {
            // Add by clicking column name:
            clickOnItemInBounds(findColumnTitle(getName(colList.get(1))), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(calcPos, calcPos.offsetByRowCols(3, 3)));
        }
        enterInfo(mainWindowActions, r, getName(colList.get(1)));

        // Now try deleting a column:
        showContextMenu(withItemInBounds(findColumnTitle(getName(colList.get(0))), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(calcPos, calcPos.offsetByRowCols(3, 3)), (n, p) -> {
        }), null);
        clickOn(".id-virtGrid-column-delete");
        sleep(300);
        assertFalse(getCalculate(mainWindowActions).getCalculatedColumns().containsKey(new ColumnId(getName(colList.get(0)))));

        // Delete on from original:
        showContextMenu(withItemInBounds(findColumnTitle(getName(colList.get(2))), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(CellPosition.ORIGIN, CellPosition.ORIGIN.offsetByRowCols(3, 3)), (n, p) -> {
        }), null);
        clickOn(".id-virtGrid-column-delete");
        sleep(300);
        assertFalse(getCalculate(mainWindowActions).getData().getColumnIds().contains(new ColumnId(getName(colList.get(2)))));
    }
    
    private void enterInfo(MainWindowActions mainWindowActions, Random r, @ExpressionIdentifier String columnNameToReplace)
    {
        sleep(300);
        assertTrue(lookup(".expression-editor").tryQuery().isPresent());
        
        boolean clickedExistingForName = r.nextBoolean(); 
        if (clickedExistingForName)
        {
            assertTrue(getFocusOwner() instanceof TextField);
            // Click on existing column name to use it
            clickOnItemInBounds(findColumnTitle(columnNameToReplace), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(CellPosition.ORIGIN, CellPosition.ORIGIN.offsetByRowCols(2, 3)));            moveAndDismissPopupsAtPos(point(".expression-editor .editor-display"));
            clickOn(".expression-editor .editor-display");
            Node focusOwner = getFocusOwner();
            assertTrue("Focus owner: " + (focusOwner == null ? "null" : focusOwner.getClass().toString()), focusOwner instanceof EditorDisplay);
        }
        else
        {
            // Use autocomplete
            autoComplete(columnNameToReplace, true);
        }
        
        push(KeyCode.SHORTCUT, KeyCode.A);
        push(KeyCode.DELETE);
        
        // Copy existing column:
        boolean clickedExistingForExpression = r.nextBoolean();
        if (clickedExistingForExpression)
        {
            clickOnItemInBounds(findColumnTitle(columnNameToReplace), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(CellPosition.ORIGIN, CellPosition.ORIGIN.offsetByRowCols(2, 3)));
        }
        else
        {
            lexComplete(columnNameToReplace);
        }
        
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        assertFalse("Exp Editor should be hidden, after: " + clickedExistingForName + " and " + clickedExistingForExpression, lookup(".expression-editor").tryQuery().isPresent());
        sleep(500);
        Calculate calculate = getCalculate(mainWindowActions);
        Expression expression = TestUtil.checkNonNull(calculate.getCalculatedColumns().get(new ColumnId(columnNameToReplace)));
        MatcherAssert.assertThat(expression, Matchers.isIn(ImmutableList.of(new ColumnReference(new TableId("Table1"), new ColumnId(columnNameToReplace), ColumnReferenceType.CORRESPONDING_ROW), new ColumnReference(new ColumnId(columnNameToReplace), ColumnReferenceType.CORRESPONDING_ROW))));
        
    }

    private Calculate getCalculate(MainWindowActions mainWindowActions)
    {
        return TestUtil.checkNonNull(Utility.filterClass(mainWindowActions._test_getTableManager().streamAllTables(), Calculate.class).findFirst().orElse(null));
    }

    private NodeQuery findColumnTitle(@ExpressionIdentifier String columnNameToReplace)
    {
        return lookup(".column-title").match((Label l) -> TestUtil.fx(() -> l.getText()).equals(columnNameToReplace));
    }
}
