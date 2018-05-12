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
import records.gui.expressioneditor.GeneralExpressionEntry.Unfinished;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Pair;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 18/02/2017.
 */
public class UnfinishedExpression extends NonOperatorExpression
{
    private final String text;

    public UnfinishedExpression(String text)
    {
        this.text = text;
    }

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        onError.recordError(this, StyledString.s("Incomplete expression or unknown function: \"" + text + "\""));
        return null; // Unfinished expressions can't type check
    }

    @Override
    public @Value Object getValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Cannot get value for unfinished expression");
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
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> {
            GeneralExpressionEntry generalExpressionEntry = new GeneralExpressionEntry(new Unfinished(text), p, s);
            return generalExpressionEntry;
        };
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
        return o instanceof UnfinishedExpression && text.equals(((UnfinishedExpression)o).text);
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
