package records.data;

import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import records.gui.EnteredDisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryBooleanColumn extends Column
{
    private final ColumnId title;
    private final BooleanColumnStorage storage;

    public MemoryBooleanColumn(RecordSet rs, ColumnId title, List<Boolean> list) throws InternalException
    {
        super(rs);
        this.title = title;
        this.storage = new BooleanColumnStorage();
        this.storage.addAll(list);
    }

    @Override
    @OnThread(Tag.Any)
    public ColumnId getName()
    {
        return title;
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        return storage.getType();
    }

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException
    {
        return new MemoryBooleanColumn(rs, title, storage.getShrunk(shrunkLength));
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

    public void add(boolean b) throws InternalException
    {
        storage.add(b);
    }
}
