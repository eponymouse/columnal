package records.transformations.expression;

import java.util.List;
import java.util.Map;
import java.util.Random;

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
import records.gui.expressioneditor.ExpressionNodeParent;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.MutVar;
import records.types.NumTypeExp;
import records.types.TypeExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

/**
 * Created by neil on 30/11/2016.
 */
public class EqualExpression extends NaryOpExpression
{
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
    public @Nullable TypeExp checkNaryOp(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable TypeExp argType = checkAllOperandsSameType(new MutVar(this), dataLookup, typeState, onError, p -> new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression,ExpressionNodeParent>>>(null, p.getOurType() instanceof NumTypeExp ? ImmutableList.copyOf(
                ExpressionEditorUtil.getFixesForMatchingNumericUnits(typeState, p)
        ) : ImmutableList.of()));
        if (argType == null)
            return null;
        else
            return onError.recordType(this, TypeExp.fromConcrete(this, DataType.BOOLEAN));
    }

    @Override
    public @Value Object getValueNaryOp(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        @Value Object first = expressions.get(0).getValue(rowIndex, state);
        for (int i = 1; i < expressions.size(); i++)
        {
            @Value Object rhsVal = expressions.get(i).getValue(rowIndex, state);
            if (0 != Utility.compareValues(first, rhsVal))
                return DataTypeUtility.value(false);
        }

        return DataTypeUtility.value(true);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        /*
        Formula lhsForm = lhs.toSolver(formulaManager, src, columnVariables);
        Formula rhsForm = rhs.toSolver(formulaManager, src, columnVariables);
        if (lhsForm instanceof BitvectorFormula && rhsForm instanceof BitvectorFormula)
        {
            BitvectorFormulaManager m = formulaManager.getBitvectorFormulaManager();
            return m.equal((BitvectorFormula)lhsForm, (BitvectorFormula)rhsForm);
        }
        */
        throw new UnimplementedException();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }
}
