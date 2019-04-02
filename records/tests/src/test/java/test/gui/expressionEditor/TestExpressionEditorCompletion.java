package test.gui.expressionEditor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.KeyCode;
import org.hamcrest.Matchers;
import org.junit.Test;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.transformations.Calculate;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gui.trait.PopupTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

@OnThread(Tag.Simulation)
public class TestExpressionEditorCompletion extends FXApplicationTest implements PopupTrait
{
    @SuppressWarnings("nullness")
    private MainWindowActions mainWindowActions;

    private void loadExpression(String expressionSrc) throws Exception
    {
        TableManager toLoad = new DummyManager();
        toLoad.record(
            new ImmediateDataSource(toLoad,
                new InitialLoadDetails(new TableId("IDS"), new CellPosition(CellPosition.row(1), CellPosition.col(1)), null),
                new EditableRecordSet(
                    ImmutableList.of(rs -> DataType.NUMBER.makeImmediateColumn(new ColumnId("My Number"), DataTypeUtility.value(0)).apply(rs)),
                    (SimulationSupplier<Integer>)() -> 0)));
        toLoad.record(new Calculate(toLoad, new InitialLoadDetails(new TableId("Calc"), new CellPosition(CellPosition.row(1), CellPosition.col(6)), null), new TableId("IDS"), ImmutableMap.of(new ColumnId("My Calc"), Expression.parse(null, expressionSrc, toLoad.getTypeManager(), FunctionList.getFunctionLookup(toLoad.getUnitManager())))));

        mainWindowActions = TestUtil.openDataAsTable(windowToUse, toLoad).get();
        sleep(1000);
        // Not much more to do -- just edit the expression 
        clickOn("My Calc");
        push(KeyCode.TAB);
    }
    
    private Expression finish() throws UserException
    {
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn();
        sleep(500);
        return TestUtil.checkNonNull(((Calculate)mainWindowActions._test_getTableManager().getSingleTableOrThrow(new TableId("Calc"))).getCalculatedColumns().get(new ColumnId("My Calc")));
    }
    
    @Test
    public void testColumn() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("My Nu");
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals(new ColumnReference(new ColumnId("My Number"), ColumnReferenceType.CORRESPONDING_ROW), finish());
    }
    
    @Test
    public void testIsolatedKeyword() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("@el");
        push(KeyCode.ENTER);
        // Don't care exactly how it's saved, as long as keyword is in there:
        assertThat(finish().toString(), Matchers.containsString("^aelse"));
    }
    
    @Test
    public void testInsertIfBefore1() throws Exception
    {
        loadExpression("@column My Number");
        push(KeyCode.HOME);
        write("@if ");
        String finished = finish().toString();
        // Don't care exactly how it's saved:
        assertThat(finished, Matchers.containsString("^aif"));
        assertThat(finished, Matchers.containsString("My Number"));
    }

    @Test
    public void testInsertIfBefore1b() throws Exception
    {
        loadExpression("@column My Number");
        push(KeyCode.HOME);
        write("if");
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        String finished = finish().toString();
        // Don't care exactly how it's saved:
        assertThat(finished, Matchers.containsString("^aif"));
        assertThat(finished, Matchers.containsString("My Number"));
    }

    @Test
    public void testInsertIfBefore2() throws Exception
    {
        loadExpression("@column My Number");
        push(KeyCode.HOME);
        write("@iftrue@then");
        push(KeyCode.END);
        write("@else0@endif");
        String finished = finish().toString();
        assertEquals("@if true @then @column My Number @else 0 @endif", finished);
    }
    
    @Test
    public void testWholeIf() throws Exception
    {
        // Default in blank expression should be to insert
        // a complete if statement.
        loadExpression("@unfinished \"\"");
        write("@i");
        push(KeyCode.ENTER);
        // It's going to be invalid due to the empty bits:
        assertEquals(new IfThenElseExpression(new InvalidOperatorExpression(ImmutableList.of()), new InvalidOperatorExpression(ImmutableList.of()), new InvalidOperatorExpression(ImmutableList.of())), finish());
    }
}
