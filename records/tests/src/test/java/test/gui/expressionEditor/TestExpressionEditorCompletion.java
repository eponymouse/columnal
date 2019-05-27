package test.gui.expressionEditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import log.Log;
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
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.Expression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gui.trait.AutoCompleteTrait;
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
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@OnThread(Tag.Simulation)
public class TestExpressionEditorCompletion extends FXApplicationTest implements PopupTrait, AutoCompleteTrait
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
        correctTargetWindow();
        clickOn(lookup(".column-title").match((Label l) -> TestUtil.fx(() -> l.getText()).startsWith("My C")).<Label>query());
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
        private final ImmutableList<Pair<@CanonicalLocation Integer, @CanonicalLocation Integer>> startInclToEndIncl;

        public CompletionCheck(String content, ImmutableList<Pair<@CanonicalLocation Integer, @CanonicalLocation Integer>> startInclToEndIncl)
        {
            this.content = content;
            this.startInclToEndIncl = startInclToEndIncl;
        }
    }
    
    private CompletionCheck c(String content, int... startEndInclPairs)
    {
        ImmutableList.Builder<Pair<@CanonicalLocation Integer, @CanonicalLocation Integer>> pairs = ImmutableList.builder();
        for (int i = 0; i < startEndInclPairs.length; i += 2)
        {
            @SuppressWarnings("units")
            Pair<@CanonicalLocation Integer, @CanonicalLocation Integer> pos = new Pair<>(startEndInclPairs[i], startEndInclPairs[i + 1]);
            pairs.add(pos);
        }
        return new CompletionCheck(content, pairs.build());
    }
    
    private void checkCompletions(CompletionCheck... checks)
    {
        EditorDisplay editorDisplay = lookup(".editor-display").query();
        // We go from end in case there is a trailing space,
        // which will be removed once we go backwards:
        push(KeyCode.END);
        int prevPos = -1;
        int curPos;
        while (prevPos != (curPos = TestUtil.fx(() -> editorDisplay.getCaretPosition())))
        {
            // Check completions here
            @Nullable LexAutoCompleteWindow window = Utility.filterClass(listWindows().stream(), LexAutoCompleteWindow.class).findFirst().orElse(null);
            List<LexCompletion> showing = TestUtil.fx(() -> window == null ? ImmutableList.<LexCompletion>of() : window._test_getShowing());
            for (CompletionCheck check : checks)
            {
                ImmutableList<LexCompletion> matching = showing.stream().filter(l -> Objects.equals(l.content, check.content)).collect(ImmutableList.toImmutableList());
                
                boolean wasChecked = false;
                for (Pair<Integer, Integer> startInclToEndIncl : check.startInclToEndIncl)
                {
                    if (startInclToEndIncl.getFirst() <= curPos && curPos <= startInclToEndIncl.getSecond())
                    {
                        wasChecked = true;
                        if (matching.isEmpty())
                        {
                            fail("Did not find completion {{{" + check.content + "}}} at caret position " + curPos);
                        }
                        else if (matching.size() > 1)
                        {
                            fail("Found duplicate completions {{{" + check.content + "}}} at caret position " + curPos);
                        }
                        //assertEquals("Start pos for {{{" + check.content + "}}}", startInclToEndIncl.getFirst().intValue(), matching.get(0).startPos);
                    }
                }
                if (!wasChecked)
                {
                    if (matching.size() > 0)
                    {
                        fail("Found completion {{{" + check.content + "}}} which should not be present at " + curPos);
                    }
                }
            }
            prevPos = curPos;
            push(KeyCode.LEFT);
        }
    }
    
    @Test
    public void testCompColumn() throws Exception
    {
        loadExpression("@unfinished \"\"");
        checkCompletions(
            c("My Number", 0, 0),
            c("@entire My Number", 0, 0),
            c("My Calc"), // Shouldn't see our own column
            c("@entire My Calc")
        );
        write("My Nu");
        checkCompletions(
            c("My Number", 0, 5),
            c("@entire My Number", 0, 5),
            c("My Calc"), // Shouldn't see our own column
            c("@entire My Calc")
        );
        push(KeyCode.END);
        write("Q");
        checkCompletions(
            c("My Number", 0, 5),
            c("@entire My Number", 0, 5),
            c("My Calc"), // Shouldn't see our own column
            c("@entire My Calc")
        );
        push(KeyCode.END);
        write("+t");
        // Content is now:
        // My NuQ+t
        
        // Completions which don't match should still show at start of token:
        checkCompletions(c("My Number", 0,5, 7,7), 
                c("true", 0,0, 7,8),
                c("false", 0,0, 7,7),
                c("@then", 0,0, 7,8),
                c("@if", 0,0, 7,7));
    }

    @Test
    public void testRelatedColumn() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("Nu");
        checkCompletions(
            c("My Number", 0, 2),
            c("@entire My Number", 0, 2)
        );
    }

    @Test
    public void testRelatedFunction() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("text");
        checkCompletions(
            c("from text to()", 0, 4)
        );
    }
    
    @Test
    public void testCompIf() throws Exception
    {
        loadExpression("@if true | false @then 12{m} @else @call@function from text to(type{Number{m}}, \"34\")@endif");
        // Will turn into:
        // @iftrue|false@then12{m}@elsefrom text to(type{Number{m}},"34")@endif
        checkCompletions(
            // match at the start of every token:
            c("@match", 0,0, 3,3, 8,8, 18,18, 23,23, 28,28, 41,41, 56,56, 57,57, 61,61, 62,62, 68,68),
            c("@then", 0,0, 3,4, 8,8, 18,18, 23,23, 28,28, 41,41, 56,56, 57,57, 61,61, 62,62, 68,68),
            c("false", 0,0, 3,3, 8,13, 18,18, 23,23, 28,29, 41,41, 56,56, 57,57, 61,61, 62,62, 68,68),
            c("type{}", 0,0, 3,4, 8,8, 18,18, 23,23, 28,28, 41,41, 56,56, 57,57, 61,61, 62,62, 68,68),
            c("Boolean", 46,46, 55,55),
            c("minute", 21,22, 53,54),
            c("hour", 21,21, 53, 53)
        );
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
        Collection<Node> compText = TestUtil.fx(() -> lookup(n -> n instanceof Text && n.getStyleClass().contains("styled-text") &&  !((Text)n).getText().isEmpty() && !ImmutableList.of("Related", "Operators", "Help").contains(((Text)n).getText()) && n.getScene() == completions.get(0).getScene()).queryAll());
        double compX = TestUtil.fx(() -> compText.stream().mapToDouble(t -> {
            //Log.debug("Text: " + ((Text)t).getText() + " bounds: " + t.localToScreen(t.getBoundsInLocal()));
            return t.localToScreen(t.getBoundsInLocal()).getMinX();
        }).average()).orElse(-1);
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
    
    @Test
    public void testCompPartial() throws Exception
    {
        // From a failing test:
        loadExpression("@unfinished \"\"");
        write("as type(type{Number{1}},from ");
        // Get rid of auto-inserted bracket:
        push(KeyCode.DELETE);
        checkCompletions(
            c("type{}", 0,0, 8,8, 23,23, 24,24),
            c("from text()", 0,0, 8,8, 23,23, 24,29)
        );
    }

    @Test
    public void testNumMinus1() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("12");
        checkPosition();
        scrollLexAutoCompleteToOption("-");
        push(KeyCode.ENTER);
        write("3");
        assertEquals(new AddSubtractExpression(ImmutableList.of(new NumericLiteral(12, null), new NumericLiteral(3, null)), ImmutableList.of(AddSubtractOp.SUBTRACT)), finish());
    }

    @Test
    public void testNumMinus1b() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("123");
        checkPosition();
        push(KeyCode.LEFT);
        scrollLexAutoCompleteToOption("-");
        push(KeyCode.ENTER);
        assertEquals(new AddSubtractExpression(ImmutableList.of(new NumericLiteral(12, null), new NumericLiteral(3, null)), ImmutableList.of(AddSubtractOp.SUBTRACT)), finish());
    }

    @Test
    public void testLongOp() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("1<2");
        checkPosition();
        push(KeyCode.LEFT);
        scrollLexAutoCompleteToOption("<=");
        push(KeyCode.ENTER);
        assertEquals(new ComparisonExpression(ImmutableList.of(new NumericLiteral(1, null), new NumericLiteral(2, null)), ImmutableList.of(ComparisonOperator.LESS_THAN_OR_EQUAL_TO)), finish());
    }

    @Test
    public void testTrailingOperator1() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("1+");
        checkCompletions(
            c("My Number", 0,0, 2,2),
            c("+", 1, 1),
            c("*", 1, 1),
            // Plus-minus shows as multi-char completion of plus:
            c("\u00B1", 1, 2)
        );
    }

    @Test
    public void testTrailingOperator1b() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("(12/34)+");
        checkCompletions(
            c("My Number", 0,1, 4,4, 7,8),
            c("+", 2,3, 5,7),
            c("*", 2,3, 5,7),
            // Plus-minus shows as multi-char completion of plus:
            c("\u00B1", 2,3, 5,8)
        );
    }

    @Test
    public void testTrailingOperator2() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("12<");
        checkCompletions(
            c("My Number", 0,0, 3,3),
            c("+", 1, 2),
            c("<", 1, 2),
            c("<=", 1, 3),
            c("<>", 1, 3)
        );
    }

    @Test
    public void testTrailingOperator2b() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("(12/34)<");
        checkCompletions(
            c("My Number", 0,1, 4,4, 7,8),
            c("+", 2,3, 5,7),
            c("<", 2,3, 5,7),
            c("<=", 2,3, 5,8),
            c("<>", 2,3, 5,8)
        );
    }

    @Test
    public void testEndOperator() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("1+2");
        checkCompletions(
            c("My Number", 0,0, 2,2),
            c("+", 1,1, 3,3),
            c("*", 1,1, 3,3)
        );
    }
}
