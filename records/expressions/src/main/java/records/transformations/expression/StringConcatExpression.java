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
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class StringConcatExpression extends NaryOpExpression
{
    public StringConcatExpression(List<@Recorded Expression> operands)
    {
        super(operands);
        
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new StringConcatExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return ";";
    }

    @Override
    public @Nullable TypeExp checkNaryOp(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return onError.recordType(this, checkAllOperandsSameType(TypeExp.fromConcrete(this, DataType.TEXT), dataLookup, state, onError, (typeAndExpression) -> {
            // TODO offer a quick fix of wrapping to.string around operand
            return new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression>>>(typeAndExpression.getOurType() == null ? null : StyledString.concat(StyledString.s("Operands to ';' must be text but found "), typeAndExpression.getOurType().toStyledString()), ImmutableList.of());
        }));
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValueNaryOp(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        StringBuilder sb = new StringBuilder();
        for (Expression expression : expressions)
        {
            String s = Utility.cast(expression.getValue(rowIndex, state), String.class);
            sb.append(s);
        }
        return DataTypeUtility.value(sb.toString());
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
}
