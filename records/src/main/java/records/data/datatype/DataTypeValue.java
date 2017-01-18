package records.data.datatype;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.TaggedValue;
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
    private final @Nullable GetValue<@Value Number> getNumber;
    private final @Nullable GetValue<@Value String> getText;
    private final @Nullable GetValue<@Value TemporalAccessor> getDate;
    private final @Nullable GetValue<@Value Boolean> getBoolean;
    private final @Nullable GetValue<Integer> getTag;
    // Returns the length of the array at that index and accessor:
    private final @Nullable GetValue<Pair<Integer, DataTypeValue>> getArrayContent;

    // package-visible
    @SuppressWarnings("unchecked")
    DataTypeValue(Kind kind, @Nullable NumberInfo numberInfo, @Nullable DateTimeInfo dateTimeInfo, @Nullable Pair<TypeId, List<TagType<DataTypeValue>>> tagTypes, @Nullable List<DataType> memberTypes, @Nullable GetValue<@Value Number> getNumber, @Nullable GetValue<@Value String> getText, @Nullable GetValue<@Value TemporalAccessor> getDate, @Nullable GetValue<@Value Boolean> getBoolean, @Nullable GetValue<Integer> getTag, @Nullable GetValue<Pair<Integer, DataTypeValue>> getArrayContent)
    {
        super(kind, numberInfo, dateTimeInfo, (Pair<TypeId, List<TagType<DataType>>>)(Pair)tagTypes, memberTypes);
        this.getNumber = getNumber;
        this.getText = getText;
        this.getDate = getDate;
        this.getBoolean = getBoolean;
        this.getTag = getTag;
        this.getArrayContent = getArrayContent;
    }

    public static DataTypeValue bool(GetValue<@Value Boolean> getValue)
    {
        return new DataTypeValue(Kind.BOOLEAN, null, null, null, null, null, null, null, getValue, null, null);
    }

    public static DataTypeValue tagged(TypeId name, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> getTag)
    {
        return new DataTypeValue(Kind.TAGGED, null, null, new Pair<>(name, tagTypes), null, null, null, null, null, getTag, null);
    }

    public static DataTypeValue text(GetValue<@Value String> getText)
    {
        return new DataTypeValue(Kind.TEXT, null, null, null, null, null, getText, null, null, null, null);
    }

    public static DataTypeValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> getDate)
    {
        return new DataTypeValue(Kind.DATETIME, null, dateTimeInfo, null, null, null, null, getDate, null, null, null);
    }

    public static DataTypeValue number(NumberInfo numberInfo, GetValue<@Value Number> getNumber)
    {
        return new DataTypeValue(Kind.NUMBER, numberInfo, null, null, null, getNumber, null, null, null, null, null);
    }

    public static DataTypeValue arrayV()
    {
        return new DataTypeValue(Kind.ARRAY, null, null, null, Collections.emptyList(), null, null, null, null, null, null);
    }

    public static DataTypeValue arrayV(DataType innerType, GetValue<Pair<Integer, DataTypeValue>> getContent)
    {
        return new DataTypeValue(Kind.ARRAY, null, null, null, Collections.singletonList(innerType), null, null, null, null, null, getContent);
    }

    public static DataTypeValue tupleV(List<DataTypeValue> types)
    {
        return new DataTypeValue(Kind.TUPLE, null, null, null, new ArrayList<>(types), null, null, null, null, null, null);
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
        public R number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
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
        public R text(GetValue<@Value String> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected text data type");
        }

        @Override
        public R tagged(TypeId typeName, List<TagType<DataTypeValue>> tags, GetValue<Integer> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected tagged data type");
        }

        @Override
        public R bool(GetValue<@Value Boolean> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected boolean type");
        }

        @Override
        public R date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected date type");
        }

        @Override
        public R tuple(List<DataTypeValue> types) throws InternalException, UserException
        {
            return defaultOp("Unexpected tuple type");
        }

        @Override
        public R array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected array type");
        }
    }

    @OnThread(Tag.Simulation)
    public static interface DataTypeVisitorGetEx<R, E extends Throwable>
    {
        R number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, E;
        R text(GetValue<@Value String> g) throws InternalException, E;
        R bool(GetValue<@Value Boolean> g) throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, E;

        R tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, E;
        R tuple(List<DataTypeValue> types) throws InternalException, E;

        // Each item is a pair of size and accessor.  The inner type gives the type
        // of each entry (but is null when the array is empty)
        R array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, E;
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
                DataType arrayType = memberType.get(0);
                return visitor.array(arrayType, getArrayContent);
            default:
                throw new InternalException("Missing kind case");
        }
    }
