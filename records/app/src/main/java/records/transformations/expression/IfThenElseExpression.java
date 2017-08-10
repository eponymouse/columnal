package records.transformations.expression;

import annotation.qual.Value;
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
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Really, this could be done in a match expression, but I suspect
 * that would confuse users.
 *
 * This *cannot* be done in an if function as originally planned,
 * because functions are eager in their arguments, and you want
 * to be lazy in the unused then/else part for situations like
 * if y <> 0 then x / y else 0
 */
public class IfThenElseExpression extends NonOperatorExpression
{
    private final Expression condition;
    private final Expression thenExpression;
    private final Expression elseExpression;

    public IfThenElseExpression(Expression condition, Expression thenExpression, Expression elseExpression)
    {
        this.condition = condition;
        this.thenExpression = thenExpression;
        this.elseExpression = elseExpression;
    }


    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        @Nullable DataType conditionType = condition.check(data, state, onError);
        if (conditionType == null)
            return null;
        if (DataType.checkSame(DataType.BOOLEAN, conditionType, onError.recordError(this)) == null)
        {
            onError.recordError(condition, "Expected boolean type in condition but was " + conditionType);
            return null;
        }
        @Nullable DataType thenType = thenExpression.check(data, state, onError);
        @Nullable DataType elseType = elseExpression.check(data, state, onError);
        if (thenType == null || elseType == null)
            return null;

        @Nullable DataType jointType = DataType.checkSame(thenType, elseType, onError.recordError(this));
        if (jointType == null)
        {
            onError.recordError(elseExpression, "Expected same type in then and else, but was " + thenType + " and " + elseType);
            return null;
        }
        return jointType;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        Boolean b = (Boolean)condition.getValue(rowIndex, state);
        if (b)
            return thenExpression.getValue(rowIndex, state);
        else
            return elseExpression.getValue(rowIndex, state);
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.of(condition, thenExpression, elseExpression).flatMap(Expression::allColumnNames);
    }

    @Override
    public String save(boolean topLevel)
    {
        String content = "@if " + condition.save(false) + " @then " + thenExpression.save(false) + " @else " + elseExpression.save(false);
        return topLevel ? content : ("(" + content + ")");
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public FXPlatformFunction<ConsecutiveBase<Expression>, OperandNode<Expression>> loadAsSingle()
    {
        throw new RuntimeException("TODO");
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty(); // TODO
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null; // TODO
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IfThenElseExpression that = (IfThenElseExpression) o;

        if (!condition.equals(that.condition)) return false;
        if (!thenExpression.equals(that.thenExpression)) return false;
        return elseExpression.equals(that.elseExpression);
    }

    @Override
    public int hashCode()
    {
        int result = condition.hashCode();
        result = 31 * result + thenExpression.hashCode();
        result = 31 * result + elseExpression.hashCode();
        return result;
    }
}
