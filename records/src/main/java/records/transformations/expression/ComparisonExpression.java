package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.transformations.expression.AddSubtractExpression.Op;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    private @Nullable DataType type;

    public ComparisonExpression(List<Expression> expressions, ImmutableList<ComparisonOperator> operators)
    {
        super(expressions);
        this.operators = operators;
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new ComparisonExpression(replacements, operators);
    }

    @Override
    protected String saveOp(int index)
    {
        return operators.get(index).saveOp();
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        type = checkAllOperandsSameType(data, state, onError);
        return type;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        @Value Object cur = expressions.get(0).getValue(rowIndex, state);
        for (int i = 1; i < expressions.size(); i++)
        {
            if (!operators.get(i - 1).comparisonTrue(cur, expressions.get(i).getValue(rowIndex, state)))
                return Utility.value(false);
        }
        return Utility.value(true);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(type)));
    }
}
