package records.data.datatype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * The data-type of a homogeneously-typed list of values,
 * including a facility to get the item at a particular index.
 */
public class DataTypeValue extends DataType
{
    private final @Nullable GetValue<Number> getNumber;
    private final @Nullable GetValue<String> getText;
    private final @Nullable GetValue<TemporalAccessor> getDate;
    private final @Nullable GetValue<Boolean> getBoolean;
    private final @Nullable GetValue<Integer> getTag;
    // Takes index of item, returns the length of the array at that index.
    private final @Nullable ExFunction<Integer, Integer> getArrayLength;

    // package-visible
    @SuppressWarnings("unchecked")
    DataTypeValue(Kind kind, @Nullable NumberInfo numberInfo, @Nullable DateTimeInfo dateTimeInfo, @Nullable Pair<TypeId, List<TagType<DataTypeValue>>> tagTypes, @Nullable List<DataType> memberTypes, @Nullable GetValue<Number> getNumber, @Nullable GetValue<String> getText, @Nullable GetValue<TemporalAccessor> getDate, @Nullable GetValue<Boolean> getBoolean, @Nullable GetValue<Integer> getTag, @Nullable ExFunction<Integer, Integer> getArrayLength)
    {
        super(kind, numberInfo, dateTimeInfo, (Pair<TypeId, List<TagType<DataType>>>)(Pair)tagTypes, memberTypes);
        this.getNumber = getNumber;
        this.getText = getText;
        this.getDate = getDate;
        this.getBoolean = getBoolean;
        this.getTag = getTag;
        this.getArrayLength = getArrayLength;
    }

    public static DataTypeValue bool(GetValue<Boolean> getValue)
    {
        return new DataTypeValue(Kind.BOOLEAN, null, null, null, null, null, null, null, getValue, null, null);
    }

    public static DataTypeValue tagged(TypeId name, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> getTag)
    {
        return new DataTypeValue(Kind.TAGGED, null, null, new Pair<>(name, tagTypes), null, null, null, null, null, getTag, null);
    }

    public static DataTypeValue text(GetValue<String> getText)
    {
        return new DataTypeValue(Kind.TEXT, null, null, null, null, null, getText, null, null, null, null);
    }

