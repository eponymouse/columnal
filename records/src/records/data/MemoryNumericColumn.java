package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.columntype.NumericColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryNumericColumn extends Column
{
    private final String title;
    private final NumericColumnStorage storage;
    private final boolean hasBlanks;

    public MemoryNumericColumn(RecordSet rs, String title, NumericColumnType type, List<String> values) throws InternalException
    {
        super(rs);
        hasBlanks = values.stream().anyMatch(String::isEmpty);
        // TODO put blanks ba
        storage = new NumericColumnStorage(); //hasBlanks ? 1 : 0);
        this.title = title;
        int nextSkip = 0;
        for (String value : values)
        {
            // Add it if it can't be blank, or if isn't blank
            if (!type.mayBeBlank || !value.isEmpty())
            {
                String s = value;
                storage.addNumber(type.removePrefix(s));
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
        if (!hasBlanks)
            return storage.getType();

        return new DataType()
        {
            List<TagType> tagTypes = Arrays.asList(new TagType("Blank", null), new TagType("Number", storage.getType()));

            @Override
            public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
            {
                return visitor.tagged(tagTypes, (i, prog) -> {
                    int tag = storage.getTag(i);
                    if (tag == -1)
                        tag = 1; // No tag means it's a number so second tag
                    return tag;
                });
            }
        };
    }
}
