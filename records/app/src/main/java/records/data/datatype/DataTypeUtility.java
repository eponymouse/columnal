package records.data.datatype;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ArrayColumnStorage;
import records.data.BooleanColumnStorage;
import records.data.Column.ProgressListener;
import records.data.ColumnStorage;
import records.data.TemporalColumnStorage;
import records.data.unit.Unit;
import utility.TaggedValue;
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
import utility.ExBiConsumer;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
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
                return value((long)index);
            }

            @Override
            public @Value Object text() throws InternalException, UserException
            {
                return value(Arrays.asList("Aardvark", "Bear", "Cat", "Dog", "Emu", "Fox").get(index));
            }

            @Override
            public @Value Object bool() throws InternalException, UserException
            {
                return value((index % 2) == 1);
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return value(dateTimeInfo, LocalDate.ofEpochDay(index));
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
                return value(Utility.<DataType, @Value Object>mapListEx(inner, t -> generateExample(t, index)).toArray(new @Value Object[0]));
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
            {
                return value(Collections.emptyList());
            }
        });
    }

    public static DisplayValue display(int rowIndex, DataType dataType, @Value Object object) throws UserException, InternalException
    {
        return dataType.apply(new DataTypeVisitor<DisplayValue>()
        {
            @Override
            public DisplayValue number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new DisplayValue(rowIndex, (Number)object, displayInfo.getUnit(), displayInfo.getMinimumDP());
            }

            @Override
            public DisplayValue text() throws InternalException, UserException
            {
                return new DisplayValue(rowIndex, (String)object);
            }

            @Override
            public DisplayValue bool() throws InternalException, UserException
            {
                return new DisplayValue(rowIndex, (Boolean)object);
            }

            @Override
            public DisplayValue date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new DisplayValue(rowIndex, (Temporal) object);
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
                        return new DisplayValue(rowIndex, tagType.getName());
                    else
                        return inner.apply(this);
                }
                else
                {
                    return new DisplayValue(rowIndex, tagType.getName() + (inner == null ? "" : (" " + inner.apply(this))));
                }
            }

            @Override
            public DisplayValue tuple(List<DataType> inner) throws InternalException, UserException
            {
                @Value Object @Value[] array = (@Value Object @Value[])object;
                if (array.length != inner.size())
                    throw new InternalException("Tuple type does not match value, expected: " + inner.size() + " got " + array.length);
                List<DisplayValue> innerDisplay = new ArrayList<>();
                for (int i = 0; i < array.length; i++)
                {
                    innerDisplay.add(display(rowIndex, inner.get(i), array[i]));
                }
                return DisplayValue.tuple(rowIndex, innerDisplay);
            }

            @Override
            public DisplayValue array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return DisplayValue.array(rowIndex, Collections.emptyList());
                @NonNull DataType innerFinal = inner;
                List<@Value Object> list = (List<@Value Object>)object;
                return DisplayValue.array(rowIndex, Utility.mapListEx(list, o -> display(rowIndex, innerFinal, o)));
            }
        });
    }

    @OnThread(Tag.Simulation)
    public static ColumnStorage<?> makeColumnStorage(final DataType inner, @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet) throws InternalException
    {
        return inner.apply(new DataTypeVisitorEx<ColumnStorage<?>, InternalException>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumericColumnStorage(displayInfo, beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> bool() throws InternalException
            {
                return new BooleanColumnStorage(beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> text() throws InternalException
            {
                return new StringColumnStorage(beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return new TemporalColumnStorage(dateTimeInfo, beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException
            {
                return new TaggedColumnStorage(typeName, tags, beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tuple(List<DataType> innerTypes) throws InternalException
            {
                return new TupleColumnStorage(innerTypes, beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> array(@Nullable DataType inner) throws InternalException
            {
                return new ArrayColumnStorage(inner, beforeGet);
            }
        });
    }

    public static @Value int requireInteger(@Value Object o) throws UserException, InternalException
    {
        return Utility.<@Value Integer>withNumber(o, l -> {
            if (l.longValue() != l.intValue())
                throw new UserException("Number too large: " + l);
            return value(l.intValue());
        }, bd -> {
            try
            {
                return value(bd.intValueExact());
            }
            catch (ArithmeticException e)
            {
                throw new UserException("Number not an integer or too large: " + bd);
            }
        });
    }

    @SuppressWarnings("userindex")
    public static @UserIndex @Value int userIndex(@Value Object value) throws InternalException, UserException
    {
        @Value int integer = requireInteger(value);
        return integer;
    }

    @SuppressWarnings("valuetype")
    public static <T extends Number> @Value T value(@UnknownIfValue T number)
    {
        return number;
    }

    @SuppressWarnings("valuetype")
    public static @Value Boolean value(@UnknownIfValue Boolean bool)
    {
        return bool;
    }

    @SuppressWarnings("valuetype")
    public static @Value String value(@UnknownIfValue String string)
    {
        return string;
    }

    @SuppressWarnings("valuetype")
    public static @Value TemporalAccessor value(DateTimeInfo dest, @UnknownIfValue TemporalAccessor t)
    {
        switch (dest.getType())
        {
            case YEARMONTHDAY:
                if (t instanceof LocalDate)
                    return t;
                else
                    return LocalDate.from(t);
            case YEARMONTH:
                if (t instanceof YearMonth)
                    return t;
                else
                    return YearMonth.from(t);
            case TIMEOFDAY:
                if (t instanceof LocalTime)
                    return t;
                else
                    return LocalTime.from(t);
            case TIMEOFDAYZONED:
                if (t instanceof OffsetTime)
                    return t;
                else
                    return OffsetTime.from(t);
            case DATETIME:
                if (t instanceof LocalDateTime)
                    return t;
                else
                    return LocalDateTime.from(t);
            case DATETIMEZONED:
                if (t instanceof ZonedDateTime)
                    return t;
                else
                    return ZonedDateTime.from(t);
        }
        return t;
    }

    @SuppressWarnings("valuetype")
    public static @Value Object @Value [] value(@Value Object [] tuple)
    {
        return tuple;
    }

    @SuppressWarnings("valuetype")
    public static Utility.@Value ListEx value(Utility.@UnknownIfValue ListEx list)
    {
        return list;
    }

    @SuppressWarnings("valuetype")
    public static Utility.@Value ListEx value(@UnknownIfValue List<@Value ? extends Object> list)
    {
        return new ListEx()
        {
            @Override
            public int size() throws InternalException, UserException
            {
                return list.size();
            }

            @Override
            public @Value Object get(int index) throws InternalException, UserException
            {
                return list.get(index);
            }
        };
    }

    public static DisplayValue toDisplayValue(int rowIndex, @Value Object o)
    {
        if (o instanceof Boolean)
            return new DisplayValue(rowIndex, (Boolean)o);
        else if (o instanceof Number)
            return new DisplayValue(rowIndex, (Number)o, Unit.SCALAR, 0);
        else if (o instanceof String)
            return new DisplayValue(rowIndex, (String)o);
        throw new RuntimeException("Unexpected toDisplayValue type: " + o.getClass());
    }
}
