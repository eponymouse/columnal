package records.data;

import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import records.gui.EnteredDisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryNumericColumn extends Column
{
    private final ColumnId title;
    private final NumericColumnStorage storage;

    private MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo) throws InternalException, UserException
    {
        super(rs);
        storage = new NumericColumnStorage(numberInfo);
        this.title = title;
    }

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, List<Number> values) throws InternalException, UserException
    {
        this(rs, title, numberInfo);
        storage.addAll(values);
    }

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, Stream<String> values) throws InternalException, UserException
    {
        this(rs, title, numberInfo);
        for (String value : Utility.iterableStream(values))
        {
            storage.addRead(value);
        }
    }

    public void add(String value) throws InternalException, UserException
    {
        storage.addRead(value);
    }

    @Override
    public @OnThread(Tag.Any) ColumnId getName()
    {
        return title;
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return storage.getType();
    }

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        return new MemoryNumericColumn(rs, title, storage.getDisplayInfo(), storage._test_getShrunk(shrunkLength));
    }

    @Override
    public DisplayValue storeValue(EnteredDisplayValue writtenValue) throws InternalException, UserException
    {
        return storage.storeValue(writtenValue);
    }

    @Override
    public void addRow() throws InternalException, UserException
    {
        storage.addRow();
    }
}
