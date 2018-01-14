package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
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
import records.types.TypeCons;
import records.types.TypeExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class OrExpression extends NaryOpExpression
{
    public OrExpression(List<Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new OrExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "|";
    }

    @Override
    public @Nullable @Recorded TypeExp check(RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return checkAllOperandsSameType(TypeExp.fromConcrete(this, DataType.BOOLEAN), data, state, onError, (typeAndExpression) -> {
            return new Pair<@Nullable StyledString, @Nullable QuickFix<Expression>>(StyledString.concat(StyledString.s("Operands to '|' must be boolean but found "), typeAndExpression.getFirst().toStyledString()), null);
        });
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        for (Expression expression : expressions)
        {
            Boolean b = Utility.cast(expression.getValue(rowIndex, state), Boolean.class);
            if (b == true)
                return DataTypeUtility.value(true);
        }
        return DataTypeUtility.value(false);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(new TypeCons(null, TypeExp.CONS_BOOLEAN))));
    }
}
