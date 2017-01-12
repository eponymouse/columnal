package records.transformations.expression;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BitvectorFormulaManager;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

/**
 * Created by neil on 30/11/2016.
 */
public class EqualExpression extends BinaryOpExpression
{
    public EqualExpression(Expression lhs, Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "=";
    }

    @Override
    public BinaryOpExpression copy(@Nullable Expression replaceLHS, @Nullable Expression replaceRHS)
    {
        return new EqualExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    public @Nullable DataType checkBinaryOp(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        if (DataType.checkSame(lhsType, rhsType, err -> onError.accept(this, err)) == null)
            return null;
        return DataType.BOOLEAN;
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        Object lhsVal = lhs.getValue(rowIndex, state);
        Object rhsVal = rhs.getValue(rowIndex, state);
        return Utility.value(0 == Utility.compareValues(lhsVal, rhsVal));
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        Formula lhsForm = lhs.toSolver(formulaManager, src, columnVariables);
        Formula rhsForm = rhs.toSolver(formulaManager, src, columnVariables);
        if (lhsForm instanceof BitvectorFormula && rhsForm instanceof BitvectorFormula)
        {
            BitvectorFormulaManager m = formulaManager.getBitvectorFormulaManager();
            return m.equal((BitvectorFormula)lhsForm, (BitvectorFormula)rhsForm);
        }
        throw new UnimplementedException();
    }
}
