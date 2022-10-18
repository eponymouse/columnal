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

package xyz.columnal.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.TypeExp.TypeError;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.Random;

public class StringConcatExpression extends NaryOpTotalExpression
{
    public StringConcatExpression(List<@Recorded Expression> operands)
    {
        super(operands);
        
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new StringConcatExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return ";";
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(@Recorded StringConcatExpression this, ColumnLookup dataLookup, TypeState state, final ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Items in a String concat expression can be:
        // - Variable declaration
        // - Values
        // With the added restriction that two variables cannot be adjacent.
        // Although it's a bit hacky, we check for variables directly from here using instanceof
        
        boolean lastWasVariable = false;
        for (@Recorded Expression expression : expressions)
        {
            if (isPattern(expression))
            {
                if (lastWasVariable)
                {
                    onError.recordError(expression, Either.left(new TypeError(StyledString.s("Cannot have two variables/match-any next to each other in text pattern match; how can we know where one match stops and the next begins?"), ImmutableList.of())));
                    return null;
                }
                else
                {
                    lastWasVariable = true;
                }
            }
            else
            {
                
                lastWasVariable = false;
            }
            @Nullable CheckedExp c = expression.check(dataLookup, state, kind, LocationInfo.UNIT_DEFAULT, onError);
            
            if (c == null)
                return null;
            // TODO offer a quick fix of wrapping to.string around operand
            if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.text(this), c.typeExp)) == null)
                return null;
            state = c.typeState;
        }
        return onError.recordType(this, state, TypeExp.text(this));
    }

    @Override
    public ValueResult getValueNaryOp(ImmutableList<ValueResult> values, EvaluateState state) throws InternalException
    {
        StringBuilder sb = new StringBuilder();
        for (ValueResult value : values)
        {
            String s = Utility.cast(value.value, String.class);
            sb.append(s);
        }
        return result(DataTypeUtility.value(sb.toString()), state, values);
    }

    @Override
    public ValueResult matchAsPattern(@Value Object value, final @NonNull EvaluateState originalState) throws InternalException, EvaluationException
    {
        String s = Utility.cast(value, String.class);
        int curOffset = 0;

        ImmutableList.Builder<ValueResult> matches = ImmutableList.builderWithExpectedSize(expressions.size());
        @Nullable Expression pendingMatch = null;
        EvaluateState threadedState = originalState;
        for (int i = 0; i < expressions.size(); i++)
        {
            if (isPattern(expressions.get(i)))
            {
                pendingMatch = expressions.get(i);
            }
            else
            {
                // It's a value; get that value:
                ValueResult valueToFind = fetchSubExpression(expressions.get(i), threadedState, matches);
                String subValue = Utility.cast(valueToFind.value, String.class);
                if (subValue.isEmpty())
                {
                    // Matches, but nothing to do.  Keep going...
                }
                else if (pendingMatch == null)
                {
                    // Must match exactly:
                    if (s.regionMatches(curOffset, subValue, 0, subValue.length()))
                    {
                        // Fine, proceed!
                        curOffset += subValue.length();
                    }
                    else
                    {
                        // Can't match
                        return explanation(DataTypeUtility.value(false), ExecutionType.MATCH, originalState, matches.build(), ImmutableList.of(), false);
                    }
                    pendingMatch = null;
                }
                else
                {
                    // We find the next occurrence:
                    int nextPos = s.indexOf(subValue, curOffset);
                    if (nextPos == -1)
                        return explanation(DataTypeUtility.value(false), ExecutionType.MATCH, originalState, matches.build(), ImmutableList.of(), false);
                    ValueResult match = matchSubExpressionAsPattern(pendingMatch, DataTypeUtility.value(s.substring(curOffset, nextPos)), threadedState, matches);
                    if (Utility.cast(match.value, Boolean.class) == false)
                        return explanation(DataTypeUtility.value(false), ExecutionType.MATCH, originalState, matches.build(), ImmutableList.of(), false);
                    threadedState = match.evaluateState;
                    curOffset = nextPos + subValue.length();
                    pendingMatch = null;
                }
            }
        }
        if (pendingMatch != null)
        {
            ValueResult last = matchSubExpressionAsPattern(pendingMatch, DataTypeUtility.value(s.substring(curOffset)), threadedState, matches);
            if (Utility.cast(last.value, Boolean.class) == false)
                return explanation(DataTypeUtility.value(false), ExecutionType.MATCH, originalState, matches.build(), ImmutableList.of(), false);
            threadedState = last.evaluateState;
        }


        return explanation(DataTypeUtility.value(true), ExecutionType.MATCH, threadedState, matches.build(), ImmutableList.of(), false);
    }

    private boolean isPattern(Expression expression)
    {
        if (expression instanceof MatchAnythingExpression)
            return true;
        if (expression instanceof IdentExpression)
        {
            if (((IdentExpression)expression).isVarDeclaration())
                return true;
        }
        return false;
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.concatText(this, expressions);
    }
}
