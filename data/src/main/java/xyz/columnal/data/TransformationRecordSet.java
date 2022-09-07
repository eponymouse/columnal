package xyz.columnal.data;

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.SimulationFunctionInt;

import java.util.List;

public abstract class TransformationRecordSet extends RecordSet
{
    public TransformationRecordSet()
    {
        super();
    }

    public TransformationRecordSet(List<SimulationFunction<RecordSet, Column>> columns) throws InternalException, UserException
    {
        super(columns);
    }

    public void buildColumn(SimulationFunctionInt<RecordSet, Column> makeColumn) throws InternalException, UserException
    {
        columns.add(makeColumn.apply(this));
        if (columns.stream().map(Column::getName).distinct().count() != columns.size())
        {
            throw new UserException("Duplicate column names found");
        }
    }
}
