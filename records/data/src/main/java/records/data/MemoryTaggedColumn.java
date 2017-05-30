package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationRunnable;
import utility.TaggedValue;

import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryTaggedColumn extends EditableColumn
{
    private final TaggedColumnStorage storage;
    private final TypeId typeName;

    public MemoryTaggedColumn(RecordSet rs, ColumnId title, TypeId typeName, List<TagType<DataType>> tags, List<TaggedValue> list) throws InternalException
    {
        super(rs, title);
        this.typeName = typeName;
        this.storage = new TaggedColumnStorage(typeName, tags);
        this.storage.addAll(list);
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType() throws InternalException
    {
        return storage.getType();
    }

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        List<TagType<DataType>> tags = getType().apply(new SpecificDataTypeVisitor<List<TagType<DataType>>>()
        {
            @Override
            public List<TagType<DataType>> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return tags;
            }
        });
        return new MemoryTaggedColumn(rs, getName(), typeName, tags, storage.getShrunk(shrunkLength));
    }

    public void add(TaggedValue taggedValue) throws InternalException
    {
        storage.add(taggedValue);
    }


    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, count);
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        return storage.removeRows(index, count);
    }
}
