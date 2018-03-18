package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Status;
import records.gui.expressioneditor.OperandNode;
import records.types.MutVar;
import records.types.TypeExp;
import styled.StyledString;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The question mark that makes an implicit lambda, e.g. in ? < 5
 */
public class ImplicitLambdaArg extends NonOperatorExpression
{
    @Override
    public @Nullable TypeExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        ImmutableList<TypeExp> questTypes = typeState.findVarType("?");
        if (questTypes == null || questTypes.isEmpty())
            throw new UserException("? is not a valid expression by itself");
        // Pick last one in case of nested definitions:
        return questTypes.get(questTypes.size() - 1);
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return state.get("?");
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "?";
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new GeneralExpressionEntry("?", false, Status.IMPLICIT_LAMBDA_ARG, p, s);
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
        return o instanceof ImplicitLambdaArg;
    }

    @Override
    public int hashCode()
    {
        return 1;
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s("?");
    }
}
