package records.data.datatype;

import records.error.InternalException;
import records.error.UserException;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 25/11/2016.
 */
public class BooleanDataType extends DataType
{
    public static final List<TagType> tagTypes = Arrays.asList(new TagType("false", null), new TagType("true", null));

    private final GetValue<Boolean> get;

    public BooleanDataType(GetValue<Boolean> get)
    {
        this.get = get;
    }

    @Override
    public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
    {
        return visitor.tagged(tagTypes, DataType.mapValue(get, b -> b ? 1 : 0));
    }
}
