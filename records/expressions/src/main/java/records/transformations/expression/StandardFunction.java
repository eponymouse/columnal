package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionDefinition.FunctionTypes;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

public class StandardFunction extends NonOperatorExpression
{
    private final FunctionDefinition functionDefinition;
    // null if type check fails for some reason:
    private @MonotonicNonNull FunctionTypes type;

    public StandardFunction(FunctionDefinition functionDefinition)
    {
        this.functionDefinition = functionDefinition;
    }

    @Override
    public @Recorded @Nullable TypeExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = functionDefinition.makeParamAndReturnType(typeState.getTypeManager());
        return type.getFunctionType(this);
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        if (type == null)
            throw new InternalException("Attempting to fetch function despite failing type check");
        
        return type.getInstanceAfterTypeCheck();
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "@function " + OutputBuilder.quotedIfNecessary(functionDefinition.getName());
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new GeneralExpressionEntry(new GeneralExpressionEntry.StdFunc(functionDefinition), p, s);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return type == null ? null : newExpressionOfDifferentType.getDifferentType(type.getFunctionType(this));
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(functionDefinition.getName());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StandardFunction that = (StandardFunction) o;
        return Objects.equals(functionDefinition.getName(), that.functionDefinition.getName());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(functionDefinition.getName());
    }

    public String _test_getName()
    {
        return functionDefinition.getName();
    }
}
