package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.explanation.ExplanationLocation;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.MutVar;
import records.typeExp.TypeClassRequirements;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

/**
 * Created by neil on 30/11/2016.
 */
public class EqualExpression extends NaryOpShortCircuitExpression
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
    protected Op loadOp(int index)
    {
        return Op.EQUALS;
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(ColumnLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // The one to be returned, but not used for later operands:
        TypeState retTypeState = typeState;
        
        // The rule is that zero or one args can be a pattern, the rest must be expressions.
        // The type state must not carry over between operands, because otherwise you could write if ($s, $t) = (t, s) which doesn't pan out,
        // because all the non-patterns must be evaluated before the pattern.
        TypeExp type = new MutVar(this);
        List<Integer> invalidIndexes = new ArrayList<>();
        List<Optional<TypeExp>> expressionTypes = new ArrayList<>(expressions.size());
        for (int i = 0; i < expressions.size(); i++)
        {
            boolean invalid = false;
            Expression expression = expressions.get(i);
            @Nullable CheckedExp checked = expression.check(dataLookup, typeState, LocationInfo.UNIT_CONSTRAINED, onError);
            expressionTypes.add(Optional.ofNullable(checked).map(c -> c.typeExp));
            if (checked == null)
            {
                invalid = true;
            }
            else
            {
                if (checked.expressionKind == ExpressionKind.PATTERN)
                {
                    // Have we already seen a pattern?
                    if (patternIndex.isPresent() && patternIndex.getAsInt() != i)
                    {
                        onError.recordError(this, StyledString.s("Only one item in an equals expression can be a pattern."));
                        invalid = true;
                    }
                    else if (expressions.size() > 2)
                    {
                        // If there is a pattern and an expression, we don't allow two expressions, because it's not
                        // easily obvious to the user what the semantics should be e.g. 1 = @anything = 2, or particularly
                        // (1, (? + 1)) = (1, @anything) = (1, (? + 2)).
                        onError.recordError(this, StyledString.s("Cannot have a pattern in an equals expression with more than two operands."));
                        invalid = true;
                    }
                    else
                    {
                        patternIndex = OptionalInt.of(i);
                        retTypeState = checked.typeState;
                        checked.requireEquatable(false);
                    }
                }

                if (!invalid && onError.recordError(this, TypeExp.unifyTypes(type, checked.typeExp)) == null)
                {
                    invalid = true;
                }
            }
            
            if (invalid)
            {
                invalidIndexes.add(i);
            }
        }
        if (!invalidIndexes.isEmpty())
        {
            for (Integer index : invalidIndexes)
            {
                TypeProblemDetails tpd = new TypeProblemDetails(ImmutableList.copyOf(expressionTypes), ImmutableList.copyOf(expressions), index);
                onError.recordQuickFixes(expressions.get(index), ExpressionEditorUtil.getFixesForMatchingNumericUnits(typeState, tpd));
            }
            return null;
        }
        
        
        if (!patternIndex.isPresent())
        {
            type.requireTypeClasses(TypeClassRequirements.require("Equatable", "<equals>"));
        }
        
        return onError.recordType(this, ExpressionKind.EXPRESSION, retTypeState, TypeExp.bool(this));
    }

    @Override
    public ValueResult getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        if (patternIndex.isPresent())
        {
            if (expressions.size() > 2)
                throw new InternalException("Pattern present in equals despite having more than two operands");
            @Value Object value = expressions.get(1 - patternIndex.getAsInt()).getValue(state).getFirst();
            @Nullable EvaluateState result = expressions.get(patternIndex.getAsInt()).matchAsPattern(value, state);
            return new ValueResult(DataTypeUtility.value(result != null), result != null ? result : state, expressions);    
        }
        
        @Value Object first = expressions.get(0).getValue(state).getFirst();
        for (int i = 1; i < expressions.size(); i++)
        {
            @Value Object rhsVal = expressions.get(i).getValue(state).getFirst();
            if (0 != Utility.compareValues(first, rhsVal))
            {
                return new ValueResult(DataTypeUtility.value(false), ImmutableList.copyOf(expressions.subList(0, i + 1)));
            }
        }

        return new ValueResult(DataTypeUtility.value(true), expressions);
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }
}
