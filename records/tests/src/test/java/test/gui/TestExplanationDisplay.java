package test.gui;

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
import records.transformations.Check;
import records.transformations.Check.CheckType;
import records.transformations.expression.Expression;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
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
public class TestExplanationDisplay extends FXApplicationTest implements ScrollToTrait
{
    private static final CellPosition CHECK_POS = new CellPosition(CellPosition.row(13), CellPosition.col(9));
    
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
        tempManager.record(new ImmediateDataSource(tempManager, new InitialLoadDetails(new TableId("T1"), null, null), new EditableRecordSet(columns, () -> 4)));

        columns.clear();
        columns.add(nums("asc", 1, 2, 3, 4));
        columns.add(text("alphabet animals", "Aardvark", "Bear", "Cat", "Deer"));
        tempManager.record(new ImmediateDataSource(tempManager, new InitialLoadDetails(new TableId("T2"), null, null), new EditableRecordSet(columns, () -> 4)));
        
        mainWindowActions = TestUtil.openDataAsTable(windowToUse, tempManager).get();
    }

    private static SimulationFunction<RecordSet, EditableColumn> bools(String name, boolean... values)
    {
        return rs -> new MemoryBooleanColumn(rs, new ColumnId(name), Utility.<Boolean, Either<String, Boolean>>mapList(Booleans.asList(values), Either::right), false);
    }

    private static SimulationFunction<RecordSet, EditableColumn> nums(String name, Number... values)
    {
        return rs -> new MemoryNumericColumn(rs, new ColumnId(name), new NumberInfo(Unit.SCALAR), Utility.<Number, Either<String, Number>>mapList(Arrays.asList(values), Either::right), 0);
    }

    private static SimulationFunction<RecordSet, EditableColumn> text(String name, String... values)
    {
        return rs -> new MemoryStringColumn(rs, new ColumnId(name), Utility.<String, Either<String, String>>mapList(Arrays.asList(values), Either::right), "");
    }


    private void addCheck(String srcTable, CheckType checkType, String expressionSrc) throws InternalException, UserException
    {
        Expression expression = Expression.parse(null, expressionSrc, mainWindowActions._test_getTableManager().getTypeManager(), FunctionList.getFunctionLookup(mainWindowActions._test_getTableManager().getUnitManager()));
        Check check = new Check(mainWindowActions._test_getTableManager(), new InitialLoadDetails(null, CHECK_POS, null), new TableId(srcTable), checkType, expression);
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
        addCheck("T2", CheckType.ALL_ROWS, "@column asc < 4");
        testFailureExplanation("\u2192asc was 4, using asc (row 4)", "\u2192asc < 4 was false");
    }

    @Test
    public void testExplanationSimple2b() throws UserException, InternalException
    {
        addCheck("T2", CheckType.STANDALONE, "@call @function all(@entire asc, ? < 4)");
        testFailureExplanation(
     //"\u2192asc was 4, using asc (row 4)",
            "? was 4",
            "? < 4 was false",
            "all(\u2195asc, ? < 4) was false, using asc (row 4)");
    }

    @Test
    public void testExplanationSimple3() throws UserException, InternalException
    {
        addCheck("T2", CheckType.ALL_ROWS, "@column asc < @call @function text length(@column alphabet animals)");
        testFailureExplanation(
     "\u2192asc was 3, using asc (row 3)",
            "\u2192alphabet animals was \"Cat\", using alphabet animals (row 3)",
            "text length(\u2192alphabet animals) was 3",
            "\u2192asc < text length(\u2192alphabet animals) was false");
    }

    @Test
    public void testExplanationMatch() throws UserException, InternalException
    {
        addCheck("T2", CheckType.NO_ROWS, "@match (@column asc, @column alphabet animals) @case (_n, _) @given n > 5 @then false @case (_, _animal) @then (_n = @call @function text length(animal)) & (n > 5) @endmatch");
        testFailureExplanation(
                "(\u2192asc, \u2192alphabet animals) was (1, \"Aardvark\"), using asc (row 1), alphabet animals (row 1)",
                "(_n, _) matched",
                "n was 1",
                "n > 5 was false",
                "(_, _animal) matched",
                "animal was \"Aardvark\"",
                "text length(animal) was 8",
                "_n matched",
                "_n = text length(animal) was true",
                "n was 8",
                "n > 5 was true",
                "(_n = text length(animal)) & (n > 5) was true",
                "match (\u2192asc, \u2192alphabet animals) case (_n, _) given n > 5 then false case (_, _animal) then (_n = text length(animal)) & (n > 5) endmatch was true");
    }

    @Test
    public void testExplanationRepetitiveIf() throws UserException, InternalException
    {
        addCheck("T2", CheckType.NO_ROWS, "@if (@column asc + 1) > 3 @then (@column asc + 1) > 4 @else (@column asc + 1) > 3 @endif");
        testFailureExplanation(
                "\u2192asc was 4, using asc (row 4)",
                "\u2192asc + 1 was 5",
                "(\u2192asc + 1) > 3 was true",
                "(\u2192asc + 1) > 4 was true",
                "if (\u2192asc + 1) > 3 then (\u2192asc + 1) > 4 else (\u2192asc + 1) > 3 endif was true"
        );
    }  

    private void testFailureExplanation(String... lines)
    {
        TestUtil.fx_(() -> mainWindowActions._test_getVirtualGrid().findAndSelect(Either.left(CellPosition.ORIGIN)));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), CHECK_POS);
        clickOn("Fail");
        sleep(1000);
        TextFlow textFlow = lookup(".explanation-flow").query();
        assertNotNull(textFlow);
        String allText = TestUtil.fx(() -> textFlow.getChildren().stream().filter(t -> t instanceof Text).map(t -> ((Text)t).getText()).collect(Collectors.joining()));
        String[] splitText = allText.split("\n", -1);
        assertArrayEquals(lines, splitText);
    }

}
