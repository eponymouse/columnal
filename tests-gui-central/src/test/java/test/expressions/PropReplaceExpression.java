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

package test.expressions;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.MatchAnythingExpression;
import xyz.columnal.transformations.expression.NumericLiteral;
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
