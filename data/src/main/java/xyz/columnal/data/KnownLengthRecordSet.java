package xyz.columnal.data;

import annotation.units.TableDataRowIndex;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.ExFunction;
import xyz.columnal.utility.SimulationFunction;

import java.util.List;

/**
 * Created by neil on 07/12/2016.
 */
public class KnownLengthRecordSet extends RecordSet
{
    private final int length;

    public <C extends Column> KnownLengthRecordSet(List<SimulationFunction<RecordSet, C>> columns, int length) throws InternalException, UserException
    {
        super(columns);
        this.length = length;
    }

    @Override
    public boolean indexValid(int index) throws UserException, InternalException
    {
        return index < length;
    }

    @Override
    @SuppressWarnings("units")
    public @TableDataRowIndex int getLength() throws UserException, InternalException
    {
        return length;
    }
}
