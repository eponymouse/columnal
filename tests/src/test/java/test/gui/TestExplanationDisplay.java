package test.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.primitives.Booleans;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.Before;
import org.junit.Test;
import records.data.*;
import records.data.Table.InitialLoadDetails;
import records.data.datatype.NumberInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.transformations.Check;
import records.transformations.Check.CheckType;
import records.transformations.expression.Expression;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static test.TestUtil.fx_;

@OnThread(Tag.Simulation)
public class TestExplanationDisplay extends FXApplicationTest implements ScrollToTrait, ClickOnTableHeaderTrait
{
    private static final CellPosition CHECK_POS = new CellPosition(CellPosition.row(20), CellPosition.col(9));
    
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private MainWindowActions mainWindowActions;
    
    @Before
    public void loadSourceTables() throws Exception
    {
        TableManager tempManager = DummyManager.make();
        List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
        columns.add(bools("all true", true, true, true, true));
        columns.add(bools("half false", false, true, false, true));
        columns.add(bools("all false", false, false, false, false));
        tempManager.record(new ImmediateDataSource(tempManager, new InitialLoadDetails(new TableId("T1"), null, null, null), new EditableRecordSet(columns, () -> 4)));

        columns.clear();
        columns.add(nums("asc", 1, 2, 3, 4));
        columns.add(text("alphabet animals", "Aardvark", "Bear", "Cat", "Deer"));
        tempManager.record(new ImmediateDataSource(tempManager, new InitialLoadDetails(new TableId("T2"), null, null, null), new EditableRecordSet(columns, () -> 4)));
        
        mainWindowActions = TestUtil.openDataAsTable(windowToUse, tempManager).get();
    }

    private static SimulationFunction<RecordSet, EditableColumn> bools(@ExpressionIdentifier String name, boolean... values)
    {
        return rs -> new MemoryBooleanColumn(rs, new ColumnId(name), Utility.<Boolean, Either<String, Boolean>>mapList(Booleans.asList(values), Either::right), false);
    }

    private static SimulationFunction<RecordSet, EditableColumn> nums(@ExpressionIdentifier String name, Number... values)
    {
        return rs -> new MemoryNumericColumn(rs, new ColumnId(name), new NumberInfo(Unit.SCALAR), Utility.<Number, Either<String, Number>>mapList(Arrays.asList(values), Either::right), 0);
    }

    private static SimulationFunction<RecordSet, EditableColumn> text(@ExpressionIdentifier String name, String... values)
    {
        return rs -> new MemoryStringColumn(rs, new ColumnId(name), Utility.<String, Either<String, String>>mapList(Arrays.asList(values), Either::right), "");
    }


    private void addCheck(@ExpressionIdentifier String srcTable, CheckType checkType, String expressionSrc) throws InternalException, UserException
    {
        Expression expression = TestUtil.parseExpression(expressionSrc, mainWindowActions._test_getTableManager().getTypeManager(), FunctionList.getFunctionLookup(mainWindowActions._test_getTableManager().getUnitManager()));
        Check check = new Check(mainWindowActions._test_getTableManager(), new InitialLoadDetails(null, null, CHECK_POS, null), new TableId(srcTable), checkType, expression);
        mainWindowActions._test_getTableManager().record(check);
        // Wait for GUI:
        sleep(2000);
    }

    @Test
    public void testExplanationSimple1() throws UserException, InternalException
    {
        addCheck("T2", CheckType.STANDALONE, "0 > 1");
        testFailureExplanation("0 > 1 was false");
    }
    
    @Test
    public void testExplanationSimple2() throws UserException, InternalException
    {
        addCheck("T2", CheckType.ALL_ROWS, "column\\\\asc < 4");
        testFailureExplanation("asc < 4 was false", "asc was 4, using asc (row 4)");
    }

    @Test
    public void testExplanationSimple2b() throws UserException, InternalException
    {
        addCheck("T2", CheckType.STANDALONE, "@call function\\\\all(table\\\\T2#asc, ? < 4)");
        testFailureExplanation(
     //"asc was 4, using asc (row 4)"
      "all(T2#asc, ? < 4) was false, using asc (row 4)",
            "? < 4 was false",
            "? was 4");
    }

