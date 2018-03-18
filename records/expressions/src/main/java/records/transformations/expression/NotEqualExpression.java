package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BitvectorFormulaManager;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.types.NumTypeExp;
import records.types.TypeExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.Map;
import java.util.Optional;

/**
 * Created by neil on 30/11/2016.
 */
public class NotEqualExpression extends BinaryOpExpression
{
    public NotEqualExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "<>";
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    public @Nullable TypeExp checkBinaryOp(TableLookup data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (onError.recordError(this, TypeExp.unifyTypes(lhsType, rhsType)) == null)
        {
            if (lhsType instanceof NumTypeExp && rhsType instanceof NumTypeExp)
            {
                for (int i = 0; i < 2; i++)
                {
                    final @NonNull TypeProblemDetails typeProblemDetails = new TypeProblemDetails(ImmutableList.of(Optional.ofNullable(lhsType), Optional.ofNullable(rhsType)), ImmutableList.of(lhs, rhs), i);
                    // Must show an error to get the quick fixes to show:
                    onError.recordError(typeProblemDetails.getOurExpression(), StyledString.s("Operands to <> must have matching units"));
                    onError.recordQuickFixes(typeProblemDetails.getOurExpression(), ExpressionEditorUtil.getFixesForMatchingNumericUnits(state, typeProblemDetails));
                }
            }
            return null;
        }
        return TypeExp.fromConcrete(this, DataType.BOOLEAN);
    }

    @Override
    public @Value Object getValueBinaryOp(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        @Value Object lhsVal = lhs.getValue(rowIndex, state);
        @Value Object rhsVal = rhs.getValue(rowIndex, state);
        return DataTypeUtility.value(0 != Utility.compareValues(lhsVal, rhsVal));
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new NotEqualExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        Formula lhsForm = lhs.toSolver(formulaManager, src, columnVariables);
        Formula rhsForm = rhs.toSolver(formulaManager, src, columnVariables);
        if (lhsForm instanceof BitvectorFormula && rhsForm instanceof BitvectorFormula)
        {
            BitvectorFormulaManager m = formulaManager.getBitvectorFormulaManager();
            return formulaManager.getBooleanFormulaManager().not(m.equal((BitvectorFormula)lhsForm, (BitvectorFormula)rhsForm));
        }
        throw new UnimplementedException();
    }
}
