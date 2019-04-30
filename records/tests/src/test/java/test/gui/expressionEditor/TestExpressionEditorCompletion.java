package test.gui.expressionEditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Ignore;
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
import records.gui.lexeditor.EditorDisplay;
import records.gui.lexeditor.completion.LexAutoCompleteWindow;
import records.gui.lexeditor.completion.LexCompletion;
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
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
        TestUtil.doubleOk(this);
        sleep(500);
        return TestUtil.checkNonNull(((Calculate)mainWindowActions._test_getTableManager().getSingleTableOrThrow(new TableId("Calc"))).getCalculatedColumns().get(new ColumnId("My Calc")));
    }
    
    private class CompletionCheck
    {
        private final String content;
        private final Pair<@CanonicalLocation Integer, @CanonicalLocation Integer> startInclToEndIncl;

        public CompletionCheck(String content, Pair<@CanonicalLocation Integer, @CanonicalLocation Integer> startInclToEndIncl)
        {
            this.content = content;
            this.startInclToEndIncl = startInclToEndIncl;
        }
    }
    
    private CompletionCheck c(String content, int startIncl, int endIncl)
    {
        @SuppressWarnings("units")
        Pair<@CanonicalLocation Integer, @CanonicalLocation Integer> pos = new Pair<>(startIncl, endIncl);
        return new CompletionCheck(content, pos);
    }
    
    private void checkCompletions(CompletionCheck... checks)
    {
        EditorDisplay editorDisplay = lookup(".editor-display").query();
        push(KeyCode.HOME);
        int prevPos = -1;
        int curPos;
        while (prevPos != (curPos = TestUtil.fx(() -> editorDisplay.getCaretPosition())))
        {
            // Check completions here
            @Nullable LexAutoCompleteWindow window = Utility.filterClass(listWindows().stream(), LexAutoCompleteWindow.class).findFirst().orElse(null);
            List<LexCompletion> showing = TestUtil.fx(() -> window == null ? ImmutableList.<LexCompletion>of() : window._test_getShowing());
            for (CompletionCheck check : checks)
            {
                if (check.startInclToEndIncl.getFirst() <= curPos && curPos <= check.startInclToEndIncl.getSecond())
                {
                    ImmutableList<LexCompletion> matching = showing.stream().filter(l -> l.content.equals(check.content)).collect(ImmutableList.toImmutableList());
                    if (matching.isEmpty())
                    {
                        fail("Did not find completion {{{" + check.content + "}}} at caret position " + curPos);
                    }
                    else if (matching.size() > 1)
                    {
                        fail("Found duplicate completions {{{" + check.content + "}}} at caret position " + curPos);
                    }
                    assertEquals(matching.get(0).startPos, check.startInclToEndIncl.getFirst().intValue());
                }
            }
            prevPos = curPos;
            push(KeyCode.RIGHT);
        }
    }
    
    @Test
    public void testCompColumn() throws Exception
    {
        loadExpression("@unfinished \"\"");
        checkCompletions(c("My Number", 0, 0));
        write("My Nu");
        checkCompletions(c("My Number", 0, 5));
        write("Q");
        checkCompletions(c("My Number", 0, 5));
        write("+t");
        // Completions which don't match should still show at start of token:
        checkCompletions(c("My Number", 0, 5), 
                c("true", 7, 8),
                c("false", 0, 0),
                c("false", 7, 7),
                c("@then", 7, 7),
                c("@then", 8, 8),
                c("@if", 0, 0),
                c("@if", 7, 7));
    }
    
    @Test
    public void testColumn1() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("My Nu");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals(new ColumnReference(new ColumnId("My Number"), ColumnReferenceType.CORRESPONDING_ROW), finish());
    }

    @Test
    public void testColumn1b() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("My Nu");
        checkPosition();
        // Click first cell:
        Node cell = TestUtil.checkNonNull(lookup(".lex-completion").<Node>queryAll().stream().min(Comparator.comparing(c -> TestUtil.fx(() -> c.localToScreen(c.getBoundsInLocal()).getMinY()))).orElse(null));
        // Doesn't matter if registered as double click or two single:
        clickOn(point(cell));
        clickOn(point(cell));
        assertEquals(new ColumnReference(new ColumnId("My Number"), ColumnReferenceType.CORRESPONDING_ROW), finish());
    }

    @Test
    public void testColumn2() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("@entire My Nu");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals(new ColumnReference(new ColumnId("My Number"), ColumnReferenceType.WHOLE_COLUMN), finish());
    }

    @Test
    public void testColumn2a() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("@entire MY NU");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals(new ColumnReference(new ColumnId("My Number"), ColumnReferenceType.WHOLE_COLUMN), finish());
    }

    @Test
    public void testColumn2b() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("@ent");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals(new ColumnReference(new ColumnId("My Number"), ColumnReferenceType.WHOLE_COLUMN), finish());
    }

    private void checkPosition()
    {
        EditorDisplay editorDisplay = lookup(".editor-display").query();
        // Caret will update on blink, but let's not wait around for that:
        TestUtil.fx_(() -> editorDisplay._test_queueUpdateCaret());
        sleep(200);
        ImmutableList<LexAutoCompleteWindow> completions = Utility.filterClass(listWindows().stream(), LexAutoCompleteWindow.class).collect(ImmutableList.toImmutableList());
        assertEquals(1, completions.size());
        Node caret = lookup(".document-caret").query();
        double caretBottom = TestUtil.fx(() -> caret.localToScreen(caret.getBoundsInLocal()).getMaxY());
        MatcherAssert.assertThat(TestUtil.fx(() -> completions.get(0).getY()), Matchers.closeTo(caretBottom, 2.0));
        @SuppressWarnings("units") // Because passes through IntStream
        @CanonicalLocation int targetStartPos = TestUtil.fx(() -> completions.get(0)._test_getShowing().stream().mapToInt(c -> c.startPos).min().orElse(0));
        double edX = TestUtil.fx(() -> FXUtility.getCentre(editorDisplay._test_getCaretBounds(targetStartPos)).getX());
        Collection<Node> compText = TestUtil.fx(() -> lookup(n -> n instanceof Text && n.getScene() == completions.get(0).getScene()).queryAll());
        double compX = TestUtil.fx(() -> compText.stream().mapToDouble(t -> t.localToScreen(t.getBoundsInLocal()).getMinX()).min()).orElse(-1);
        MatcherAssert.assertThat(compX, Matchers.closeTo(edX, 0.5));
    }

    @Test
    public void testIsolatedKeyword() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("@el");
        checkPosition();
        // Should be single completion that is auto-selected:
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
        checkPosition();
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
        checkPosition();
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
        checkPosition();
        push(KeyCode.ENTER);
        // It's going to be invalid due to the empty bits:
        assertEquals(new IfThenElseExpression(new InvalidOperatorExpression(ImmutableList.of()), new InvalidOperatorExpression(ImmutableList.of()), new InvalidOperatorExpression(ImmutableList.of())), finish());
    }

    @Test
    public void testLong() throws Exception
    {
        // Check a long multi-line expression still shows completion in right place:
        loadExpression("@if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif");
        push(KeyCode.END);
        write("+My Number");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals("@if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif + @column My Number", finish().toString());
    }
}
