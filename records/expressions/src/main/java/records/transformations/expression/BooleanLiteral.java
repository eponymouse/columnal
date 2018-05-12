package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.typeExp.TypeExp;
import styled.StyledString;

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
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws InternalException
    {
        return onError.recordType(this, TypeExp.bool(this));
    }

    @Override
    public @Value Object getValue(EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return Boolean.toString(value);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(Boolean.toString(value));
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
    public String editString()
    {
        return value.toString();
    }
}
