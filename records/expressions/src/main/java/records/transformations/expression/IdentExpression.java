package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Unfinished;
import records.loadsave.OutputBuilder;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Pair;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A plain identifier.  If it resolves to a variable, it's a variable-use.  If not, it's an unfinished expression.
 */
public class IdentExpression extends NonOperatorExpression
{
    // TODO add resolver listener
    private final String text;

    public IdentExpression(String text)
    {
        this.text = text;
    }

    @Override
    public @Nullable CheckedExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (!GrammarUtility.validIdentifier(text))
        {
            onError.recordError(this, StyledString.s("Invalid identifier: \"" + text + "\""));
            return null;
        }
        
        List<TypeExp> varType = state.findVarType(text);
        if (varType == null)
        {
            onError.recordError(this, StyledString.s("Incomplete expression or unknown function or variable: \"" + text + "\""));
            return null;
        }
        // If they're trying to use it, it justifies us trying to unify all the types:
        return onError.recordTypeAndError(this, TypeExp.unifyTypes(varType), ExpressionKind.EXPRESSION, state);
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        return new Pair<>(state.get(text), state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "@unfinished " + OutputBuilder.quoted(text);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(text);
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(GeneralExpressionEntry.load(new Unfinished(text)));
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
        return o instanceof IdentExpression && text.equals(((IdentExpression)o).text);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(text);
    }

    public String getText()
    {
        return text;
    }
}