/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui.expressionEditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import test.functions.TFunctionUtil;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import xyz.columnal.gui.lexeditor.completion.LexAutoCompleteWindow;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.expression.*;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.gui.trait.AutoCompleteTrait;
import test.gui.trait.PopupTrait;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;

import java.util.Collection;
import java.util.Comparator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@OnThread(Tag.Simulation)
public class TestExpressionEditorCompletion extends BaseTestEditorCompletion implements PopupTrait, AutoCompleteTrait
{
    @SuppressWarnings("nullness")
    private MainWindowActions mainWindowActions;

    private void loadExpression(String expressionSrc) throws Exception
    {
        TableManager toLoad = new DummyManager();
        toLoad.record(
            new ImmediateDataSource(toLoad,
                new InitialLoadDetails(new TableId("IDS"), null, new CellPosition(CellPosition.row(1), CellPosition.col(1)), null),
                new EditableRecordSet(
                    ImmutableList.of(rs -> ColumnUtility.makeImmediateColumn(DataType.NUMBER, new ColumnId("My Number"), DataTypeUtility.value(0)).apply(rs)),
                    (SimulationSupplier<Integer>)() -> 0)));
        toLoad.record(new Calculate(toLoad, new InitialLoadDetails(new TableId("Calc"), null, new CellPosition(CellPosition.row(1), CellPosition.col(6)), null), new TableId("IDS"), ImmutableMap.of(new ColumnId("My Calc"), TFunctionUtil.parseExpression(expressionSrc, toLoad.getTypeManager(), FunctionList.getFunctionLookup(toLoad.getUnitManager())))));

        mainWindowActions = TAppUtil.openDataAsTable(windowToUse, toLoad).get();
        sleep(1000);
        // Not much more to do -- just edit the expression 
        correctTargetWindow();
        clickOn(TFXUtil.fx(() -> lookup(".column-title").match((Label l) -> TFXUtil.fx(() -> l.getText()).startsWith("My C")).<Label>query()));
        push(KeyCode.TAB);
    }
    
    private Expression finish() throws UserException
    {
        TFXUtil.doubleOk(this);
        sleep(500);
        return TBasicUtil.checkNonNull(((Calculate)mainWindowActions._test_getTableManager().getSingleTableOrThrow(new TableId("Calc"))).getCalculatedColumns().get(new ColumnId("My Calc")));
    }
    
    private String finishExternal() throws UserException
    {
        return finish().save(SaveDestination.TO_FILE, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY);
    }

    @Test
    public void testCompColumn() throws Exception
    {
        loadExpression("@unfinished \"\"");
        checkCompletions(
            c("My Number", 0, 0),
            c("table\\\\IDS",0, 0),
            c("My Calc"), // Shouldn't see our own column
            c("table\\\\Calc") // Shouldn't see our own table
        );
        write("My Nu");
        checkCompletions(
            c("My Number", 0, 5),
            c("table\\\\IDS", 0, 0),
            c("My Calc"), // Shouldn't see our own column
            c("table\\\\Calc") // Shouldn't see our own table
        );
        push(KeyCode.END);
        write("Q");
        checkCompletions(
            c("My Number", 0, 5),
            c("My Calc") // Shouldn't see our own column
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
            c("My Number", 0, 2)
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
        loadExpression("@if true | false @then 12{m} @else @callfunction\\\\from text to(type{Number{m}}, \"34\")@endif");
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
        assertEquals(IdentExpression.column(new ColumnId("My Number")), finish());
    }

    @Test
    public void testColumn1b() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("My Nu");
        checkPosition();
        // Click first cell:
        Node cell = TBasicUtil.checkNonNull(TFXUtil.fx(() -> lookup(".lex-completion").<Node>queryAll().stream().min(Comparator.comparing(c -> c.localToScreen(c.getBoundsInLocal()).getMinY())).orElse(null)));
        // Doesn't matter if registered as double click or two single:
        clickOn(point(cell).atOffset(5, 0));
        clickOn(point(cell).atOffset(5, 0));
        assertEquals(IdentExpression.column(new ColumnId("My Number")), finish());
    }

