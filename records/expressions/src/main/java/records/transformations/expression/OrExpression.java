package records.transformations.expression;

import annotation.qual.Value;
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
import records.types.TypeExp;
import utility.Pair;

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
    public @Nullable TypeExp check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        TypeExp bool = TypeExp.fromConcrete(this, DataType.BOOLEAN);
        for (Expression expression : expressions)
        {
            TypeExp type = expression.check(data, state, onError);
            if (type == null || onError.recordError(this, TypeExp.unifyTypes(bool, type)) == null)
                return null;
        }
        return bool;
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        for (Expression expression : expressions)
        {
            Boolean b = (Boolean) expression.getValue(rowIndex, state);
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
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(DataType.BOOLEAN)));
    }
}
