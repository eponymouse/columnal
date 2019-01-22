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

    public void buildColumn(SimulationFunctionInt<RecordSet, Column> makeColumn) throws InternalException
    {
        columns.add(makeColumn.apply(this));
    }
}
