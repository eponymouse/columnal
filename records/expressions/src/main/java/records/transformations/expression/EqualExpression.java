package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.typeExp.MutVar;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeClassRequirements;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

/**
 * Created by neil on 30/11/2016.
 */
public class EqualExpression extends NaryOpExpression
{
    private OptionalInt patternIndex = OptionalInt.empty();
    
    public EqualExpression(List<@Recorded Expression> operands)
    {
        super(operands);
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new EqualExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "=";
    }


    @Override
    public @Nullable CheckedExp checkNaryOp(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // The rule is that zero or one args can be a pattern, the rest must be expressions.
        // The type state must not carry over between operands, because otherwise you could write if ($s, $t) = (t, s) which doesn't pan out,
        // because all the non-patterns must be evaluated before the pattern.
        TypeExp type = new MutVar(this);
        for (int i = 0; i < expressions.size(); i++)
        {
            Expression expression = expressions.get(i);
            @Nullable CheckedExp checked = expression.check(dataLookup, typeState, onError);
            if (checked == null)
                return null;
            if (checked.expressionKind == ExpressionKind.PATTERN)
            {
                // Have we already seen a pattern?
                if (patternIndex.isPresent())
                {
                    onError.recordError(this, StyledString.s("Only one item in an equals expression can be a pattern."));
                    return null;
                }
                else
                {
                    patternIndex = OptionalInt.of(i);
                    checked.requireEquatable(false);
                }
            }
            // If there is a pattern and an expression, we don't allow two expressions, because it's not
            // easily obvious to the user what the semantics should be e.g. 1 = @anything = 2, or particularly
            // (1, (? + 1)) = (1, @anything) = (1, (? + 2)).
            if (patternIndex.isPresent() && i >= 2)
            {
                onError.recordError(this, StyledString.s("If one part of an equals expression is a pattern, you may only compare it to one expression."));
                return null;
            }
            
            if (onError.recordError(this, TypeExp.unifyTypes(type, checked.typeExp)) == null)
                return null;
        }
        if (!patternIndex.isPresent())
        {
            type.requireTypeClasses(TypeClassRequirements.require("Equatable", "<equals>"));
        }
        
        return onError.recordType(this, ExpressionKind.EXPRESSION, typeState, TypeExp.bool(this));
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        @Value Object first = expressions.get(0).getValue(state);
        for (int i = 1; i < expressions.size(); i++)
        {
            @Value Object rhsVal = expressions.get(i).getValue(state);
            if (0 != Utility.compareValues(first, rhsVal))
                return DataTypeUtility.value(false);
        }

        return DataTypeUtility.value(true);
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }
}
