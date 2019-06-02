package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Utility;
import utility.Utility.TransparentBuilder;

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
    public @Nullable CheckedExp checkNaryOp(ColumnLookup dataLookup, TypeState state, final ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Items in a String concat expression can be:
        // - Variable declaration
        // - Values
        // With the added restriction that two variables cannot be adjacent.
        // Although it's a bit hacky, we check for variables directly from here using instanceof
        
        boolean lastWasVariable = false;
        for (Expression expression : expressions)
        {
            if (isPattern(expression))
            {
                if (lastWasVariable)
                {
                    onError.recordError(expression, Either.left(StyledString.s("Cannot have two variables/match-any next to each other in text pattern match; how can we know where one match stops and the next begins?")));
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
    public ValueResult getValueNaryOp(ImmutableList<ValueResult> values, EvaluateState state) throws UserException, InternalException
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
    public ValueResult matchAsPattern(@Value Object value, final @NonNull EvaluateState originalState) throws InternalException, UserException
    {
        String s = Utility.cast(value, String.class);
        int curOffset = 0;

        TransparentBuilder<ValueResult> matches = new TransparentBuilder<>(expressions.size());
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
                ValueResult valueToFind = matches.add(expressions.get(i).calculateValue(threadedState));
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
                    ValueResult match = matches.add(pendingMatch.matchAsPattern(DataTypeUtility.value(s.substring(curOffset, nextPos)), threadedState));
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
            ValueResult last = matches.add(pendingMatch.matchAsPattern(DataTypeUtility.value(s.substring(curOffset)), threadedState));
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
