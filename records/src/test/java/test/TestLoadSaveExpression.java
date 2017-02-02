package test;

import org.junit.Test;
import records.data.ColumnId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StringLiteral;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 30/11/2016.
 */
public class TestLoadSaveExpression
{
    @Test
    public void testParseSimple() throws InternalException, UserException
    {
        //TODO add tests with units
        assertBothWays(
            new ColumnReference(new ColumnId("Card"), ColumnReferenceType.CORRESPONDING_ROW),
            "@column Card"
        );
        assertBothWays(
            new BooleanLiteral(true),
            "true"
        );
        assertBothWays(
            new NumericLiteral(0, null),
            "0"
        );
        assertEquals(
            new ColumnReference(new ColumnId("Card"), ColumnReferenceType.CORRESPONDING_ROW),
            Expression.parse(null, "@column \"Card\"", DummyManager.INSTANCE.getTypeManager())
        );
    }
    @Test
    public void testParseCompound() throws InternalException, UserException
    {
        assertBothWays(
            new NotEqualExpression(new StringLiteral("Card"), new StringLiteral("xxx")),
            "\"Card\" <> \"xxx\""
        );
        assertEquals(
            new NotEqualExpression(new ColumnReference(new ColumnId("Card"), ColumnReferenceType.CORRESPONDING_ROW), new StringLiteral("xxx")),
            Expression.parse(null, "@column \"Card\" <> \"xxx\"", DummyManager.INSTANCE.getTypeManager())
        );
        assertBothWays(
            new NotEqualExpression(new ColumnReference(new ColumnId("Card"), ColumnReferenceType.CORRESPONDING_ROW), new StringLiteral("xxx")),
            "@column Card <> \"xxx\""
        );
        assertBothWays(
            new NotEqualExpression(new BooleanLiteral(false), new BooleanLiteral(true)),
            "false <> true"
        );
        assertBothWays(
            new NotEqualExpression(new NumericLiteral(23, null), new BooleanLiteral(true)),
            "23 <> true"
        );
        assertBothWays(
            new NotEqualExpression(new NumericLiteral(0, null), new BooleanLiteral(true)),
            "0 <> true"
        );
        assertBothWays(
            new NotEqualExpression(new BooleanLiteral(false),
                new NotEqualExpression(new NumericLiteral(0, null), new BooleanLiteral(true))),
            "false <> (0 <> true)"
        );

        assertBothWays(
            new NotEqualExpression(new NumericLiteral(new BigDecimal("1403426927672136511427478267571691349056738827396"), null),
                new NotEqualExpression(
                    new NotEqualExpression(new BooleanLiteral(false), new NumericLiteral(new BigDecimal("807027098579703254931175241526980987544176732399"), null)),
                    new BooleanLiteral(false)
                )
            ),
            "1403426927672136511427478267571691349056738827396 <> ((false <> 807027098579703254931175241526980987544176732399) <> false)"
        );
    }

    private static void assertBothWays(Expression expression, String src) throws InternalException, UserException
    {
        assertEquals(expression, Expression.parse(null, src, DummyManager.INSTANCE.getTypeManager()));
        assertEquals(src, expression.save(true));
    }
}
