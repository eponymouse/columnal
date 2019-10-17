package test.gui.expressionEditor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TaggedTypeDefinition;
import records.gui.MainWindow.MainWindowActions;
import records.transformations.Calculate;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationSupplier;
import utility.gui.FXUtility;

import static org.junit.Assert.assertNotNull;

@OnThread(Tag.Simulation)
public class TestTypeEditorCompletion extends BaseTestEditorCompletion
{
    @SuppressWarnings("nullness")
    private MainWindowActions mainWindowActions;

    private void loadTypeExpression(String typeExpressionSrc, TaggedTypeDefinition... taggedTypes) throws Exception
    {
        TableManager toLoad = new DummyManager();
        toLoad.record(
                new ImmediateDataSource(toLoad,
                        new InitialLoadDetails(new TableId("IDS"), null, new CellPosition(CellPosition.row(1), CellPosition.col(1)), null),
                        new EditableRecordSet(
                                ImmutableList.of(rs -> DataType.NUMBER.makeImmediateColumn(new ColumnId("My Number"), DataTypeUtility.value(0)).apply(rs)),
                                (SimulationSupplier<Integer>)() -> 0)));
        
        mainWindowActions = TestUtil.openDataAsTable(windowToUse, toLoad).get();
        sleep(1000);
        // Start creating column 
        correctTargetWindow();
        Node expandRight = lookup(".expand-arrow").match(n -> TestUtil.fx(() -> FXUtility.hasPseudoclass(n, "expand-right"))).<Node>query();
        assertNotNull(expandRight);
        // Won't happen, assertion will fail:
        if (expandRight == null) return;
        clickOn(expandRight);
        write("Col");
        push(KeyCode.TAB);
    }
    
    @Test
    public void testCore() throws Exception
    {
        loadTypeExpression("");
        checkCompletions(
            c("Number", 0, 0),
            c("Text", 0, 0),
            c("Date", 0, 0),
            c("DateTime", 0, 0),
            c("DateYM", 0, 0)
        );
        write("Dat");

        checkCompletions(c("Number", 0, 0));
        checkCompletions(c("Text", 0, 0));
        checkCompletions(c("Date", 0, 3));
        checkCompletions(c("DateTime", 0, 3));
        checkCompletions(c("DateYM", 0, 3));
    }
}
