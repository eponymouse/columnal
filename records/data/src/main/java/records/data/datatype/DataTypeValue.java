package records.data.datatype;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * The data-type of a homogeneously-typed list of values,
 * including a facility to get the item at a particular index.
 */
public final class DataTypeValue extends DataType
{
    private final @Nullable GetValue<@Value Number> getNumber;
    private final @Nullable GetValue<@Value String> getText;
    private final @Nullable GetValue<@Value TemporalAccessor> getDate;
    private final @Nullable GetValue<@Value Boolean> getBoolean;
    private final @Nullable GetValue<@Value TaggedValue> getTag;
    private final @Nullable GetValue<@Value Object @Value[]> getTuple;
    private final @Nullable GetValue<@Value ListEx> getArrayContent;

    // package-visible
    // @SuppressWarnings("unchecked")
    DataTypeValue(Kind kind, @Nullable NumberInfo numberInfo, @Nullable DateTimeInfo dateTimeInfo, @Nullable TagTypeDetails tagTypes, @Nullable List<DataType> memberTypes, @Nullable GetValue<@Value Number> getNumber, @Nullable GetValue<@Value String> getText, @Nullable GetValue<@Value TemporalAccessor> getDate, @Nullable GetValue<@Value Boolean> getBoolean, @Nullable GetValue<@Value TaggedValue> getTag, @Nullable GetValue<@Value Object @Value []> getTuple, @Nullable GetValue<@Value ListEx> getArrayContent)
    {
        super(kind, numberInfo, dateTimeInfo, tagTypes, memberTypes);
        this.getNumber = getNumber;
        this.getText = getText;
        this.getDate = getDate;
        this.getBoolean = getBoolean;
        this.getTag = getTag;
        this.getTuple = getTuple;
        this.getArrayContent = getArrayContent;
    }
    
    public static DataTypeValue bool(GetValue<@Value Boolean> getValue)
    {
        return new DataTypeValue(Kind.BOOLEAN, null, null, null, null, null, null, null, getValue, null, null, null);
    }

    public static DataTypeValue tagged(TypeId name, ImmutableList<Either<Unit, DataType>> tagTypeVariableSubsts, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> getTag)
    {
        return new DataTypeValue(Kind.TAGGED, null, null, new TagTypeDetails(name, tagTypeVariableSubsts, tagTypes.stream().map(tt -> tt.<DataType>map(x -> x)).collect(ImmutableList.<TagType<DataType>>toImmutableList())), null, null, null, null, null, getTag, null, null);
    }

    public static DataTypeValue text(GetValue<@Value String> getText)
    {
        return new DataTypeValue(Kind.TEXT, null, null, null, null, null, getText, null, null, null, null, null);
    }

