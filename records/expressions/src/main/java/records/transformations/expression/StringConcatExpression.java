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
import records.gui.expressioneditor.ExpressionNodeParent;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Random;

public class StringConcatExpression extends NaryOpExpression
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
    public @Nullable Pair<@Recorded TypeExp, TypeState> checkAsPattern(boolean varDeclAllowed, TableLookup data, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Items in a String concat expression can be:
        // - Variable declaration
        // - Values
        // With the added restriction that two variables cannot be adjacent.
        // Although it's a bit hacky, we check for variables directly from here using instanceof
        if (expressions.size() == 1) // Shouldn't happen; how are we a concat expression?
            return expressions.get(0).checkAsPattern(varDeclAllowed, data, typeState, onError);
        
        boolean lastWasVariable = false;
        TypeExp ourType = TypeExp.text(this);
        for (int i = 0; i < expressions.size(); i++)
        {
            @Nullable Pair<@Recorded TypeExp, TypeState> p;
            if (expressions.get(i) instanceof VarDeclExpression)
            {
                if (lastWasVariable)
                {
                    onError.recordError(expressions.get(i), Either.left(StyledString.s("Cannot have two variables next to each other in text pattern match; how can we know where one match stops and the next begins?")));
                    return null;
                }
                else
                {
                    p = expressions.get(i).checkAsPattern(true, data, typeState, onError);
                    lastWasVariable = true;
                }
            }
            else
            {
                p = expressions.get(i).checkAsPattern(false, data, typeState, onError);
                lastWasVariable = false;
            }
            if (p == null)
                return null;
            
            onError.recordError(this, TypeExp.unifyTypes(ourType, p.getFirst()));
            // This lets later items use matches from earlier, but I think that's fine:
            typeState = p.getSecond();
        }

        return new Pair<>(onError.recordTypeNN(this, ourType), typeState);
    }

    @Override
    public @Nullable TypeExp checkNaryOp(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return onError.recordType(this, checkAllOperandsSameType(TypeExp.text(this), dataLookup, state, onError, (typeAndExpression) -> {
            // TODO offer a quick fix of wrapping to.string around operand
            @Nullable TypeExp curType = typeAndExpression.getOurType();
            @Nullable StyledString message;
            if (curType == null || curType.toString().equals("Text"))
                message = null;
            else
                message = StyledString.concat(StyledString.s("Operands to ';' must be text but found "), curType.toStyledString());
            return new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression,ExpressionNodeParent>>>(message, ImmutableList.of());
        }));
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValueNaryOp(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        StringBuilder sb = new StringBuilder();
        for (Expression expression : expressions)
        {
            String s = Utility.cast(expression.getValue(rowIndex, state), String.class);
            sb.append(s);
        }
        return DataTypeUtility.value(sb.toString());
    }

    @Override
    public @OnThread(Tag.Simulation) @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, @NonNull EvaluateState state) throws InternalException, UserException
    {
        String s = Utility.cast(value, String.class);
        int curOffset = 0;

        @Nullable VarDeclExpression pendingMatch = null;
        for (int i = 0; i < expressions.size(); i++)
        {
            if (expressions.get(i) instanceof VarDeclExpression)
            {
                pendingMatch = (VarDeclExpression)expressions.get(i);
            }
            else
            {
                // It's a value; get that value:
                String subValue = Utility.cast(expressions.get(i).getValue(rowIndex, state), String.class);
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
                    EvaluateState newState = pendingMatch.matchAsPattern(rowIndex, s.substring(curOffset, nextPos), state);
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
            return pendingMatch.matchAsPattern(rowIndex, s.substring(curOffset), state);
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
