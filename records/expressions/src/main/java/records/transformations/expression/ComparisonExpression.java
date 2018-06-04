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
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.MutVar;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeClassRequirements;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 17/01/2017.
 */
public class ComparisonExpression extends NaryOpExpression
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
    protected Op loadOp(int index)
    {
        switch (operators.get(index))
        {
            case LESS_THAN:
                return Op.LESS_THAN;
            case LESS_THAN_OR_EQUAL_TO:
                return Op.LESS_THAN_OR_EQUAL;
            case GREATER_THAN:
                return Op.GREATER_THAN;
            case GREATER_THAN_OR_EQUAL_TO:
            default: // To avoid compiler error
                return Op.GREATER_THAN_OR_EQUAL;
        }
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = checkAllOperandsSameTypeAndNotPatterns(new MutVar(this, TypeClassRequirements.require("Comparable", operators.get(0).saveOp())), dataLookup, state, onError, p -> new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression,ExpressionSaver>>>(null, p.getOurType() instanceof NumTypeExp ? ImmutableList.copyOf(
            ExpressionEditorUtil.getFixesForMatchingNumericUnits(state, p)
        ) : ImmutableList.of()));
        if (type == null)
            return null;
        return onError.recordType(this, ExpressionKind.EXPRESSION, state, TypeExp.bool(this));
    }

    @Override
    @OnThread(Tag.Simulation)
    public Pair<@Value Object, EvaluateState> getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        @Value Object cur = expressions.get(0).getValue(state).getFirst();
        for (int i = 1; i < expressions.size(); i++)
        {
            @Value Object next = expressions.get(i).getValue(state).getFirst();
            if (!operators.get(i - 1).comparisonTrue(cur, next))
                return new Pair<>(DataTypeUtility.value(false), state);
            cur = next;
        }
        return new Pair<>(DataTypeUtility.value(true), state);
    }

    @SuppressWarnings("recorded")
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(type)));
    }
}