    public static DataTypeValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> getDate)
    {
        return new DataTypeValue(Kind.DATETIME, null, dateTimeInfo, null, null, null, null, getDate, null, null, null, null);
    }

    public static DataTypeValue number(NumberInfo numberInfo, GetValue<@Value Number> getNumber)
    {
        return new DataTypeValue(Kind.NUMBER, numberInfo, null, null, null, getNumber, null, null, null, null, null, null);
    }

    public static DataTypeValue arrayV(DataType innerType, GetValue<@Value ListEx> getContent)
    {
        return new DataTypeValue(Kind.ARRAY, null, null, null, Collections.singletonList(innerType), null, null, null, null, null, null, getContent);
    }

    public static DataTypeValue tuple(List<DataType> types, GetValue<@Value Object @Value[]> getContent)
    {
        return new DataTypeValue(Kind.TUPLE, null, null, null, new ArrayList<>(types), null, null, null, null, null, getContent, null);
    }

    public void setCollapsed(int rowIndex, Either<String, @Value Object> value) throws InternalException, UserException
    {
        applyGet(new DataTypeVisitorGet<Void>()
        {
            @SuppressWarnings("value")
            @OnThread(Tag.Simulation)
            private <T> void set(GetValue<@Value T> g, Class<T> castTo) throws UserException, InternalException
            {
                g.set(rowIndex, value.<@Value T>map(v -> castTo.cast(v)));
            }
            
            @Override
            @OnThread(Tag.Simulation)
            public Void number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                set(g, Number.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void text(GetValue<@Value String> g) throws InternalException, UserException
            {
                set(g, String.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                set(g, Boolean.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                set(g, TemporalAccessor.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
            {
                set(g, TaggedValue.class);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value []> g) throws InternalException, UserException
            {
                g.set(rowIndex, value.<@Value Object @Value []>mapInt(v -> Utility.castTuple(v, types.size())));
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
            {
                set(g, ListEx.class);
                return null;
            }
        });
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
        public R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags, GetValue<@Value TaggedValue> g) throws InternalException, UserException
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
        public R tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value []> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected tuple type");
        }

        @Override
        public R array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected array type");
        }
    }

    public static interface DataTypeVisitorGetEx<R, E extends Throwable>
    {
        R number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, E;
        R text(GetValue<@Value String> g) throws InternalException, E;
        R bool(GetValue<@Value Boolean> g) throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, E;

        R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, E;
        R tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value[]> g) throws InternalException, E;

        // Each item is an array.  The inner type gives the type
        // of each entry
        R array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, E;
    }

    public static interface DataTypeVisitorGet<R> extends DataTypeVisitorGetEx<R, UserException>
    {
        
    }
    

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    public final <R, E extends Throwable> R applyGet(DataTypeVisitorGetEx<R, E> visitor) throws InternalException, E
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
                return visitor.tagged(taggedTypeName, tagTypeVariableSubstitutions, tagTypes, getTag);
            case TUPLE:
                return visitor.tuple(memberType, getTuple);
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

    public static interface GetValue<@Value T>
    {
        @OnThread(Tag.Simulation)
        @NonNull @Value T getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException;

        @OnThread(Tag.Simulation)
        default @NonNull @Value T get(int index) throws UserException, InternalException { return getWithProgress(index, null); }

        @OnThread(Tag.Simulation)
        default void set(int index, Either<String, @Value T> value) throws InternalException, UserException
        {
            throw new InternalException("Attempted to set value for uneditable column: " + getClass());
        };
    }


    /**
     * Gets the collapsed, dynamically typed value at the given index
     *
     * Number: Byte/Short/Integer/Long/BigDecimal
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
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object text(GetValue<@Value String> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value[] >g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                return g.get(index);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Value Object date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                return g.get(index);
            }
        });
    }

    /**
     * Copies the type of this item, but allows you to pull the data from an arbitrary DataTypeValue
     * (i.e. not necessarily the original).  Useful for implementing concat, sort and similar, as it
     * allows arbitrary mapping of an index back to any index of any DataTypeValue.
     *
     * @param original The original type which we are copying from
     * @param getOriginalValueAndIndex A function which takes the final index which we are interested
     *                                 in and gives back the DataTypeValue to draw from, and the index
     *                                 to ask for that in that DataTypeValue.  Any DataTypeValue returned
     *                                 must be of the same type as original.
     */
    public static DataTypeValue copySeveral(DataType original, SimulationFunction<Integer, Pair<DataTypeValue, Integer>> getOriginalValueAndIndex) throws InternalException
    {
        TagTypeDetails newTagTypes = null;
        if (original.tagTypes != null && original.tagTypeVariableSubstitutions != null && original.taggedTypeName != null)
        {
            ImmutableList.Builder<TagType<DataType>> tagTypes = ImmutableList.builder();
            for (int tagIndex = 0; tagIndex < original.tagTypes.size(); tagIndex++)
            {
                TagType<DataType> t = original.tagTypes.get(tagIndex);
                int tagIndexFinal = tagIndex;
                DataType inner = t.getInner();
                if (inner == null)
                    tagTypes.add(new TagType<>(t.getName(), null));
                else
                    tagTypes.add(new TagType<>(t.getName(), ((DataTypeValue) inner).copySeveral(inner, i ->
                    {
                        @NonNull Pair<DataTypeValue, Integer> destinationParent = getOriginalValueAndIndex.apply(i);
                        @Nullable List<TagType<DataType>> destinationTagTypes = destinationParent.getFirst().tagTypes;
                        if (destinationTagTypes == null)
                            throw new InternalException("Joining together columns but other column not tagged");
                        DataTypeValue innerDTV = (DataTypeValue) destinationTagTypes.get(tagIndexFinal).getInner();
                        if (innerDTV == null)
                            throw new InternalException("Joining together columns but tag types don't match across types");
                        return new Pair<DataTypeValue, Integer>(innerDTV, destinationParent.getSecond());
                    })));
            }
            newTagTypes = new TagTypeDetails(original.taggedTypeName, original.tagTypeVariableSubstitutions, tagTypes.build());
        }

        final @Nullable List<DataType> memberTypes;
        if (original.memberType == null)
            memberTypes = null;
        else
        {
            ArrayList<DataType> r = new ArrayList<>(original.memberType.size());
            // If it's a tuple, we should map each member element across
            if (original.isTuple())
            {
                for (int memberTypeIndex = 0; memberTypeIndex < original.memberType.size(); memberTypeIndex++)
                {
                    DataType type = original.memberType.get(memberTypeIndex);
                    int memberTypeIndexFinal = memberTypeIndex;
                    r.add(((DataTypeValue) type).copySeveral(type, i -> getOriginalValueAndIndex.apply(i).mapFirstEx(dtv -> {
                        if (dtv.memberType == null)
                            throw new InternalException("copySeveral: original " + original + " had memberType but given target does not: " + dtv);
                        return (DataTypeValue)dtv.memberType.get(memberTypeIndexFinal);
                    })));
                }
            }
            else
            {
                // If it's an array, just keep the original inner type:
                r.addAll(original.memberType);
            }
            memberTypes = r;
        }
        return new DataTypeValue(original.kind, original.numberInfo, original.dateTimeInfo, newTagTypes,
            memberTypes,
            DataTypeValue.<@Value Number>several(getOriginalValueAndIndex, dtv -> dtv.getNumber),
            DataTypeValue.<@Value String>several(getOriginalValueAndIndex, dtv -> dtv.getText),
            DataTypeValue.<@Value TemporalAccessor>several(getOriginalValueAndIndex, dtv -> dtv.getDate),
            DataTypeValue.<@Value Boolean>several(getOriginalValueAndIndex, dtv -> dtv.getBoolean),
            several(getOriginalValueAndIndex, dtv -> dtv.getTag),
                DataTypeValue.<@Value Object @Value[]>several(getOriginalValueAndIndex, dtv -> dtv.getTuple),
            several(getOriginalValueAndIndex, dtv -> dtv.getArrayContent));

    }

    public DataTypeValue copyReorder(GetValue<Integer> getOriginalIndex) throws InternalException
    {
        return copySeveral(this, i -> new Pair<DataTypeValue, Integer>(this, getOriginalIndex.getWithProgress(i, null)));
    }

    private static <T> @Nullable GetValue<@Value T> several(SimulationFunction<Integer, Pair<DataTypeValue, Integer>> getOriginalIndex, @Nullable Function<DataTypeValue, @Nullable GetValue<@Value T>> g)
    {
        if (g == null)
            return null;
        @NonNull Function<DataTypeValue, @Nullable GetValue<@Value T>> gFinal = g;
        return (int destIndex, final @Nullable ProgressListener prog) -> {
            @OnThread(Tag.Simulation) @NonNull Pair<DataTypeValue, Integer> src = getOriginalIndex.apply(destIndex);
            @Nullable GetValue<@Value T> innerGet = gFinal.apply(src.getFirst());
            if (innerGet == null)
                throw new InternalException("Inner get in several was null");
            return innerGet.getWithProgress(src.getSecond(), prog == null ? null : prog);
        };
    }
    
    public static interface OverrideSet
    {
        @OnThread(Tag.Simulation)
        public void set(int index, Either<String, @Value Object> value);
    }

    /**
     * A copy of this DataTypeValue with the given set operation
     */
    public DataTypeValue withSet(OverrideSet set) throws InternalException
    {
        return applyGet(new DataTypeVisitorGetEx<DataTypeValue, InternalException>()
        {
            private <@NonNull @Value T> GetValue<@NonNull @Value T> overrideSet(GetValue<@NonNull @Value T> g)
            {
                return new GetValue<@NonNull @Value T>()
                {
                    @Override
                    public @NonNull @Value T getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        return g.getWithProgress(index, progressListener);
                    }

                    @Override
                    // @SuppressWarnings("nullness") // I guess checker thinks T could be @Nullable
                    public @OnThread(Tag.Simulation) void set(int index, Either<String, @NonNull @Value T> value) throws InternalException, UserException
                    {
                        set.set(index, value.<@Value Object>map(t -> t));
                    }
                };
            }
            
            @Override
            public DataTypeValue number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException
            {
                return DataTypeValue.number(displayInfo, overrideSet(g));
            }

            @Override
            public DataTypeValue text(GetValue<@Value String> g) throws InternalException
            {
                return DataTypeValue.text(overrideSet(g));
            }

            @Override
            public DataTypeValue bool(GetValue<@Value Boolean> g) throws InternalException
            {
                return DataTypeValue.bool(overrideSet(g));
            }

            @Override
            public DataTypeValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
            {
                return DataTypeValue.date(dateTimeInfo, overrideSet(g));
            }

            @Override
            public DataTypeValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException
            {
                return DataTypeValue.tagged(typeName, typeVars, tagTypes, overrideSet(g));
            }

            @Override
            public DataTypeValue tuple(ImmutableList<DataType> types, GetValue<@Value Object @Value[]> g) throws InternalException
            {
                return DataTypeValue.tuple(types, overrideSet(g));
            }

            @Override
            public DataTypeValue array(DataType inner, GetValue<@Value ListEx> g) throws InternalException
            {
                return DataTypeValue.arrayV(inner, overrideSet(g));
            }
        });
    }
}
