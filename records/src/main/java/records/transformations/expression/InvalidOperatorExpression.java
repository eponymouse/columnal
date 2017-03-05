package records.transformations.expression;

import annotation.qual.Value;
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
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An expression with mixed operators, which make it invalid.  Can't be run, but may be
 * used while editing and for loading/saving invalid expressions.
 */
public class InvalidOperatorExpression extends NaryOpExpression
{
    private final List<String> operators;

    public InvalidOperatorExpression(List<Expression> operands, List<String> operators)
    {
        super(operands);
        this.operators = operators;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        // TODO give error and quick fix (bracketing)
        return null; // Invalid expressions can't type check
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Cannot get value for invalid expression");
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new InvalidOperatorExpression(replacements, operators);
    }

    @Override
    protected String getSpecialPrefix()
    {
        return "@invalidops ";
    }

    @Override
    protected String saveOp(int index)
    {
        return operators.get(index);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        InvalidOperatorExpression that = (InvalidOperatorExpression) o;

        return operators.equals(that.operators);
    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + operators.hashCode();
        return result;
    }
}
