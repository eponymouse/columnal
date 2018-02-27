package test.expressions;

import org.junit.Test;
import records.data.ColumnId;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StringLiteral;
import records.transformations.function.FunctionList;
import test.DummyManager;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 30/11/2016.
 */
@SuppressWarnings("recorded")
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
            new NotEqualExpression(new NumericLiteral(new BigDecimal("140342692767213651142747826757169"), null),
                new NotEqualExpression(
                    new NotEqualExpression(new BooleanLiteral(false), new NumericLiteral(new BigDecimal("8070270985797032549311752415269"), null)),
                    new BooleanLiteral(false)
                )
            ),
            "140342692767213651142747826757169 <> ((false <> 8070270985797032549311752415269) <> false)"
        );

        assertBothWays(
            new AddSubtractExpression(Arrays.asList(
                new CallExpression(new UnitManager(), "abs",
                    new AddSubtractExpression(Arrays.asList(
                        new BooleanLiteral(true),
                        new BooleanLiteral(false),
                        new NumericLiteral(632, null),
                        new ColumnReference(new ColumnId("Date"), ColumnReferenceType.CORRESPONDING_ROW)),
                        Arrays.asList(Op.ADD, Op.SUBTRACT, Op.ADD))),
                new NumericLiteral(62, null),
                new StringLiteral("hi")
            ), Arrays.asList(Op.SUBTRACT, Op.ADD)),
            "abs(true + false - 632 + @column Date) - 62 + \"hi\""
        );
    }

    private static void assertBothWays(Expression expression, String src) throws InternalException, UserException
    {
        assertEquals(expression, Expression.parse(null, src, DummyManager.INSTANCE.getTypeManager()));
        assertEquals(src, expression.save(BracketedStatus.TOP_LEVEL, TableAndColumnRenames.EMPTY));
    }
}
