package records.data;

import records.data.columntype.NumericColumnType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryNumericColumn extends Column
{
    private final ColumnId title;
    private final NumericColumnStorage storage;
    private final boolean mayBeBlank;

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumericColumnType type, List<String> values) throws InternalException, UserException
    {
        super(rs);
        mayBeBlank = type.mayBeBlank;
        storage = new NumericColumnStorage(mayBeBlank ? 2 : 0, mayBeBlank ? 1 : -1, new NumberInfo(type.unit, type.minDP));
        this.title = title;
        for (String value : values)
        {
            // Add it if it can't be blank, or if isn't blank
            if (!mayBeBlank || !value.isEmpty())
            {
                String s = value;
                storage.addRead(type.removePrefix(s));
            } else
            {
                storage.addTag(0);
            }
        }
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
        if (!mayBeBlank)
            return storage.getType();

        return DataTypeValue.tagged("_BlankOrNumber", Arrays.asList(new TagType<>("Blank", null), new TagType<>("Number", storage.getType())),
            (i, prog) -> storage.getTag(i));
    }

    @Override
    public Column shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        return new MemoryNumericColumn(rs, title, new NumericColumnType(storage.getDisplayInfo().getUnit(), storage.getDisplayInfo().getMinimumDP(), mayBeBlank), storage.getShrunk(shrunkLength));
    }
}
