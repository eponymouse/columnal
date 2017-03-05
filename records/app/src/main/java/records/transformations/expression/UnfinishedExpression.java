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
import records.gui.expressioneditor.GeneralEntry;
import records.gui.expressioneditor.GeneralEntry.Status;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;

import java.util.Map;
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
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        onError.recordError(this, "Incomplete expression");
        return null; // Unfinished expressions can't type check
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Cannot get value for unfinished expression");
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.empty();
    }

    @Override
    public String save(boolean topLevel)
    {
        return "@unfinished " + OutputBuilder.quoted(text);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public FXPlatformFunction<ConsecutiveBase, OperandNode> loadAsSingle()
    {
        return c -> new GeneralEntry(text, Status.UNFINISHED, c);
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
        return text.hashCode();
    }
}
