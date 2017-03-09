package records.data;

import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;

import java.util.List;

/**
 * Created by neil on 08/03/2017.
 */
public class EditableRecordSet extends RecordSet
{
    private int curLength = 1;

    public EditableRecordSet(List<? extends FunctionInt<RecordSet, ? extends Column>> columns) throws InternalException, UserException
    {
        super(columns);
    }

    @Override
    public boolean indexValid(int index) throws UserException, InternalException
    {
        return index < curLength;
    }

    @Override
    public int getLength() throws UserException, InternalException
    {
        return curLength;
    }
}
