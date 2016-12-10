package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

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
    public NaryOpExpression copy(List<@Nullable Expression> replacements)
    {
        List<Expression> newExps = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++)
        {
            @Nullable Expression newExp = replacements.get(i);
            newExps.add(newExp != null ? newExp : expressions.get(i));
        }
        return new AndExpression(newExps);
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
            Boolean b = (Boolean) expression.getValue(rowIndex, state).get(0);
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
    @OnThread(Tag.Simulation)
    public @Nullable Expression _test_typeFailure(Random r, FunctionInt<@Nullable DataType, Expression> newExpressionOfDifferentType) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.apply(DataType.BOOLEAN)));
    }
}