    @Test
    public void testColumn2() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("table\\\\ID");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals(IdentExpression.table("IDS"), finish());
    }

    @Test
    public void testColumn2a() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("table\\\\id");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals(IdentExpression.table("IDS"), finish());
    }

    @Test
    public void testColumn2b() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("tab");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        assertEquals(IdentExpression.table("IDS"), finish());
    }

    private void checkPosition()
    {
        EditorDisplay editorDisplay = waitForOne(".editor-display");
        // Caret will update on blink, but let's not wait around for that:
        TFXUtil.fx_(() -> editorDisplay._test_queueUpdateCaret());
        sleep(2000);
        ImmutableList<LexAutoCompleteWindow> completions = Utility.filterClass(TFXUtil.fx(() -> listWindows()).stream(), LexAutoCompleteWindow.class).collect(ImmutableList.toImmutableList());
        assertEquals(1, completions.size());
        Node caret = waitForOne(".document-caret");
        double caretBottom = TFXUtil.fx(() -> caret.localToScreen(caret.getBoundsInLocal()).getMaxY());
        MatcherAssert.assertThat(TFXUtil.fx(() -> completions.get(0).getY()), Matchers.closeTo(caretBottom, 2.0));
        @SuppressWarnings("units") // Because passes through IntStream
        @CanonicalLocation int targetStartPos = TFXUtil.fx(() -> completions.get(0)._test_getShowing().stream().mapToInt(c -> c.startPos).min().orElse(0));
        double edX = TFXUtil.fx(() -> FXUtility.getCentre(editorDisplay._test_getCaretBounds(targetStartPos)).getX());
        Collection<Node> compText = TFXUtil.fx(() -> lookup(n -> n instanceof Text && n.getStyleClass().contains("styled-text") &&  !((Text)n).getText().isEmpty() && !ImmutableList.of("Related", "Operators", "Help").contains(((Text)n).getText()) && n.getScene() == completions.get(0).getScene()).queryAll());
        double compX = TFXUtil.fx(() -> compText.stream().mapToDouble(t -> {
            //Log.debug("Text: " + ((Text)t).getText() + " bounds: " + t.localToScreen(t.getBoundsInLocal()));
            return t.localToScreen(t.getBoundsInLocal()).getMinX();
        }).average()).orElse(-1);
        MatcherAssert.assertThat(compX, Matchers.closeTo(edX, 1.0));
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
        assertThat(finishExternal(), Matchers.containsString("^aelse"));
    }
    
    @Test
    public void testInsertIfBefore1() throws Exception
    {
        loadExpression("column\\\\My Number");
        push(KeyCode.HOME);
        write("@if ");
        String finished = finishExternal();
        // Don't care exactly how it's saved:
        assertThat(finished, Matchers.containsString("^aif"));
        assertThat(finished, Matchers.containsString("My Number"));
    }

    @Test
    public void testInsertIfBefore1b() throws Exception
    {
        loadExpression("column\\\\My Number");
        push(KeyCode.HOME);
        write("if");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        String finished = finishExternal();
        // Don't care exactly how it's saved:
        assertThat(finished, Matchers.containsString("^aif"));
        assertThat(finished, Matchers.containsString("My Number"));
    }

    @Test
    public void testInsertIfBefore2() throws Exception
    {
        loadExpression("column\\\\My Number");
        push(KeyCode.HOME);
        write("@iftrue@then");
        checkPosition();
        push(KeyCode.END);
        write("@else0@endif");
        String finished = finishExternal();
        assertEquals("@if true @then column\\\\My Number @else 0 @endif", finished);
    }
    
    @Test
    public void testWholeIfByCompletion() throws Exception
    {
        // Top completion in blank expression should be to insert a complete if statement.
        loadExpression("@unfinished \"\"");
        write("@i");
        checkPosition();
        push(KeyCode.ENTER);
        // It's going to be invalid due to the empty bits:
        assertEquals(IfThenElseExpression.unrecorded(new InvalidOperatorExpression(ImmutableList.of()), new InvalidOperatorExpression(ImmutableList.of()), new InvalidOperatorExpression(ImmutableList.of())), finish());
    }

    @Test
    public void testWholeIfByCompletion2() throws Exception
    {
        // Top completion in blank expression should be to insert a complete if statement.
        loadExpression("@unfinished \"\"");
        write("if");
        checkPosition();
        push(KeyCode.ENTER);
        // It's going to be invalid due to the empty bits:
        assertEquals(IfThenElseExpression.unrecorded(new InvalidOperatorExpression(ImmutableList.of()), new InvalidOperatorExpression(ImmutableList.of()), new InvalidOperatorExpression(ImmutableList.of())), finish());
    }

    @Test
    public void testJustIfByTyping() throws Exception
    {
        // Top completion in blank expression should be to insert a complete if statement.
        loadExpression("@unfinished \"\"");
        write("@i");
        checkPosition();
        write("f");
        // It's going to be invalid due to the empty bits:
        assertEquals(new InvalidOperatorExpression(ImmutableList.of(new InvalidIdentExpression("@if"), new InvalidOperatorExpression(ImmutableList.of()))), finish());
    }

    @Test
    public void testJustIfByCompletion() throws Exception
    {
        // Top completion in blank expression should be to insert a complete if statement.
        loadExpression("@unfinished \"\"");
        write("@i");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        // It's going to be invalid due to the empty bits:
        assertEquals(new InvalidOperatorExpression(ImmutableList.of(new InvalidIdentExpression("@if"), new InvalidOperatorExpression(ImmutableList.of()))), finish());
    }

    @Test
    public void testJustIfByTypingWithoutAt() throws Exception
    {
        // Top completion in blank expression should be to insert a complete if statement.
        loadExpression("@unfinished \"\"");
        write("if");
        checkPosition();
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        // It's going to be invalid due to the empty bits:
        assertEquals(new InvalidOperatorExpression(ImmutableList.of(new InvalidIdentExpression("@if"), new InvalidOperatorExpression(ImmutableList.of()))), finish());
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
        assertEquals("@if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif + @if true @then 0 @else 1 @endif + column\\\\My Number", finishExternal());
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
            c("+", 1, 2),
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
            c("+", 2,3, 5,8),
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
            c("<", 1, 3),
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
            c("<", 2,3, 5,8),
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
            c("+", 1,3),
            c("*", 1,1, 3,3)
        );
    }

    @Test
    public void testRelatedFunction2() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("tex");
        checkPosition();
        scrollLexAutoCompleteToOption("from text to()");
        push(KeyCode.ENTER);
        write("1");
        IdentExpression fromTextTo = IdentExpression.function(TBasicUtil.checkNonNull(FunctionList.getFunctionLookup(mainWindowActions._test_getTableManager().getUnitManager()).lookup("from text to")).getFullName());
        assertEquals(new CallExpression(fromTextTo, ImmutableList.of(new NumericLiteral(1, null))), finish());
    }

    @Test
    public void testRelatedFunction2b() throws Exception
    {
        loadExpression("@unfinished \"\"");
        write("tex");
        checkPosition();
        push(KeyCode.LEFT);
        scrollLexAutoCompleteToOption("from text to()");
        push(KeyCode.ENTER);
        write("1");
        FunctionLookup functionLookup = FunctionList.getFunctionLookup(mainWindowActions._test_getTableManager().getUnitManager());
        IdentExpression fromTextTo = IdentExpression.function(TBasicUtil.checkNonNull(functionLookup.lookup("from text to")).getFullName());
        // Should still have trailing x:
        assertEquals(TFunctionUtil.parseExpression("@invalidops(@call function\\\\conversion\\from text to(1), x)", mainWindowActions._test_getTableManager().getTypeManager(), functionLookup), finish());
    }
}
