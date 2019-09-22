package records.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationRunnable;
import utility.TaggedValue;
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryTaggedColumn extends EditableColumn
{
    private final TaggedColumnStorage storage;
    private final TypeId typeName;
    private final TaggedValue defaultValue;
    private final ImmutableList<Either<Unit, DataType>> typeVars;

    public MemoryTaggedColumn(RecordSet rs, ColumnId title, TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, List<TagType<DataType>> tags, List<Either<String, TaggedValue>> values, TaggedValue defaultValue) throws InternalException
    {
        super(rs, title);
        this.typeName = typeName;
        this.typeVars = typeVars;
        this.defaultValue = defaultValue;
        this.storage = new TaggedColumnStorage(typeName, typeVars, tags, true);
        this.storage.addAll(values.stream());
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
        List<TagType<DataType>> tags = getType().getType().apply(new SpecificDataTypeVisitor<List<TagType<DataType>>>()
        {
            @Override
            public List<TagType<DataType>> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return tags;
            }
        });
        return new MemoryTaggedColumn(rs, getName(), typeName, typeVars, tags, storage.getShrunk(shrunkLength), defaultValue);
    }

    public void add(Either<String, TaggedValue> taggedValue) throws InternalException
    {
        storage.addAll(Stream.of(taggedValue));
    }


    @SuppressWarnings("valuetype") // Not 100% sure why this is needed
    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.replicate(count, Either.<String, TaggedValue>right(defaultValue)));
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        return storage.removeRows(index, count);
    }

    @Override
    @OnThread(Tag.Any)
    public @Value Object getDefaultValue()
    {
        return defaultValue;
    }
}
