package test.expressions;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchAnythingExpression;
import records.transformations.expression.NumericLiteral;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.nonsenseTrans.GenNonsenseExpression;
import xyz.columnal.utility.Pair;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class PropReplaceExpression
{
    @Property
    public void testReplaceNothing(@From(GenNonsenseExpression.class) Expression expression)
    {
        // Test that replacing an expression not present does nothing.
        assertEquals(expression, expression.replaceSubExpression(new MatchAnythingExpression(), new NumericLiteral(0, null)));
    }
    
    @Property
    public void testReplaceNothingV(@From(GenExpressionValueBackwards.class) @From(GenExpressionValueForwards.class) ExpressionValue expressionValue)
    {
        testReplaceNothing(expressionValue.expression);
    }
    
    // We probably shouldn't have two methods doing similar things, but since we do, we can test them against each other:
    @Property
    public void testReplaceRandom(@From(GenNonsenseExpression.class) Expression expression)
    {
        List<Pair<Expression, Function<Expression, Expression>>> mutationPoints = expression._test_allMutationPoints().collect(Collectors.toList());
        for (Pair<Expression, Function<Expression, Expression>> mutationPoint : mutationPoints)
        {
            assertEquals("Replacing " + mutationPoint.getFirst(), expression.replaceSubExpression(mutationPoint.getFirst(), new MatchAnythingExpression()), mutationPoint.getSecond().apply(new MatchAnythingExpression()));
        }
    }

    @Property
    public void testReplaceRandomV(@From(GenExpressionValueBackwards.class) @From(GenExpressionValueForwards.class) ExpressionValue expressionValue)
    {
        testReplaceRandom(expressionValue.expression);
    }
}
