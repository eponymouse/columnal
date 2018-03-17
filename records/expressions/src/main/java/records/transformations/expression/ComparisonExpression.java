package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.MutVar;
import records.types.NumTypeExp;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
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
    public @Nullable TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = checkAllOperandsSameType(new MutVar(this), dataLookup, state, onError, p -> new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression>>>(null, p.getOurType() instanceof NumTypeExp ? ImmutableList.copyOf(
            ExpressionEditorUtil.getFixesForMatchingNumericUnits(state, p)
        ) : ImmutableList.of()));
        if (type == null)
            return null;
        return onError.recordType(this, TypeExp.fromConcrete(this, DataType.BOOLEAN));
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        @Value Object cur = expressions.get(0).getValue(rowIndex, state);
        for (int i = 1; i < expressions.size(); i++)
        {
            @Value Object next = expressions.get(i).getValue(rowIndex, state);
            if (!operators.get(i - 1).comparisonTrue(cur, next))
                return DataTypeUtility.value(false);
            cur = next;
        }
        return DataTypeUtility.value(true);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @SuppressWarnings("recorded")
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(type)));
    }
}