/*
    public int getArrayLength(int index) throws InternalException, UserException
    {
        if (getArrayLength == null)
            throw new InternalException("Trying to get array length of non-array: " + this);
        return getArrayLength.apply(index);
    }*/

    public static interface GetValue<T>
    {
        @OnThread(Tag.Simulation)
        @NonNull T getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException;
        @OnThread(Tag.Simulation)
        default @NonNull T get(int index) throws UserException, InternalException { return getWithProgress(index, null); }
    }


    /**
     * Gets the collapsed, dynamically typed value at the given index
     *
     * Number: Byte/Short/Integer/Long/BigInteger/BigDecimal
     * Text: String
     * Boolean: Boolean
     * Datetime: LocalDate/LocalTime/ZonedDateTime/....
     * Tagged type: TaggedValue
     * Tuple: array
     * Array: List
     */
    @OnThread(Tag.Simulation)
    public final @Value Object getCollapsed(int index) throws InternalException, UserException
    {
        return applyGet(new DataTypeVisitorGet<@Value Object>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public @Value Object number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object text(GetValue<@Value String> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                Integer tagIndex = g.get(index);
                @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();;
                return new TaggedValue(tagIndex, inner == null ? null : inner.applyGet(this));
            }

            @Override
            public @Value Object tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                @Value Object [] array = new Object[types.size()];
                for (int i = 0; i < types.size(); i++)
                {
                    array[i] = types.get(i).applyGet(this);
                }
                return Utility.value(array);
            }

            @Override
            public @Value Object array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                List<@Value Object> l = new ArrayList<>();
                @NonNull Pair<Integer, DataTypeValue> details = g.get(index);
                for (int indexInArray = 0; indexInArray < details.getFirst(); indexInArray++)
                {
                    // Need to look for indexInArray, not index, to get full list:
                    l.add(details.getSecond().getCollapsed(indexInArray));
                }
                return Utility.value(l);
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                return g.get(index);
            }
        });
    }

    // Copies the type of this item, but allows you to pull the data from an arbitrary DataTypeValue
    // (i.e. not necessarily this one).  Useful for implementing concat and similar.
    public static DataTypeValue copySeveral(DataType original, GetValue<Pair<DataTypeValue, Integer>> getOriginalValueAndIndex) throws InternalException
    {
        Pair<TypeId, List<TagType<DataTypeValue>>> newTagTypes = null;
        if (original.tagTypes != null && original.taggedTypeName != null)
        {
            newTagTypes = new Pair<>(original.taggedTypeName, new ArrayList<>());
            for (int tagIndex = 0; tagIndex < original.tagTypes.size(); tagIndex++)
            {
                TagType t = original.tagTypes.get(tagIndex);
                int tagIndexFinal = tagIndex;
                DataType inner = t.getInner();
                if (inner == null)
                    newTagTypes.getSecond().add(new TagType<>(t.getName(), null));
                else
                    newTagTypes.getSecond().add(new TagType<>(t.getName(), ((DataTypeValue) inner).copySeveral(inner, (i, prog) ->
                    {
                        @NonNull Pair<DataTypeValue, Integer> destinationParent = getOriginalValueAndIndex.get(i);
                        @Nullable List<TagType<DataType>> destinationTagTypes = destinationParent.getFirst().tagTypes;
                        if (destinationTagTypes == null)
                            throw new InternalException("Joining together columns but other column not tagged");
                        DataTypeValue innerDTV = (DataTypeValue) destinationTagTypes.get(tagIndexFinal).getInner();
                        if (innerDTV == null)
                            throw new InternalException("Joining together columns but tag types don't match across types");
                        return new Pair<DataTypeValue, Integer>(innerDTV, destinationParent.getSecond());
                    })));
            }
        }

        final @Nullable List<DataType> memberTypes;
        if (original.memberType == null)
            memberTypes = null;
        else
        {
            ArrayList<DataType> r = new ArrayList<>(original.memberType.size());
            // TODO not sure if this bit is right:
            for (DataType type : original.memberType)
                r.add(((DataTypeValue)type).copySeveral(type, getOriginalValueAndIndex));
            memberTypes = r;
        }
        return new DataTypeValue(original.kind, original.numberInfo, original.dateTimeInfo, newTagTypes,
            memberTypes,
            DataTypeValue.<@Value Number>several(getOriginalValueAndIndex, dtv -> dtv.getNumber),
            DataTypeValue.<@Value String>several(getOriginalValueAndIndex, dtv -> dtv.getText),
            DataTypeValue.<@Value TemporalAccessor>several(getOriginalValueAndIndex, dtv -> dtv.getDate),
            DataTypeValue.<@Value Boolean>several(getOriginalValueAndIndex, dtv -> dtv.getBoolean),
            several(getOriginalValueAndIndex, dtv -> dtv.getTag),
            several(getOriginalValueAndIndex, dtv -> dtv.getArrayContent));

    }

    public DataTypeValue copyReorder(GetValue<Integer> getOriginalIndex) throws InternalException
    {
        return copySeveral(this, (i, prog) -> new Pair<DataTypeValue, Integer>(this, getOriginalIndex.getWithProgress(i, prog)));
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

    private static <T> @Nullable GetValue<T> several(GetValue<Pair<DataTypeValue, Integer>> getOriginalIndex, @Nullable Function<DataTypeValue, @Nullable GetValue<T>> g)
    {
        if (g == null)
            return null;
        @NonNull Function<DataTypeValue, @Nullable GetValue<T>> gFinal = g;
        return (int destIndex, final @Nullable ProgressListener prog) -> {
            @OnThread(Tag.Simulation) @NonNull Pair<DataTypeValue, Integer> src = getOriginalIndex.getWithProgress(destIndex, prog);
            @Nullable GetValue<T> innerGet = gFinal.apply(src.getFirst());
            if (innerGet == null)
                throw new InternalException("Inner get in several was null");
            return innerGet.getWithProgress(src.getSecond(), prog == null ? null : prog);
        };
    }
}
