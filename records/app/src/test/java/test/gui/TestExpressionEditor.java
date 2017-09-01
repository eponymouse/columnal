package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.ColumnId;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.gui.MainWindow;
import records.gui.View;
import records.importers.ClipboardUtils;
import records.transformations.Filter;
import records.transformations.Transform;
import records.transformations.TransformationInfo;
import records.transformations.expression.*;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenNonsenseExpression;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditor extends ApplicationTest implements ListUtilTrait, ScrollToTrait
{
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }

    @Property(trials = 10)
    public void testEntry(@When(seed=3L) @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue, @When(seed=1L) @From(GenRandom.class) Random r) throws InterruptedException, ExecutionException, InternalException, IOException, UserException, InvocationTargetException
    {
        TestUtil.openDataAsTable(windowToUse, expressionValue.typeManager, expressionValue.recordSet);
        try
        {
            scrollTo(".id-tableDisplay-menu-button");
            clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
            selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getName().toLowerCase().matches("calculate.?columns"));
            push(KeyCode.TAB);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            enterExpression(expressionValue.expression, false, r);
            // Finish any final column completion:
            push(KeyCode.TAB);
            // Hide any code completion (also: check it doesn't dismiss dialog)
            push(KeyCode.ESCAPE);
            push(KeyCode.ESCAPE);
            clickOn(".ok-button");
            // Now close dialog, and check for equality;
            View view = lookup(".view").query();
            if (view == null)
            {
                assertNotNull(view);
                return;
            }
            TestUtil.sleep(200);
            Transform transform = (Transform) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));

            // Check expressions match:
            Expression expression = transform.getCalculatedColumns().get(0).getSecond();
            assertEquals(expressionValue.expression, expression);
            // Just in case equals is wrong, check String comparison:
            assertEquals(expressionValue.expression.toString(), expression.toString());

            // Now check values match:
            scrollTo(".tableDisplay-transformation .id-tableDisplay-menu-button");
            clickOn(".tableDisplay-transformation .id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-copyValues");
            TestUtil.sleep(1000);
            Optional<List<Pair<ColumnId, List<@Value Object>>>> clip = TestUtil.fx(() -> ClipboardUtils.loadValuesFromClipboard(expressionValue.typeManager));
            assertTrue(clip.isPresent());
            // Need to fish out first column from clip, then compare item:
            //TestUtil.checkType(expressionValue.type, clip.get().get(0));
            List<@Value Object> actual = clip.get().stream().filter(p -> p.getFirst().equals(new ColumnId("DestCol"))).findFirst().orElseThrow(RuntimeException::new).getSecond();
            TestUtil.assertValueListEqual("Transformed", expressionValue.value, actual);
        }
        finally
        {
            Stage s = windowToUse;
            Platform.runLater(() -> s.hide());
        }
    }

    private void enterExpression(Expression expression, boolean needsBrackets, Random r)
    {
        Class<?> c = expression.getClass();
        if (c == TupleExpression.class)
        {
            TupleExpression t = (TupleExpression)expression;
            write("(");
            ImmutableList<Expression> members = t._test_getMembers();
            for (int i = 0; i < members.size(); i++)
            {
                if (i > 0)
                    write(",");
                enterExpression(members.get(i), true, r);

            }
            write(")");
        }
        else if (c == ArrayExpression.class)
        {
            ArrayExpression t = (ArrayExpression)expression;
            write("[");
            ImmutableList<Expression> members = t._test_getElements();
            for (int i = 0; i < members.size(); i++)
            {
                if (i > 0)
                    write(",");
                enterExpression(members.get(i), true, r);

            }
            write("]");
        }
        else if (c == StringLiteral.class)
        {
            write('"');
            int extraChars = 0;
            for (char singleChar : ((StringLiteral) expression).getRawString().toCharArray())
            {
                if (singleChar != '"')
                {
                    write(singleChar);
                }
                else
                {
                    write('a');
                    push(KeyCode.LEFT);
                    write('"');
                    extraChars += 1;
                }
            }
            for (int i = 0; i < extraChars; i++)
            {
                push(KeyCode.DELETE);
            }
            write('"');
        }
        else if (Literal.class.isAssignableFrom(c))
        {
            write(expression.toString());
        }
        else if (c == ColumnReference.class)
        {
            write(((ColumnReference)expression).allColumnNames().findFirst().get().getRaw());
        }
        else if (c == CallExpression.class)
        {
            CallExpression call = (CallExpression) expression;
            write(call._test_getFunctionName());
            //TODO bracket should work same as tab here, but doesn't yet:
            push(KeyCode.TAB);
            //write("(");
            enterExpression(call._test_getParam(), false, r);
            write(")");
        }
        else if (c == TagExpression.class)
        {
            TagExpression tag = (TagExpression)expression;
            write(tag._test_getTagName().getFirst() + ":" + tag._test_getTagName().getSecond());
            push(KeyCode.TAB);
            if (tag.getInner() != null)
            {
                enterExpression(tag.getInner(), false, r);
            }
        }
        else if (c == MatchExpression.class)
        {
            MatchExpression match = (MatchExpression)expression;
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.MATCH));
            enterExpression(match.getExpression(), false, r);
            for (MatchClause matchClause : match.getClauses())
            {
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASE));
                for (int i = 0; i < matchClause.getPatterns().size(); i++)
                {
                    if (i > 0)
                        write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ORCASE));
                    Pattern pattern = matchClause.getPatterns().get(i);
                    enterExpression(pattern.getPattern(), false, r);
                    @Nullable Expression guard = pattern.getGuard();
                    if (guard != null)
                    {
                        write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASEGUARD));
                        enterExpression(guard, false, r);
                    }
                }
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN));
                enterExpression(matchClause.getOutcome(), false, r);
            }
            // To finish whole match expression:
            write(")");
        }
        else if (c == IfThenElseExpression.class)
        {
            IfThenElseExpression ite = (IfThenElseExpression) expression;
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.IF));
            enterExpression(ite._test_getCondition(), false, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN));
            enterExpression(ite._test_getThen(), false, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ELSE));
            enterExpression(ite._test_getElse(), false, r);
            write(")");
        }
        else if (NaryOpExpression.class.isAssignableFrom(c))
        {
            NaryOpExpression n = (NaryOpExpression)expression;
            if (needsBrackets)
                write("(");
            for (int i = 0; i < n.getChildren().size(); i++)
            {
                enterExpression(n.getChildren().get(i), true, r);
                if (i < n.getChildren().size() - 1)
                {
                    write(n._test_getOperatorEntry(i));
                }
            }
            if (needsBrackets)
                write(")");
        }
        else if (BinaryOpExpression.class.isAssignableFrom(c))
        {
            BinaryOpExpression b = (BinaryOpExpression)expression;
            if (needsBrackets)
                write("(");
            enterExpression(b.getLHS(), true, r);
            write(b._test_getOperatorEntry());
            enterExpression(b.getRHS(), true, r);
            if (needsBrackets)
                write(")");
        }
        else if (c == VarDeclExpression.class)
        {
            write(((VarDeclExpression)expression).getName());
            // Have to manually move on because it won't auto-complete:
            push(KeyCode.TAB); // TODO make sure we've scrolled to new-var in cases of overlap
        }
        else if (c == VarUseExpression.class)
        {
            write(((VarUseExpression)expression).getName());
            push(KeyCode.TAB); // TODO make sure we've scrolled to new-var in cases of overlap
        }
        else
        {
            fail("Not yet supported: " + c);
        }
        // TODO add randomness to entry methods (e.g. selection from auto-complete
        // TODO check position of auto-complete
    }

    // TODO test that nonsense is preserved after load (which will change it all to invalid) -> save -> load (which should load invalid version)
}
