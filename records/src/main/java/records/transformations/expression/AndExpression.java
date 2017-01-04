package records.transformations.expression;

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
import utility.ExBiConsumer;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class AndExpression extends NaryOpExpression
{
    public AndExpression(List<Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new AndExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "&";
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        for (Expression expression : expressions)
        {
            DataType type = expression.check(data, state, onError);
            if (DataType.checkSame(DataType.BOOLEAN, type, s -> onError.accept(expression, s)) == null)
                return null;
        }
        return DataType.BOOLEAN;
    }

    @Override
    public List<Object> getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        for (Expression expression : expressions)
        {
            Boolean b = (Boolean) expression.getValue(rowIndex, state);
            if (b == false)
                return Collections.singletonList(false);
        }
        return Collections.singletonList(true);
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
