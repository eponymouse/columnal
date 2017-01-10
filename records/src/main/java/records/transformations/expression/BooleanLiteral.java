package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import utility.ExBiConsumer;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by neil on 27/11/2016.
 */
public class BooleanLiteral extends Literal
{
    private final boolean value;

    public BooleanLiteral(boolean value)
    {
        this.value = value;
    }

    @Override
    public DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError)
    {
        return DataType.BOOLEAN;
    }

    @Override
    public Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(boolean topLevel)
    {
        return Boolean.toString(value);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables)
    {
        return formulaManager.getBooleanFormulaManager().makeBoolean(value);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BooleanLiteral that = (BooleanLiteral) o;

        return value == that.value;
    }

    @Override
    public int hashCode()
    {
        return (value ? 1 : 0);
    }
}
