package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.types.TypeExp;
import styled.StyledString;
import utility.Pair;

import java.util.Map;

/**
 * Created by neil on 27/11/2016.
 */
public class BooleanLiteral extends Literal
{
    private final @Value Boolean value;

    public BooleanLiteral(boolean value)
    {
        this.value = DataTypeUtility.value(value);
    }

    @Override
    public @Recorded @Nullable TypeExp check(RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws InternalException
    {
        return onError.recordType(this, TypeExp.fromConcrete(this, DataType.BOOLEAN));
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(BracketedStatus surround)
    {
        return Boolean.toString(value);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(Boolean.toString(value));
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

        return value.equals(that.value);
    }

    @Override
    public int hashCode()
    {
        return (value ? 1 : 0);
    }

    @Override
    protected String editString()
    {
        return value.toString();
    }
}
