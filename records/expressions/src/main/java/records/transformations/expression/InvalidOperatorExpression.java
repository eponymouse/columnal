package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Unfinished;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.StreamTreeBuilder;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An expression with mixed operators, which make it invalid.  Can't be run, but may be
 * used while editing and for loading/saving invalid expressions.
 */
public class InvalidOperatorExpression extends NonOperatorExpression
{
    private final ImmutableList<Either<String, Expression>> items;

    public InvalidOperatorExpression(ImmutableList<Either<String, @Recorded Expression>> operands)
    {
        this.items = operands;
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();
        for (int i = 0; i < items.size(); i++)
        {
            items.get(i).either_(s -> r.add(GeneralExpressionEntry.load(new Unfinished(s))),
                e -> r.addAll(e.loadAsConsecutive(BracketedStatus.MISC)));
        }
        return r.stream();
    }

    @Override
    public @Nullable CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        onError.recordError(this, StyledString.s("Mixed or invalid operators in expression"));
        return null; // Invalid expressions can't type check
    }

    @Override
    public @OnThread(Tag.Simulation) Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Cannot get value for invalid expression");
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return items.stream().flatMap(x -> x.either(s -> Stream.of(), e -> e.allColumnReferences()));
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "TODO";
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        InvalidOperatorExpression that = (InvalidOperatorExpression) o;

        return items.equals(that.items);
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s("TODO");
    }
}
