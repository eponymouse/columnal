package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.BooleanColumnStorage;
import records.data.ColumnStorage;
import records.data.DateColumnStorage;
import records.data.NestedColumnStorage;
import records.data.NumericColumnStorage;
import records.data.StringColumnStorage;
import records.data.TaggedColumnStorage;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import utility.Pair;
import utility.Utility;

import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 22/11/2016.
 */
public class DataTypeUtility
{
    public static List<Object> generateExample(DataType type, int index) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<List<Object>>()
        {

            @Override
            public List<Object> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return Collections.singletonList((Long)(long)index);
            }

            @Override
            public List<Object> text() throws InternalException, UserException
            {
                return Collections.singletonList(Arrays.asList("Aardvark", "Bear", "Cat", "Dog", "Emu", "Fox").get(index));
            }

            @Override
            public List<Object> bool() throws InternalException, UserException
            {
                return Collections.singletonList((index % 2) == 1);
            }

            @Override
            public List<Object> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return Collections.singletonList(LocalDate.ofEpochDay(index));
            }

            @Override
            public List<Object> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tag = index % tags.size();
                @Nullable DataType inner = tags.get(tag).getInner();
                if (inner != null)
                    return Utility.consList((Object)(Integer)tag, generateExample(inner, index - tag));
                else
                    return Collections.singletonList((Object)(Integer)tag);
            }
        });
    }

    public static int compare(DataType dataType, List<Object> as, List<Object> bs) throws InternalException
    {
        return Utility.compareLists(as, bs);
        /*
        return dataType.apply(new DataTypeVisitor<Integer>()
        {
            int index = 0;

            @Override
            public Integer number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return Utility.compareNumbers(as.get(index), bs.get(index));
            }

            @Override
            public Integer text() throws InternalException, UserException
            {
                return ((String)as.get(index)).compareTo((String)bs.get(index));
            }

            @Override
            public Integer tagged(List<TagType> tags) throws InternalException, UserException
            {
                return null;
            }
        })
        */
    }

    public static List<List<DisplayValue>> displayAll(List<DataType> columnTypes, List<List<List<Object>>> dataRows) throws InternalException, UserException
    {
        List<List<DisplayValue>> r = new ArrayList<>();
        for (List<List<Object>> dataRow : dataRows)
        {
            List<DisplayValue> row = new ArrayList<>();
            for (int i = 0; i < columnTypes.size(); i++)
                row.add(display(columnTypes.get(i), dataRow.get(i)));
            r.add(row);
        }
        return r;
    }

    public static DisplayValue display(DataType dataType, List<Object> objects) throws UserException, InternalException
    {
        return dataType.apply(new DataTypeVisitor<DisplayValue>()
        {
            int index = 0;
            @Override
            public DisplayValue number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new DisplayValue((Number)objects.get(index), displayInfo.getUnit(), displayInfo.getMinimumDP());
            }

            @Override
            public DisplayValue text() throws InternalException, UserException
            {
                return new DisplayValue((String)objects.get(index));
            }

            @Override
            public DisplayValue bool() throws InternalException, UserException
            {
                return new DisplayValue((Boolean)objects.get(index));
            }

            @Override
            public DisplayValue date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new DisplayValue((Temporal) objects.get(index));
            }

            @Override
            public DisplayValue tagged(TypeId typeName, List<TagType<DataType>> tagTypes) throws InternalException, UserException
            {
                int tag = (Integer)objects.get(index++);
                TagType tagType = tagTypes.get(tag);
                @Nullable DataType inner = tagType.getInner();
                if (DataType.canFitInOneNumeric(tagTypes))
                {
                    if (inner == null)
                        return new DisplayValue(tagType.getName());
                    else
                        return inner.apply(this);
                }
                else
                {
                    return new DisplayValue(tagType.getName() + (inner == null ? "" : (" " + inner.apply(this))));
                }
            }
        });
    }

    public static ColumnStorage<?> makeColumnStorage(final DataType inner) throws InternalException
    {
        return inner.apply(new DataTypeVisitorEx<ColumnStorage<?>, InternalException>()
        {
            @Override
            public ColumnStorage<?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumericColumnStorage(displayInfo);
            }

            @Override
            public ColumnStorage<?> bool() throws InternalException
            {
                return new BooleanColumnStorage();
            }

            @Override
            public ColumnStorage<?> text() throws InternalException
            {
                return new StringColumnStorage();
            }

            @Override
            public ColumnStorage<?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return new DateColumnStorage(dateTimeInfo);
            }

            @Override
            public ColumnStorage<?> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException
            {
                return new TaggedColumnStorage(typeName, tags);
            }

            @Override
            public ColumnStorage<?> tuple(List<DataType> innerTypes) throws InternalException
            {
                return new NestedColumnStorage(innerTypes);
            }

            @Override
            public ColumnStorage<?> array(DataType inner) throws InternalException
            {
                return new NestedColumnStorage(inner);
            }
        });
    }
}