    @Test
    public void testExplanationSimple3() throws UserException, InternalException
    {
        addCheck("T2", CheckType.ALL_ROWS, "column\\\\asc < @call function\\\\text length(column\\\\alphabet animals)");
        testFailureExplanation(
      "asc < text length(alphabet animals) was false",
            "text length(alphabet animals) was 3",
            "alphabet animals was \"Cat\", using alphabet animals (row 3)",
            "asc was 3, using asc (row 3)"
            );
    }

    @Test
    public void testExplanationError() throws UserException, InternalException
    {
        addCheck("T2", CheckType.ALL_ROWS, "(1 / (column\\\\asc - 3)) < 1.1");
        testFailureExplanation(
     "In: (1 / (column\\\\asc - 3)) < 1.1",
           "Division by zero in: 1 / (column\\\\asc - 3)",
           "asc - 3 was 0",
           "asc was 3, using asc (row 3)");
    }

    @Test
    public void testExplanationError2() throws UserException, InternalException
    {
        addCheck("T2", CheckType.ALL_ROWS, "@if (column\\\\asc > 2) @then (1 / (column\\\\asc - 3)) @else 0 @endif < 1.1");
        testFailureExplanation(
      "In: @if column\\\\asc > 2 @then 1 / (column\\\\asc - 3) @else 0 @endif < 1.1",
            "In: @if column\\\\asc > 2 @then 1 / (column\\\\asc - 3) @else 0 @endif",
            "Division by zero in: 1 / (column\\\\asc - 3)",
            "asc - 3 was 0",
            "asc > 2 was true",
            "asc was 3, using asc (row 3)"
            );
    }

    @Test
    public void testExplanationMatch() throws UserException, InternalException
    {
        addCheck("T2", CheckType.NO_ROWS, "@match (num: column\\\\asc, animal: column\\\\alphabet animals) @case (num: n) @given n > 5 @then false @case (num: _, animal: animal) @then (@call function\\\\text length(animal) =~ n) & (n > 5) @endmatch");
        testFailureExplanation(
                "@match (num: asc, animal: alphabet animals) @case (num: n) @given n > 5 @then false @case (num: _, animal: animal) @then (text length(animal) =~ n) & (n > 5) @endmatch was true",
                "(text length(animal) =~ n) & (n > 5) was true",
                "n > 5 was true",
                "n was 8",
                "text length(animal) =~ n was true",
                "text length(animal) was 8",
                "animal was \"Aardvark\"",
                "(num: _, animal: animal) matched",
                "n > 5 was false",
                "n was 1",
                "(num: n) matched",
                "(num: asc, animal: alphabet animals) was (animal: \"Aardvark\", num: 1), using asc (row 1), alphabet animals (row 1)");
    }

    @Test
    public void testExplanationRepetitiveIf() throws UserException, InternalException
    {
        addCheck("T2", CheckType.NO_ROWS, "@if (column\\\\asc + 1) > 3 @then (column\\\\asc + 1) > 4 @else (column\\\\asc + 1) > 3 @endif");
        testFailureExplanation(
      "@if (asc + 1) > 3 @then (asc + 1) > 4 @else (asc + 1) > 3 @endif was true",
            "(asc + 1) > 4 was true",
            "(asc + 1) > 3 was true",
            "asc + 1 was 5",
            "asc was 4, using asc (row 4)"
        );
    }  

    private void testFailureExplanation(String... lines)
    {
        TestUtil.fx_(() -> mainWindowActions._test_getVirtualGrid().findAndSelect(Either.left(CellPosition.ORIGIN.offsetByRowCols(1, 1))));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), CHECK_POS);
        CellPosition resultPos = CHECK_POS.offsetByRowCols(1, 0);
        clickOnItemInBounds(lookup(".check-result"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(resultPos, resultPos));
        sleep(1000);
        TextFlow textFlow = lookup(".explanation-flow").query();
        assertNotNull(textFlow);
        String allText = TestUtil.fx(() -> textFlow.getChildren().stream().filter(t -> t instanceof Text).map(t -> ((Text)t).getText()).collect(Collectors.joining()));
        String[] splitText = allText.split("\n(\u2b11 )?", -1);
        assertArrayEquals(lines, splitText);
    }

}
