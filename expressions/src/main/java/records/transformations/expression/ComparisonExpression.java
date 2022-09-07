package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeClassRequirements;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.TypeExp.TypeError;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 17/01/2017.
 */
public class ComparisonExpression extends NaryOpShortCircuitExpression
{
    public static enum ComparisonOperator
    {
        LESS_THAN {
            @Override
            @OnThread(Tag.Simulation)
            public boolean comparisonTrue(@Value Object a, @Value Object b) throws InternalException, UserException
            {
                return Utility.compareValues(a, b) < 0;
            }
            public String saveOp() { return "<"; }
        },
        LESS_THAN_OR_EQUAL_TO {
            @Override
            @OnThread(Tag.Simulation)
            public boolean comparisonTrue(@Value Object a, @Value Object b) throws InternalException, UserException
            {
                return Utility.compareValues(a, b) <= 0;
            }
            public String saveOp() { return "<="; }
        },
        GREATER_THAN {
            @Override
            @OnThread(Tag.Simulation)
            public boolean comparisonTrue(@Value Object a, @Value Object b) throws InternalException, UserException
            {
                return Utility.compareValues(a, b) > 0;
            }

            public String saveOp() { return ">"; }
        },
        GREATER_THAN_OR_EQUAL_TO {
            @Override
            @OnThread(Tag.Simulation)
            public boolean comparisonTrue(@Value Object a, @Value Object b) throws InternalException, UserException
            {
                return Utility.compareValues(a, b) >= 0;
            }

            public String saveOp() { return ">="; }
        };

        @OnThread(Tag.Simulation)
        public abstract boolean comparisonTrue(@Value Object a, @Value Object b) throws InternalException, UserException;
        public abstract String saveOp();

        public static ComparisonOperator parse(String text) throws UserException
        {
            @Nullable ComparisonOperator op = Arrays.stream(values()).filter(candidate -> candidate.saveOp().equals(text)).findFirst().orElse(null);
            if (op == null)
                throw new UserException("Unparseable operator: \"" + text + "\"");
            return op;
        }
    }

    private final ImmutableList<ComparisonOperator> operators;
    private @Nullable TypeExp type;

    public ComparisonExpression(List<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators)
    {
        super(expressions);
        this.operators = operators;
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new ComparisonExpression(replacements, operators);
    }

    @Override
    protected String saveOp(int index)
    {
        return operators.get(index).saveOp();
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(@Recorded ComparisonExpression this, ColumnLookup dataLookup, TypeState state, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = checkAllOperandsSameTypeAndNotPatterns(new MutVar(this, TypeClassRequirements.require("Comparable", operators.get(0).saveOp())), dataLookup, state, LocationInfo.UNIT_CONSTRAINED, onError, p -> p.getOurType() instanceof NumTypeExp ? ImmutableMap.<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>>of(this, new Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>(new TypeError(StyledString.s("Mismatched units"), p.getAvailableTypesForError()), ImmutableList.copyOf(
                ExpressionUtil.getFixesForMatchingNumericUnits(state, p)
        ))) : ImmutableMap.<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>>of());
        if (type == null)
            return null;
        return onError.recordType(this, state, TypeExp.bool(this));
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueResult getValueNaryOp(EvaluateState state) throws EvaluationException, InternalException
    {
        ImmutableList.Builder<ValueResult> usedValues = ImmutableList.builderWithExpectedSize(expressions.size());
        @Value Object cur = fetchSubExpression(expressions.get(0), state, usedValues).value;
        for (int i = 1; i < expressions.size(); i++)
        {
            @Value Object next = fetchSubExpression(expressions.get(i),state, usedValues).value;
            try
            {
                if (!operators.get(i - 1).comparisonTrue(cur, next))
                {
                    return result(DataTypeUtility.value(false), state, usedValues.build());
                }
            }
            catch (UserException e)
            {
                throw new EvaluationException(e, this, ExecutionType.VALUE, state, usedValues.build());
            }
            cur = next;
        }
        return result(DataTypeUtility.value(true), state, usedValues.build());
    }

    @SuppressWarnings("recorded")
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(type)));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.comparison(this, expressions, operators);
    }
}
