package test.gui;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.View;
import records.transformations.Filter;
import records.transformations.TransformationInfo;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.Expression;
import records.transformations.expression.Literal;
import records.transformations.expression.TagExpression;
import records.transformations.expression.TupleExpression;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenNonsenseExpression;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditor extends ApplicationTest implements ListUtilTrait
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
    public void testEntry(@When(seed=-9954007651823638L) @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue, @When(seed=1L) @From(GenRandom.class) Random r) throws InterruptedException, ExecutionException, InternalException, IOException, UserException, InvocationTargetException
    {
        TestUtil.openDataAsTable(windowToUse, expressionValue.recordSet);
        clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
        // TODO switch from filter to calculate, so that we can actually check result
        selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getName().toLowerCase().matches("keep.?rows"));
        // Focus expression editor:
        push(KeyCode.TAB);
        enterExpression(expressionValue.expression, r);
        clickOn(".ok-button");
        // Now close dialog, and check for equality;
        View view = lookup(".view").query();
        Filter filter = (Filter)view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));
        assertEquals(expressionValue.expression, filter.getFilterExpression());
        // Just in case equals is wrong, check String comparison:
        assertEquals(expressionValue.expression.toString(), filter.getFilterExpression().toString());
    }

    private void enterExpression(Expression expression, Random r)
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
                enterExpression(members.get(i), r);

            }
            write(")");
        }
        else if (Literal.class.isAssignableFrom(c))
        {
            write(expression.toString());
        }
        else if (c == ColumnReference.class)
        {
            write(((ColumnReference)expression).allColumnNames().findFirst().get().getRaw());
            // Will be option of one or all, one should be top so press TAB:
            push(KeyCode.TAB);
        }
        else if (c == CallExpression.class)
        {
            CallExpression call = (CallExpression) expression;
            write(call._test_getFunctionName());
            write("(");
            enterExpression(call._test_getParam(), r);
        }
        else if (c == TagExpression.class)
        {
            TagExpression tag = (TagExpression)expression;
            // TODO need to pick from list based on tag:
            write(tag._test_getTagName().getSecond());
            push(KeyCode.TAB);
            if (tag.getInner() != null)
            {
                enterExpression(tag.getInner(), r);
            }
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
