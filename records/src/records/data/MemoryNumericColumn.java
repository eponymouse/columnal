package records.data;

import records.data.columntype.NumericColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberDisplayInfo;
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
    private final String title;
    private final NumericColumnStorage storage;
    private final boolean mayBeBlank;

    public MemoryNumericColumn(RecordSet rs, String title, NumericColumnType type, List<String> values) throws InternalException
    {
        super(rs);
        mayBeBlank = type.mayBeBlank;
        storage = new NumericColumnStorage(mayBeBlank ? 2 : 0, mayBeBlank ? 1 : -1, new NumberDisplayInfo(type.displayPrefix, type.minDP));
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
    public @OnThread(Tag.Any) String getName()
    {
        return title;
    }

    @Override
    public long getVersion()
    {
        return 1;
    }

    @Override
    public DataType getType()
    {
        if (!mayBeBlank)
            return storage.getType();

        return new DataType()
        {
            List<TagType> tagTypes = Arrays.asList(new TagType("Blank", null), new TagType("Number", storage.getType()));

            @Override
            public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
            {
                return visitor.tagged(tagTypes, (i, prog) -> storage.getTag(i));
            }
        };
    }
}
