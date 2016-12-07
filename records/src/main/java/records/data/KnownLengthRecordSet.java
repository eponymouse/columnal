package records.data;

import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;

import java.util.List;

/**
 * Created by neil on 07/12/2016.
 */
public class KnownLengthRecordSet extends RecordSet
{
    private final int length;

    public KnownLengthRecordSet(String title, List<FunctionInt<RecordSet, Column>> columns, int length) throws InternalException, UserException
    {
        super(title, columns);
        this.length = length;
    }

    @Override
    public boolean indexValid(int index) throws UserException, InternalException
    {
        return index < length;
    }

    @Override
    public int getLength() throws UserException, InternalException
    {
        return length;
    }
}