    public static DataTypeValue date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> getDate)
    {
        return new DataTypeValue(Kind.DATETIME, null, dateTimeInfo, null, null, null, null, getDate, null, null, null);
    }

    public static DataTypeValue number(NumberInfo numberInfo, GetValue<Number> getNumber)
    {
        return new DataTypeValue(Kind.NUMBER, numberInfo, null, null, null, getNumber, null, null, null, null, null);
    }

    public static DataTypeValue array(DataType innerType, ExFunction<Integer, Integer> getLength)
    {
        return new DataTypeValue(Kind.ARRAY, null, null, null, Collections.singletonList(innerType), null, null, null, null, null, getLength);
    }

    public static DataTypeValue tupleV(List<DataTypeValue> types)
    {
        return new DataTypeValue(Kind.TUPLE, null, null, null, null, null, null, null, null, null, null);
    }

    public static class SpecificDataTypeVisitorGet<R> implements DataTypeVisitorGet<R>
    {
        private final @Nullable InternalException internal;
        private final @Nullable UserException user;
        private final @Nullable R value;

        public SpecificDataTypeVisitorGet(InternalException e)
        {
            this.internal = e;
            this.user = null;
            this.value = null;
        }

        public SpecificDataTypeVisitorGet(UserException e)
        {
            this.internal = null;
            this.user = e;
            this.value = null;
        }

        public SpecificDataTypeVisitorGet(R value)
        {
            this.value = value;
            this.internal = null;
            this.user = null;
        }

        @Override
        public R number(GetValue<Number> g, NumberInfo displayInfo) throws InternalException, UserException
        {
            return defaultOp("Unexpected number data type");
        }

        private R defaultOp(String msg) throws InternalException, UserException
        {
            if (internal != null)
                throw internal;
            if (user != null)
                throw user;
            if (value != null)
                return value;
            throw new InternalException(msg);
        }

        @Override
        public R text(GetValue<String> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected text data type");
        }

        @Override
        public R tagged(TypeId typeName, List<TagType<DataTypeValue>> tags, GetValue<Integer> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected tagged data type");
        }

        @Override
        public R bool(GetValue<Boolean> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected boolean type");
        }

        @Override
        public R date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected date type");
        }

        @Override
        public R tuple(List<DataTypeValue> types) throws InternalException, UserException
        {
            return defaultOp("Unexpected tuple type");
        }

        @Override
        public R array(ExFunction<Integer, Integer> size, DataTypeValue type) throws InternalException, UserException
        {
            return defaultOp("Unexpected array type");
        }
    }

    @OnThread(Tag.Simulation)
    public static interface DataTypeVisitorGetEx<R, E extends Throwable>
    {
        R number(GetValue<Number> g, NumberInfo displayInfo) throws InternalException, E;
        R text(GetValue<String> g) throws InternalException, E;
        R bool(GetValue<Boolean> g) throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> g) throws InternalException, E;

        R tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, E;
        R tuple(List<DataTypeValue> types) throws InternalException, E;

        R array(ExFunction<Integer, Integer> size, DataTypeValue type) throws InternalException, E;
    }

    @OnThread(Tag.Simulation)
    public static interface DataTypeVisitorGet<R> extends DataTypeVisitorGetEx<R, UserException>
    {
        
    }
    

    @SuppressWarnings({"nullness", "unchecked"})
    @OnThread(Tag.Simulation)
    public final <R> R applyGet(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
    {
        switch (kind)
        {
            case NUMBER:
                return visitor.number(getNumber, numberInfo);
            case TEXT:
                return visitor.text(getText);
            case DATETIME:
                return visitor.date(dateTimeInfo, getDate);
            case BOOLEAN:
                return visitor.bool(getBoolean);
            case TAGGED:
                return visitor.tagged(taggedTypeName, (List<TagType<DataTypeValue>>)(List)tagTypes, getTag);
            case TUPLE:
                return visitor.tuple((List<DataTypeValue>)(List)memberType);
            case ARRAY:
                DataTypeValue arrayType = (DataTypeValue) memberType.get(0);
                return visitor.array(arrayType.getArrayLength, arrayType);
            default:
                throw new InternalException("Missing kind case");
        }
    }

    public int getArrayLength(int index) throws InternalException, UserException
    {
        if (getArrayLength == null)
            throw new InternalException("Trying to get array length of non-array: " + this);
        return getArrayLength.apply(index);
    }

    public static interface GetValue<T>
    {
        @OnThread(Tag.Simulation)
        @NonNull T getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException;
        @OnThread(Tag.Simulation)
        @NonNull default T get(int index) throws UserException, InternalException { return getWithProgress(index, null); }
    }


    /**
     * Gets the collapsed, dynamically typed value at the given index
     *
     * Number: Byte/Short/Integer/Long/BigInteger/BigDecimal
     * Text: String
     * Boolean: Boolean
     * Datetime: LocalDate/LocalTime/ZonedDateTime/....
     * Tagged type: Pair<Integer, Object>
     * Tuple: array
     * Array: List
     */
    @OnThread(Tag.Simulation)
    public final Object getCollapsed(int index) throws InternalException, UserException
    {
        return applyGet(new DataTypeVisitorGet<Object>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public Object number(GetValue<Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Object text(GetValue<String> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Object tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                Integer tagIndex = g.get(index);
                @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();;
                return new Pair<Integer, @Nullable Object>(tagIndex, inner == null ? null : inner.applyGet(this));
            }

            @Override
            public Object tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                Object[] array = new Object[types.size()];
                for (int i = 0; i < types.size(); i++)
                {
                    array[i] = types.get(i).applyGet(this);
                }
                return array;
            }

            @Override
            public Object array(ExFunction<Integer, Integer> size, DataTypeValue type) throws InternalException, UserException
            {
                List<Object> l = new ArrayList<>();
                Integer theSize = size.apply(index);
                for (int i = 0; i < theSize; i++)
                {
                    // Need to look for i, not index, to get full list:
                    l.add(getCollapsed(i));
                }
                return l;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Object bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Object date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> g) throws InternalException, UserException
            {
                return g.get(index);
            }
        });
    }

    public DataTypeValue copyReorder(GetValue<Integer> getOriginalIndex) throws UserException, InternalException
    {
        Pair<TypeId, List<TagType<DataTypeValue>>> newTagTypes = null;
        if (this.tagTypes != null && this.taggedTypeName != null)
        {
            newTagTypes = new Pair<>(taggedTypeName, new ArrayList<>());
            for (TagType t : tagTypes)
                newTagTypes.getSecond().add(new TagType<>(t.getName(), t.getInner() == null ? null : ((DataTypeValue)t.getInner()).copyReorder(getOriginalIndex)));
        }


        return new DataTypeValue(kind, numberInfo, dateTimeInfo, newTagTypes,
            memberType == null ? null : Utility.mapListEx(memberType, t -> ((DataTypeValue)t).copyReorder(getOriginalIndex)),
            reOrder(getOriginalIndex, getNumber),
            reOrder(getOriginalIndex, getText),
            reOrder(getOriginalIndex, getDate),
            reOrder(getOriginalIndex, getBoolean),
            reOrder(getOriginalIndex, getTag), null);
    }

    @SuppressWarnings("nullness")
    private static <T> @Nullable GetValue<T> reOrder(GetValue<Integer> getOriginalIndex, @Nullable GetValue<T> g)
    {
        if (g == null)
            return null;
        return (int destIndex, final ProgressListener prog) -> {
            int srcIndex = getOriginalIndex.getWithProgress(destIndex, prog == null ? null : (d -> prog.progressUpdate(d*0.5)));
            return g.getWithProgress(srcIndex, prog == null ? null : (d -> prog.progressUpdate(d * 0.5 + 0.5)));
        };
    }

    public static <T, R> GetValue<R> mapValue(GetValue<T> g, Function<T, @NonNull R> map)
    {
        return (i, prog) -> map.apply(g.getWithProgress(i, prog));
    }

}
