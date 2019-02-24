package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

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
    protected Op loadOp(int index)
    {
        return Op.STRING_CONCAT;
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(ColumnLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Items in a String concat expression can be:
        // - Variable declaration
        // - Values
        // With the added restriction that two variables cannot be adjacent.
        // Although it's a bit hacky, we check for variables directly from here using instanceof
        
        ExpressionKind kind = ExpressionKind.EXPRESSION;
        boolean lastWasVariable = false;
        for (Expression expression : expressions)
        {
            if (expression instanceof VarDeclExpression || expression instanceof MatchAnythingExpression)
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
            @Nullable CheckedExp c = expression.check(dataLookup, state, LocationInfo.UNIT_DEFAULT, onError);
            
            if (c == null)
                return null;
            // TODO offer a quick fix of wrapping to.string around operand
            if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.text(this), c.typeExp)) == null)
                return null;
            state = c.typeState;
            kind = kind.or(c.expressionKind);
        }
        return onError.recordType(this, kind, state, TypeExp.text(this));
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        StringBuilder sb = new StringBuilder();
        for (Expression expression : expressions)
        {
            Pair<@Value Object, EvaluateState> valueAndState = expression.getValue(state);
            String s = Utility.cast(valueAndState.getFirst(), String.class);
            sb.append(s);
            state = valueAndState.getSecond();
        }
        return new Pair<>(DataTypeUtility.value(sb.toString()), state);
    }

    @Override
    public @Nullable EvaluateState matchAsPattern(@Value Object value, @NonNull EvaluateState state) throws InternalException, UserException
    {
        String s = Utility.cast(value, String.class);
        int curOffset = 0;

        @Nullable Expression pendingMatch = null;
        for (int i = 0; i < expressions.size(); i++)
        {
            if (expressions.get(i) instanceof VarDeclExpression || expressions.get(i) instanceof MatchAnythingExpression)
            {
                pendingMatch = expressions.get(i);
            }
            else
            {
                // It's a value; get that value:
                String subValue = Utility.cast(expressions.get(i).getValue(state).getFirst(), String.class);
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
                        return null;
                    }
                    pendingMatch = null;
                }
                else
                {
                    // We find the next occurrence:
                    int nextPos = s.indexOf(subValue, curOffset);
                    if (nextPos == -1)
                        return null;
                    EvaluateState newState = pendingMatch.matchAsPattern(DataTypeUtility.value(s.substring(curOffset, nextPos)), state);
                    if (newState == null)
                        return null;
                    state = newState;
                    curOffset = nextPos + subValue.length();
                    pendingMatch = null;
                }
            }
        }
        if (pendingMatch != null)
        {
            return pendingMatch.matchAsPattern(DataTypeUtility.value(s.substring(curOffset)), state);
        }
        else
            return state;
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }
}
