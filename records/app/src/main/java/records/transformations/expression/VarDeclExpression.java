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
import records.gui.expressioneditor.OperandNode;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;

import java.util.Map;
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
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        // If normal check is called, something has gone wrong because we are only
        // valid in a pattern
        onError.recordError(this, "Variable cannot be declared outside pattern match");
        return null;
    }

    @Override
    public @Nullable Pair<DataType, TypeState> checkAsPattern(boolean varDeclAllowed, DataType srcType, RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        if (!varDeclAllowed)
            return null; // We are a variable declaration, so clearly not allowed!

        @Nullable TypeState newState = state.add(varName, srcType, onError.recordError(this));
        if (newState == null)
            return null;
        else
            return new Pair<>(srcType, newState);
    }

    @Override
    public @OnThread(Tag.Simulation) @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, EvaluateState state) throws InternalException, UserException
    {
        return state.add(varName, value);
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on variable declaration (should only call matchAsPattern)");
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.empty();
    }

    @Override
    public String save(boolean topLevel)
    {
        return "@newvar " + varName;
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public FXPlatformFunction<ConsecutiveBase<Expression>, OperandNode<Expression>> loadAsSingle()
    {
        throw new RuntimeException("TODO");
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
}
