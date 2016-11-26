package records.transformations.expression;

import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;

/**
 * Created by neil on 25/11/2016.
 */
public class NumericLiteral extends Expression
{
    private final Number value;

    public NumericLiteral(Number value)
    {
        this.value = value;
    }

    @Override
    public DataType getType(RecordSet data) throws UserException, InternalException
    {
        return new DataType()
        {
            @Override
            public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
            {
                return visitor.number((i, prog) -> value, NumberDisplayInfo.DEFAULT);
            }
        };
    }

    @Override
    public String save()
    {
        if (value instanceof Double)
            return String.format("%f", value.doubleValue());
        else
            return value.toString();
    }
}
