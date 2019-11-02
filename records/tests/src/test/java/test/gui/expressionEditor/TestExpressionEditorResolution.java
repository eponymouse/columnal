package test.gui.expressionEditor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.Test;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.MemoryNumericColumn;
import records.data.Table;
import records.data.Table.InitialLoadDetails;
import records.data.TableManager;
import records.data.datatype.NumberInfo;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import records.transformations.Calculate;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;

import static org.junit.Assert.assertEquals;

public class TestExpressionEditorResolution extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait, EnterExpressionTrait, PopupTrait
{
    private void testLoadNameResolution(String expressionSrc, String expectedLoaded) throws Exception
    {
        TableManager orig = new DummyManager();
        Table data = new ImmediateDataSource(orig, TestUtil.ILD, new EditableRecordSet(ImmutableList.of(rs -> new MemoryNumericColumn(rs, new ColumnId("round"), NumberInfo.DEFAULT, ImmutableList.of(), 0L)), () -> 0));
        orig.record(data);
        Table calc = new Calculate(orig, new InitialLoadDetails(new CellPosition(CellPosition.row(4), CellPosition.col(4))), data.getId(), ImmutableMap.of(new ColumnId("Calc Col"), TestUtil.parseExpression(expressionSrc, orig.getTypeManager(), FunctionList.getFunctionLookup(orig.getUnitManager()))));
        orig.record(calc);
        
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, orig).get();
        sleep(1000);
        try
        {
            CellPosition targetPos = new CellPosition(CellPosition.row(5), CellPosition.col(5));
            
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            
            clickOnItemInBounds(lookup(".table-display-column-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos));
            sleep(500);
            push(KeyCode.TAB);
            sleep(100);
            String content = getEditorDisplay()._test_getEditor()._test_getRawText();

            assertEquals(expectedLoaded, content);

            // Close dialog, ignoring errors:
            TestUtil.doubleOk(this);
        }
        finally
        {
            Stage s = windowToUse;
            Platform.runLater(() -> s.hide());
        }
    }

    private EditorDisplay getEditorDisplay()
    {
        Node focusOwner = getFocusOwner();
        if (!(focusOwner instanceof EditorDisplay))
            throw new RuntimeException("Focus owner is " + (focusOwner == null ? "null" : focusOwner.getClass().toString()));
        return (EditorDisplay) focusOwner;
    }

    @Test
    public void checkLoad1() throws Exception
    {
        testLoadNameResolution("var\\\\x", "x");
    }

    @Test
    public void checkLoad2() throws Exception
    {
        testLoadNameResolution("@call function\\\\number\\abs(3)", "abs(3)");
    }

    @Test
    public void checkLoad3() throws Exception
    {
        testLoadNameResolution("@call function\\\\number\\round(3)", "function\\\\round(3)");
    }

    @Test
    public void checkLoad3b() throws Exception
    {
        testLoadNameResolution("column\\\\round", "column\\\\round");
    }

    @Test
    public void checkLoad4() throws Exception
    {
        testLoadNameResolution("@define var\\\\round = 3 @then var\\\\round + column\\\\round @enddefine", "@definevar\\\\round=3@thenvar\\\\round+column\\\\round@enddefine");
    }

    @Test
    public void checkLoad4b() throws Exception
    {
        testLoadNameResolution("@define var\\\\round 2 = 3 @then var\\\\round 2 + column\\\\round @enddefine", "@defineround 2=3@thenround 2+column\\\\round@enddefine");
    }

    @Test
    public void checkLoad5() throws Exception
    {
        testLoadNameResolution("@define var\\\\abs = 2 @then var\\\\abs + @call function\\\\number\\abs(-4) @enddefine + @call function\\\\number\\abs(3)", "@definevar\\\\abs=2@thenvar\\\\abs+function\\\\abs(-4)@enddefine+abs(3)");
    }

    @Test
    public void checkLoad6() throws Exception
    {
        testLoadNameResolution("@if 2 =~ var\\\\abs @then var\\\\abs + @call function\\\\number\\abs(-4) @else @call function\\\\number\\abs(-5) @endif + @call function\\\\number\\abs(3)", "@if2=~var\\\\abs@thenvar\\\\abs+function\\\\abs(-4)@elseabs(-5)@endif+abs(3)");
    }
}
