package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import test.gen.GenNonsenseExpression;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 30/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveExpression
{
    @Property
    public void testLoadSave(@From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        String saved = expression.save(true);
        Expression reloaded = Expression.parse(null, saved);
        assertEquals("Saved version: " + saved, expression, reloaded);
        String resaved = reloaded.save(true);
        assertEquals(saved, resaved);
        
    }
}
