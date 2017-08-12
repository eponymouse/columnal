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
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Status;
import records.gui.expressioneditor.OperandNode;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 11/12/2016.
 */
public class VarUseExpression extends NonOperatorExpression
{
    private final String varName;

    public VarUseExpression(String varName)
    {
        this.varName = varName;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        Set<DataType> varType = state.findVarType(varName);
        if (varType == null)
        {
            onError.recordError(this, "Undeclared variable: \"" + varName + "\"");
            return null;
        }
        if (varType.size() > 1)
        {
            onError.recordError(this, "Variable \"" + varName + "\" cannot be used because it may have different types: " + varType.stream().map(DataType::toString).collect(Collectors.toList()));
            return null;
        }
        return varType.iterator().next();
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return state.get(varName);
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.empty();
    }

    @Override
    public String save(boolean topLevel)
    {
        return varName;
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public SingleLoader<OperandNode<Expression>> loadAsSingle()
    {
        return (p, s) -> new GeneralExpressionEntry(varName, Status.VARIABLE_USE, p, s);
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

        VarUseExpression that = (VarUseExpression) o;

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
