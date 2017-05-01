package records.data;

import javafx.application.Platform;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;

import java.util.List;

/**
 * Created by neil on 08/03/2017.
 */
public class EditableRecordSet extends RecordSet
{
    private int curLength;

    public EditableRecordSet(List<? extends FunctionInt<RecordSet, ? extends Column>> columns, int length) throws InternalException, UserException
    {
        super(columns);
        this.curLength = length;
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

    @Override
    protected void addRow() throws UserException, InternalException
    {
        for (Column c : getColumns())
        {
            c.addRow();
        }
        curLength += 1;
        if (listener != null)
        {
            RecordSetListener listenerFinal = listener;
            Platform.runLater(() -> listenerFinal.rowAddedAtEnd());
        }
        // TODO re-run dependents
    }
}
