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
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

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
public class IfThenElseExpression extends Expression
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
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        return null; // TODO
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return Utility.value(0); // TODO
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.of(condition, thenExpression, elseExpression).flatMap(Expression::allColumnNames);
    }

    @Override
    public String save(boolean topLevel)
    {
        return ""; // TODO
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
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
        return false; // TODO
    }

    @Override
    public int hashCode()
    {
        return 0; // TODO
    }
}
