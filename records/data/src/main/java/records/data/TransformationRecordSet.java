package records.data;

import records.error.InternalException;
import records.error.UserException;
import utility.SimulationFunction;
import utility.SimulationFunctionInt;

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
