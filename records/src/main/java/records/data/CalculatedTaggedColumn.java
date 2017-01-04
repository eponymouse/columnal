package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;

/**
 * Created by neil on 05/11/2016.
 */
public abstract class CalculatedTaggedColumn extends CalculatedColumn
{
    private final TaggedColumnStorage storage;

    @SuppressWarnings("initialization")
    public <DT extends DataType> CalculatedTaggedColumn(RecordSet recordSet, ColumnId name, TypeId typeName, List<TagType<DT>> copyTagTypes) throws InternalException
    {
        super(recordSet, name);
        
        this.storage = new TaggedColumnStorage(typeName, copyTagTypes);
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType() throws InternalException, UserException
    {
        return storage.getType();
    }

    @Override
    protected int getCacheFilled()
    {
        return storage.filled();
    }

    public void addAllUnpacked(List<Object> values) throws UserException, InternalException
    {
        for (Object v : values)
            addUnpacked(v);
    }

    protected void addUnpacked(Object value) throws UserException, InternalException
    {
        storage.addUnpacked((Pair<Integer, @Nullable Object>)value);
    }
}
