package records.transformations.expression;

import records.data.RecordSet;
import records.data.datatype.BooleanDataType;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

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
    public DataType getType(RecordSet data) throws UserException, InternalException
    {
        return new BooleanDataType((i, prog) -> value);
    }

    @Override
    public @OnThread(Tag.FXPlatform) String save()
    {
        return Boolean.toString(value);
    }
}
