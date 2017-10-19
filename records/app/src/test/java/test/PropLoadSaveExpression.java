package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Rule;
import org.junit.runner.RunWith;
import records.data.Table;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ErrorDisplayerRecord;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.Expression;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenNonsenseExpression;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by neil on 30/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveExpression
{
    @Rule
    public JavaFXThreadingRule javafxRule = new JavaFXThreadingRule();

    @Property(trials = 2000)
    public void testLoadSaveNonsense(@From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        testLoadSave(expression);
    }

    @Property(trials = 2000)
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void testEditNonsense(@When(seed=-533694247408751559L) @From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        Expression edited = new ExpressionEditor(expression, new ReadOnlyObjectWrapper<@Nullable Table>(null), new ReadOnlyObjectWrapper<@Nullable DataType>(null), DummyManager.INSTANCE, e -> {}).save(new ErrorDisplayerRecord(), e -> {});
        assertEquals(expression, edited);
        assertEquals(expression.save(true), edited.save(true));
    }

    @Property(trials = 1000)
    public void testLoadSaveReal(@From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue) throws InternalException, UserException
    {
        try
        {
            testLoadSave(expressionValue.expression);
        }
        catch (OutOfMemoryError e)
        {
            fail("Out of memory issue with expression: " + expressionValue.expression);
        }
    }

    private void testLoadSave(@From(GenNonsenseExpression.class) Expression expression) throws UserException, InternalException
    {
        String saved = expression.save(true);
        // Use same manage to load so that types are preserved:
        Expression reloaded = Expression.parse(null, saved, new TypeManager(new UnitManager()));
        assertEquals("Saved version: " + saved, expression, reloaded);
        String resaved = reloaded.save(true);
        assertEquals(saved, resaved);

    }
}
