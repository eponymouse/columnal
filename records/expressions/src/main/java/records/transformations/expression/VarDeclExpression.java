package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
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
    private final @ExpressionIdentifier String varName;

    public VarDeclExpression(@ExpressionIdentifier String varName)
    {
        this.varName = varName;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (!GrammarUtility.validIdentifier(varName))
        {
            onError.recordError(this, StyledString.s("Invalid identifier: \"" + varName + "\""));
            return null;
        }
        
        MutVar type = new MutVar(this);
        @Nullable TypeState newState = typeState.add(varName, type, onError.recordErrorCurried(this));
        if (newState == null)
            return null;
        else
            return new CheckedExp(onError.recordTypeNN(this, type), newState, ExpressionKind.PATTERN);
    }

    @Override
    public ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        return result(DataTypeUtility.value(true), state.add(varName, value));
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on variable declaration (should only call matchAsPattern)");
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public Stream<String> allVariableReferences()
    {
        // This isn't a reference, it's a declaration
        return Stream.empty();
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "_" + varName;
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(varName);
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(GeneralExpressionEntry.load("_" + varName));
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

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }
}
