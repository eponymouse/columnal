package records.data.datatype;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ArrayColumnStorage;
import records.data.BooleanColumnStorage;
import records.data.ColumnStorage;
import records.data.DateColumnStorage;
import records.data.TaggedValue;
import records.data.TupleColumnStorage;
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
import threadchecker.OnThread;
import threadchecker.Tag;
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
    public static @Value Object generateExample(DataType type, int index) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<@Value Object>()
        {

            @Override
            public @Value Object number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return Utility.value((long)index);
            }

            @Override
            public @Value Object text() throws InternalException, UserException
            {
                return Utility.value(Arrays.asList("Aardvark", "Bear", "Cat", "Dog", "Emu", "Fox").get(index));
            }

            @Override
            public @Value Object bool() throws InternalException, UserException
            {
                return Utility.value((index % 2) == 1);
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return Utility.value(LocalDate.ofEpochDay(index));
            }

            @Override
            public @Value TaggedValue tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tag = index % tags.size();
                @Nullable DataType inner = tags.get(tag).getInner();
                if (inner != null)
                    return new TaggedValue(tag, generateExample(inner, index - tag));
                else
                    return new TaggedValue(tag, null);
            }

            @Override
            public @Value Object tuple(List<DataType> inner) throws InternalException, UserException
            {
                return Utility.value(Utility.<DataType, @Value Object>mapListEx(inner, t -> generateExample(t, index)).toArray(new @Value Object[0]));
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
            {
                return Utility.value(Collections.emptyList());
            }
        });
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

    public static DisplayValue display(DataType dataType, Object object) throws UserException, InternalException
    {
        return dataType.apply(new DataTypeVisitor<DisplayValue>()
        {
            @Override
            public DisplayValue number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new DisplayValue((Number)object, displayInfo.getUnit(), displayInfo.getMinimumDP());
            }

            @Override
            public DisplayValue text() throws InternalException, UserException
            {
                return new DisplayValue((String)object);
            }

            @Override
            public DisplayValue bool() throws InternalException, UserException
            {
                return new DisplayValue((Boolean)object);
            }

            @Override
            public DisplayValue date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new DisplayValue((Temporal) object);
            }

            @Override
            public DisplayValue tagged(TypeId typeName, List<TagType<DataType>> tagTypes) throws InternalException, UserException
            {
                int tag = ((TaggedValue)object).getTagIndex();
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

            @Override
            public DisplayValue tuple(List<DataType> inner) throws InternalException, UserException
            {
                Object[] array = (Object[])object;
                if (array.length != inner.size())
                    throw new InternalException("Tuple type does not match value, expected: " + inner.size() + " got " + array.length);
                List<DisplayValue> innerDisplay = new ArrayList<>();
                for (int i = 0; i < array.length; i++)
                {
                    innerDisplay.add(display(inner.get(i), array[i]));
                }
                return DisplayValue.tuple(innerDisplay);
            }

            @Override
            public DisplayValue array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return DisplayValue.array(Collections.emptyList());
                @NonNull DataType innerFinal = inner;
                List<Object> list = (List<Object>)object;
                return DisplayValue.array(Utility.mapListEx(list, o -> display(innerFinal, o)));
            }
        });
    }

    @OnThread(Tag.Simulation)
    public static ColumnStorage<?> makeColumnStorage(final DataType inner) throws InternalException
    {
        return inner.apply(new DataTypeVisitorEx<ColumnStorage<?>, InternalException>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumericColumnStorage(displayInfo);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> bool() throws InternalException
            {
                return new BooleanColumnStorage();
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> text() throws InternalException
            {
                return new StringColumnStorage();
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return new DateColumnStorage(dateTimeInfo);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException
            {
                return new TaggedColumnStorage(typeName, tags);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tuple(List<DataType> innerTypes) throws InternalException
            {
                return new TupleColumnStorage(innerTypes);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> array(@Nullable DataType inner) throws InternalException
            {
                return new ArrayColumnStorage(inner);
            }
        });
    }
}
