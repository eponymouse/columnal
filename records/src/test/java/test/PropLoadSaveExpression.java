package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import test.gen.GenExpression;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 30/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveExpression
{
    @Property
    public void testLoadSave(@From(GenExpression.class) Expression expression) throws InternalException, UserException
    {
        Expression reloaded = Expression.parse(null, expression.save(true));
        assertEquals(expression, reloaded);
    }
}
