package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.typeExp.MutVar;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 23/02/2017.
 */
public class MatchAnythingExpression extends NonOperatorExpression
{
    @Override
    public @Nullable @Recorded CheckedExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return new CheckedExp(onError.recordTypeNN(this, new MutVar(this)), state, ExpressionKind.PATTERN);
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on \"@anything\" pattern (should only call matchAsPattern)");
    }

    @Override
    public @OnThread(Tag.Simulation) @Nullable EvaluateState matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        // Like the name says, we match anything:
        return state;
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "@anything";
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s("@anything");
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionNodeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(GeneralExpressionEntry.load(Keyword.ANYTHING));
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        return o instanceof MatchAnythingExpression;
    }

    @Override
    public int hashCode()
    {
        return 0;
    }
}
