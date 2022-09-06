package test.expressions;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import records.data.ColumnId;
import records.data.TableAndColumnRenames;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;

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
            IdentExpression.column(new ColumnId("Card")),
            "column\\\\Card"
        );
        assertBothWays(
            new BooleanLiteral(true),
            "true"
        );
        assertBothWays(
            new NumericLiteral(0, null),
            "0"
        );
        TypeManager typeManager = DummyManager.make().getTypeManager();
        assertEquals(
            IdentExpression.column(new ColumnId("Card")),
            TestUtil.parseExpression("column\\\\Card", typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()))
        );
    }
    @Test
    public void testParseCompound() throws InternalException, UserException
    {
        assertBothWays(
            new NotEqualExpression(new StringLiteral("Card"), new StringLiteral("xxx")),
            "\"Card\" <> \"xxx\""
        );
        TypeManager typeManager = DummyManager.make().getTypeManager();
        assertEquals(
            new NotEqualExpression(IdentExpression.column(new ColumnId("Card")), new StringLiteral("xxx")),
            TestUtil.parseExpression("column\\\\Card <> \"xxx\"", typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()))
        );
        assertBothWays(
            new NotEqualExpression(IdentExpression.column(new ColumnId("Card")), new StringLiteral("xxx")),
            "column\\\\Card <> \"xxx\""
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
                new CallExpression(FunctionList.getFunctionLookup(new UnitManager()), "abs",
                    new AddSubtractExpression(Arrays.asList(
                        new BooleanLiteral(true),
                        new BooleanLiteral(false),
                        new NumericLiteral(632, null),
                        IdentExpression.column(new ColumnId("Date"))),
                        Arrays.asList(AddSubtractOp.ADD, AddSubtractOp.SUBTRACT, AddSubtractOp.ADD))),
                new NumericLiteral(62, null),
                new StringLiteral("hi")
            ), Arrays.asList(AddSubtractOp.SUBTRACT, AddSubtractOp.ADD)),
            "@call function\\\\number\\abs(true + false - 632 + column\\\\Date) - 62 + \"hi\""
        );
    }
    
    @Test
    public void testCalls() throws UserException, InternalException
    {
        assertBothWays(new CallExpression(IdentExpression.tag("Optional", "Is"), ImmutableList.of(new NumericLiteral(3, null))), "@call tag\\\\Optional\\Is(3)");
    }

    private static void assertBothWays(Expression expression, String src) throws InternalException, UserException
    {
        TypeManager typeManager = DummyManager.make().getTypeManager();
        assertEquals(expression, TestUtil.parseExpression(src, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager())));
        assertEquals(src, expression.save(SaveDestination.TO_FILE, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY));
    }
}
