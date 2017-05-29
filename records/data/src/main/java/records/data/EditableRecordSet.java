package records.data;

import javafx.application.Platform;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import utility.SimulationSupplier;

import java.util.List;

/**
 * Created by neil on 08/03/2017.
 */
public class EditableRecordSet extends RecordSet
{
    private int curLength;

    public EditableRecordSet(List<? extends FunctionInt<RecordSet, ? extends Column>> columns, SimulationSupplier<Integer> loadLength) throws InternalException, UserException
    {
        super(columns);
        this.curLength = loadLength.get();
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
    public void addRow() throws InternalException
    {
        int newRowIndex = curLength;
        curLength += 1;
        if (listener != null)
        {
            RecordSetListener listenerFinal = listener;
            Platform.runLater(() -> listenerFinal.removedAddedRows(newRowIndex, 0, 1));
        }
        // TODO re-run dependents
    }
}
