package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.MutVar;
import records.typeExp.TypeClassRequirements;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Utility;
import utility.Utility.TransparentBuilder;

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
    // Is the last item in the operators a pattern?  If so, last operator is =~ rather than =
    private final boolean lastIsPattern;
    
    public EqualExpression(List<@Recorded Expression> operands, boolean lastIsPattern)
    {
        super(operands);
        this.lastIsPattern = lastIsPattern;
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new EqualExpression(replacements, lastIsPattern);
    }

    @Override
    protected String saveOp(int index)
    {
        if (lastIsPattern && index == expressions.size() - 2)
            return "=~";
        else
            return "=";
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(@Recorded EqualExpression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind expressionKind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (lastIsPattern && expressions.size() > 2)
        {
            // If there is a pattern and an expression, we don't allow two expressions, because it's not
            // easily obvious to the user what the semantics should be e.g. 1 = @anything = 2, or particularly
            // (1, (? + 1)) = (1, @anything) = (1, (? + 2)).
            onError.recordError(this, StyledString.s("Cannot have a pattern in an equals expression with more than two operands."));
            // (This shouldn't really be reachable if all other systems are working right, but good as a sanity check)
            return null;
        }
        
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
            @Recorded Expression expression = expressions.get(i);
            @Nullable CheckedExp checked = expression.check(dataLookup, typeState, (lastIsPattern && i == expressions.size() - 1) ? ExpressionKind.PATTERN : ExpressionKind.EXPRESSION, LocationInfo.UNIT_CONSTRAINED, onError);
            expressionTypes.add(Optional.ofNullable(checked).map(c -> c.typeExp));
            if (checked == null)
            {
                invalid = true;
            }
            else
            {
                checked.requireEquatable();
                if (!invalid && onError.recordError(this, TypeExp.unifyTypes(type, checked.typeExp)) == null)
                {
                    invalid = true;
                }
                
                if (lastIsPattern && i == expressions.size() - 1)
                    retTypeState = checked.typeState;
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
                onError.recordQuickFixes(expressions.get(index), ExpressionUtil.getFixesForMatchingNumericUnits(typeState, tpd));
            }
            return null;
        }
        
        return onError.recordType(this, retTypeState, TypeExp.bool(this));
    }

    @Override
    public ValueResult getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        if (lastIsPattern)
        {
            if (expressions.size() > 2)
                throw new InternalException("Pattern present in equals despite having more than two operands");
            ValueResult value = expressions.get(0).calculateValue(state);
            ValueResult matchResult = expressions.get(1).matchAsPattern(value.value, state);
            boolean matched = Utility.cast(matchResult.value, Boolean.class);
            return result(DataTypeUtility.value(matched), matched ? matchResult.evaluateState : state, ImmutableList.of(value, matchResult));    
        }

        TransparentBuilder<ValueResult> values = new TransparentBuilder<>(expressions.size());
        @Value Object first = values.add(expressions.get(0).calculateValue(state)).value;
        for (int i = 1; i < expressions.size(); i++)
        {
            @Value Object rhsVal = values.add(expressions.get(i).calculateValue(state)).value;
            if (0 != Utility.compareValues(first, rhsVal))
            {
                return result(DataTypeUtility.value(false), state, values.build());
            }
        }

        return result(DataTypeUtility.value(true), state, values.build());
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.equal(this, expressions, lastIsPattern);
    }
    
    public ImmutableList<@Recorded Expression> getOperands()
    {
        return expressions;
    }
    
    public boolean lastIsPattern()
    {
        return lastIsPattern;
    }
}
