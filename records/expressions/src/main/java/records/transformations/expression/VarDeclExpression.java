package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.OperandNode;
import records.typeExp.MutVar;
import styled.StyledString;
import utility.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 22/02/2017.
 */
public class VarDeclExpression extends NonOperatorExpression
{
    private final String varName;

    public VarDeclExpression(String varName)
    {
        this.varName = varName;
    }

    @Override
    public @Nullable @Recorded CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        MutVar type = new MutVar(this);
        @Nullable TypeState newState = typeState.add(varName, type, onError.recordErrorCurried(this));
        if (newState == null)
            return null;
        else
            return new CheckedExp(onError.recordTypeNN(this, type), newState, ExpressionKind.PATTERN);
    }

    @Override
    public @Nullable EvaluateState matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        return state.add(varName, value);
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on variable declaration (should only call matchAsPattern)");
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "@newvar " + varName;
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(varName);
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new GeneralExpressionEntry(new GeneralExpressionEntry.VarDecl(varName), p, s);
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VarDeclExpression that = (VarDeclExpression) o;

        return varName.equals(that.varName);
    }

    @Override
    public int hashCode()
    {
        return varName.hashCode();
    }

    public String getName()
    {
        return varName;
    }
}
