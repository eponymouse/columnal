package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryTaggedColumn extends Column
{
    private final ColumnId title;
    private final TaggedColumnStorage storage;

    public MemoryTaggedColumn(RecordSet rs, ColumnId title, List<TagType<DataType>> tags, List<List<Object>> list) throws InternalException, UserException
    {
        super(rs);
        this.title = title;
        this.storage = new TaggedColumnStorage(tags);
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
    public synchronized DataTypeValue getType() throws InternalException
    {
        return storage.getType();
    }

    @Override
    public Column shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        List<TagType<DataType>> tags = getType().apply(new SpecificDataTypeVisitor<List<TagType<DataType>>>()
        {
            @Override
            public List<TagType<DataType>> tagged(List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return tags;
            }
        });
        return new MemoryTaggedColumn(rs, title, tags, storage.getShrunk(shrunkLength));
    }
}
