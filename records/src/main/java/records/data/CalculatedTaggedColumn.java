package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 05/11/2016.
 */
public abstract class CalculatedTaggedColumn extends CalculatedColumn
{
    private final TaggedColumnStorage storage;

    @SuppressWarnings("initialization")
    public <DT extends DataType> CalculatedTaggedColumn(RecordSet recordSet, ColumnId name, TypeId typeName, List<TagType<DT>> copyTagTypes) throws InternalException, UserException
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

    public void addAllUnpacked(List<List<Object>> values) throws UserException, InternalException
    {
        for (List<Object> v : values)
            addUnpacked(v);
    }

    protected void addUnpacked(List<Object> values) throws UserException, InternalException
    {
        storage.addUnpacked(values);
    }
}
