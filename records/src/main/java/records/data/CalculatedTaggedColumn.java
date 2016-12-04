package records.data;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by neil on 05/11/2016.
 */
public abstract class CalculatedTaggedColumn extends CalculatedColumn
{
    private final TaggedColumnStorage storage;

    @SuppressWarnings("initialization")
    public <DT extends DataType> CalculatedTaggedColumn(RecordSet recordSet, ColumnId name, List<TagType<DT>> copyTagTypes) throws InternalException, UserException
    {
        super(recordSet, name);
        
        this.storage = new TaggedColumnStorage(copyTagTypes);
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
