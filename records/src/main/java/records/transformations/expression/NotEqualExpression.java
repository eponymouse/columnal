package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import utility.ExBiConsumer;
import utility.Utility;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class NotEqualExpression extends BinaryOpExpression
{
    public NotEqualExpression(Expression lhs, Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "!";
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        return DataType.checkSame(lhs.check(data, state, onError), rhs.check(data, state, onError), err -> onError.accept(this, err));
    }

    @Override
    public List<Object> getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        List<Object> lhsVal = lhs.getValue(rowIndex, state);
        List<Object> rhsVal = rhs.getValue(rowIndex, state);
        return Collections.singletonList(0 == Utility.compareLists(lhsVal, rhsVal));
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }
}
