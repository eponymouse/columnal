package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
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
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static records.transformations.expression.AddSubtractExpression.Op.ADD;

/**
 * Created by neil on 10/12/2016.
 */
public class AddSubtractExpression extends NaryOpExpression
{
    public static enum Op { ADD, SUBTRACT };
    private final List<Op> ops;
    private @Nullable DataType type;

    public AddSubtractExpression(List<Expression> expressions, List<Op> ops)
    {
        super(expressions);
        this.ops = ops;
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new AddSubtractExpression(replacements, ops);
    }

    @Override
    public Optional<Rational> constantFold()
    {
        Rational running = Rational.ZERO;
        for (int i = 0; i < expressions.size(); i++)
        {
            Optional<Rational> r = expressions.get(i).constantFold();
            if (r.isPresent())
            {
                running = i == 0 || ops.get(i) == Op.ADD ? running.plus(r.get()) : running.minus(r.get());
            }
            else
                return Optional.empty();
        }
        return Optional.of(running);
    }

    @Override
    protected String saveOp(int index)
    {
        return ops.get(index) == ADD ? "+" : "-";
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        List<DataType> types = new ArrayList<>();
        for (Expression expression : expressions)
        {
            @Nullable DataType expType = expression.check(data, state, onError);
            if (expType == null)
                return null;
            types.add(expType);
        }
        type = DataType.checkAllSame(types, s -> onError.accept(this, s));
        return type;
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        Number n = (Number)expressions.get(0).getValue(rowIndex, state);
        for (int i = 1; i < expressions.size(); i++)
        {
            n = Utility.addSubtractNumbers(n, (Number)expressions.get(i).getValue(rowIndex, state), ops.get(i - 1) == ADD);
        }
        return Utility.value(n);
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
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(type)));
    }
}
