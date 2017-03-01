package records.data.datatype;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.ArrayColumnStorage;
import records.data.BooleanColumnStorage;
import records.data.CachedCalculatedColumn;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.ColumnStorage;
import records.data.MemoryArrayColumn;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.MemoryTupleColumn;
import records.data.NumericColumnStorage;
import records.data.RecordSet;
import records.data.StringColumnStorage;
import records.data.TaggedColumnStorage;
import records.data.TaggedValue;
import records.data.TemporalColumnStorage;
import records.data.TupleColumnStorage;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.unit.Unit;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.DataParser;
import records.grammar.DataParser.ArrayContext;
import records.grammar.DataParser.BoolContext;
import records.grammar.DataParser.ItemContext;
import records.grammar.DataParser.StringContext;
import records.grammar.DataParser.TaggedContext;
import records.grammar.DataParser.TupleContext;
import records.grammar.FormatLexer;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.ExConsumer;
import utility.ExFunction;
import utility.Pair;
import utility.UnitType;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

/**
 * A data type can be the following:
 *
 *  - A built-in/primitive type:
 *    - A number.  This has a small bit of dynamic typing: it may be
 *      integers or decimals, but this is a performance optimisation
 *      not a user-visible difference.
 *    - A string.
 *    - A date.
 *  - A composite type:
 *    - A set of 2+ tags.  Each tag may have 0 or 1 arguments (think Haskell's
 *      ADTs, but where you either have a tuple as an arg or nothing).
 *    - A tuple (i.e. list) of 2+ types.
 *    - An array (i.e. variable-length list) of items of a single type.
 *
 *  Written in pseudo-Haskell:
 *  data Type = N Number | T String | D Date
 *            | Tags [(TagName, Maybe Type)]
 *            | Tuple [Type]
 *            | Array Type
 */
public class DataType
{
    @OnThread(Tag.Simulation)
    public Column makeCalculatedColumn(RecordSet rs, ColumnId name, ExFunction<Integer, @Value Object> getItem) throws UserException, InternalException
    {
        return apply(new DataTypeVisitor<Column>()
        {
            private <T> T castTo(Class<T> cls, @Value Object value) throws InternalException
            {
                if (!cls.isAssignableFrom(value.getClass()))
                    throw new InternalException("Type inconsistency: should be " + cls + " but is " + value.getClass() + " for column: " + name);
                return cls.cast(value);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<NumericColumnStorage>(rs, name, (ExBiConsumer<Integer, @Nullable ProgressListener> g) -> new NumericColumnStorage(displayInfo, g), cache -> {
                    cache.add(castTo(Number.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column text() throws InternalException, UserException
            {
                return new CachedCalculatedColumn<StringColumnStorage>(rs, name, (ExBiConsumer<Integer, @Nullable ProgressListener> g) -> new StringColumnStorage(g), cache -> {
                    cache.add(castTo(String.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<TemporalColumnStorage>(rs, name, (ExBiConsumer<Integer, @Nullable ProgressListener> g) -> new TemporalColumnStorage(dateTimeInfo, g), cache -> {
                    cache.add(castTo(TemporalAccessor.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column bool() throws InternalException, UserException
            {
                return new CachedCalculatedColumn<BooleanColumnStorage>(rs, name, (ExBiConsumer<Integer, @Nullable ProgressListener> g) -> new BooleanColumnStorage(g), cache -> {
                    cache.add(castTo(Boolean.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<TaggedColumnStorage>(rs, name, (ExBiConsumer<Integer, @Nullable ProgressListener> g) -> new TaggedColumnStorage(typeName, tags, g), cache -> {
                    cache.add(castTo(TaggedValue.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tuple(List<DataType> inner) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<TupleColumnStorage>(rs, name, (ExBiConsumer<Integer, @Nullable ProgressListener> g) -> new TupleColumnStorage(inner, g), cache -> {
                    cache.add(castTo(Object[].class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column array(@Nullable DataType inner) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<ArrayColumnStorage>(rs, name, (ExBiConsumer<Integer, @Nullable ProgressListener> g) -> new ArrayColumnStorage(inner, g), cache -> {
                    if (inner != null)
                    {
                        ListEx listItem = castTo(ListEx.class, getItem.apply(cache.filled()));
                        cache.add(listItem);
                    }
                });
            }
        });
    }

    public DataTypeValue fromCollapsed(GetValue<@Value Object> get) throws UserException, InternalException
    {
        return apply(new DataTypeVisitor<DataTypeValue>()
        {
            @SuppressWarnings("value")
            private <T> GetValue<@Value T> castTo(Class<T> cls) throws InternalException
            {
                return (i, prog) -> {
                    Object value = get.getWithProgress(i, prog);
                    if (!cls.isAssignableFrom(value.getClass()))
                        throw new InternalException("Type inconsistency: should be " + cls + " but is " + value.getClass());
                    return cls.cast(value);
                };
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return DataTypeValue.number(displayInfo, castTo(Number.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue text() throws InternalException, UserException
            {
                return DataTypeValue.text(castTo(String.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return DataTypeValue.date(dateTimeInfo, castTo(TemporalAccessor.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue bool() throws InternalException, UserException
            {
                return DataTypeValue.bool(castTo(Boolean.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                GetValue<TaggedValue> getTaggedValue = castTo(TaggedValue.class);
                return DataTypeValue.tagged(typeName, Utility.mapListEx(tags, tag -> {
                    @Nullable DataType inner = tag.getInner();
                    return new TagType<>(tag.getName(), inner == null ? null : inner.fromCollapsed(
                        (i, prog) -> {
                            @Nullable @Value Object innerValue = getTaggedValue.getWithProgress(i, prog).getInner();
                            if (innerValue == null)
                                throw new InternalException("Unexpected null inner value for tag " + typeName + " " + tags.get(i));
                            return innerValue;
                        }
                    ));
                }), (i, prog) -> getTaggedValue.getWithProgress(i, prog).getTagIndex());
            }

            @Override
            @OnThread(Tag.Simulation)
            @SuppressWarnings("value")
            public DataTypeValue tuple(List<DataType> inner) throws InternalException, UserException
            {
                List<DataTypeValue> innerValues = new ArrayList<>();
                for (int type = 0; type < inner.size(); type++)
                {
                    int typeFinal = type;
                    innerValues.add(inner.get(type).fromCollapsed((i, prog) -> {
                        Object[] tuple = castTo(Object[].class).getWithProgress(i, prog);
                        return tuple[typeFinal];
                    }));
                }
                return DataTypeValue.tupleV(innerValues);
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue array(@Nullable DataType inner) throws InternalException, UserException
            {
                GetValue<@Value ListEx> getList = castTo(ListEx.class);
                if (inner == null)
                    return DataTypeValue.arrayV();
                DataType innerFinal = inner;
                return DataTypeValue.arrayV(inner, (i, prog) -> {
                    @NonNull @Value ListEx list = getList.getWithProgress(i, prog);
                    return new Pair<>(list.size(), innerFinal.fromCollapsed((arrayIndex, arrayProg) -> list.get(arrayIndex)));
                });
            }
        });
    }

    // Flattened ADT.  kind is the head tag, other bits are null/non-null depending:
    public static enum Kind {NUMBER, TEXT, DATETIME, BOOLEAN, TAGGED, TUPLE, ARRAY }
    final Kind kind;
    // For NUMBER:
    final @Nullable NumberInfo numberInfo;
    // for DATETIME:
    final @Nullable DateTimeInfo dateTimeInfo;
    // For TAGGED:
    final @Nullable TypeId taggedTypeName;
    final @Nullable List<TagType<DataType>> tagTypes;
    // For TUPLE (2+) and ARRAY (1).  If ARRAY and memberType is empty, indicates
    // the empty array (which can type-check against any array type)
    final @Nullable List<DataType> memberType;

    // package-visible
    DataType(Kind kind, @Nullable NumberInfo numberInfo, @Nullable DateTimeInfo dateTimeInfo, @Nullable Pair<TypeId, List<TagType<DataType>>> tagInfo, @Nullable List<DataType> memberType)
    {
        this.kind = kind;
        this.numberInfo = numberInfo;
        this.dateTimeInfo = dateTimeInfo;
        this.taggedTypeName = tagInfo == null ? null : tagInfo.getFirst();
        this.tagTypes = tagInfo == null ? null : tagInfo.getSecond();
        this.memberType = memberType;
    }

    public static final DataType NUMBER = DataType.number(NumberInfo.DEFAULT);
    public static final DataType BOOLEAN = new DataType(Kind.BOOLEAN, null, null, null, null);
    public static final DataType TEXT = new DataType(Kind.TEXT, null, null, null, null);

    public static DataType array()
    {
        return new DataType(Kind.ARRAY, null, null, null, Collections.emptyList());
    }

    public static DataType array(DataType inner)
    {
        return new DataType(Kind.ARRAY, null, null, null, Collections.singletonList(inner));
    }

    public static DataType tuple(DataType... inner)
    {
        return tuple(Arrays.asList(inner));
    }

    public static DataType tuple(List<DataType> inner)
    {
        return new DataType(Kind.TUPLE, null, null, null, new ArrayList<>(inner));
    }

    public static DataType date(DateTimeInfo dateTimeInfo)
    {
        return new DataType(Kind.DATETIME, null, dateTimeInfo, null, null);
    }

    public static class NumberInfo
    {
        private final Unit unit;
        private final int minimumDP;

        public NumberInfo(Unit unit, int minimumDP)
        {
            this.unit = unit;
            this.minimumDP = minimumDP;
        }

        public static final NumberInfo DEFAULT = new NumberInfo(Unit.SCALAR, 0);

        public int getMinimumDP()
        {
            return minimumDP;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            NumberInfo that = (NumberInfo) o;

            if (minimumDP != that.minimumDP) return false;
            return unit.equals(that.unit);
        }

        @Override
        public int hashCode()
        {
            int result = unit.hashCode();
            result = 31 * result + minimumDP;
            return result;
        }

        public Unit getUnit()
        {
            return unit;
        }

        public boolean sameType(@Nullable NumberInfo numberInfo)
        {
            if (numberInfo == null)
                return false;
            return unit.equals(numberInfo.unit);
        }


        public int hashCodeForType()
        {
            return unit.hashCode();
        }
    }

    public static interface DataTypeVisitorEx<R, E extends Throwable>
    {
        R number(NumberInfo displayInfo) throws InternalException, E;
        R text() throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo) throws InternalException, E;
        R bool() throws InternalException, E;

        R tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, E;
        R tuple(List<DataType> inner) throws InternalException, E;
        // If null, array is empty and thus of unknown type
        R array(@Nullable DataType inner) throws InternalException, E;
    }

    public static interface DataTypeVisitor<R> extends DataTypeVisitorEx<R, UserException>
    {
        
    }

    public static class SpecificDataTypeVisitor<R> implements DataTypeVisitor<R>
    {
        @Override
        public R number(NumberInfo displayInfo) throws InternalException, UserException
        {
            throw new InternalException("Unexpected number data type");
        }

        @Override
        public R text() throws InternalException, UserException
        {
            throw new InternalException("Unexpected text data type");
        }

        @Override
        public R tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
        {
            throw new InternalException("Unexpected tagged data type");
        }

        @Override
        public R bool() throws InternalException
        {
            throw new InternalException("Unexpected boolean type");
        }

        @Override
        public R date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
        {
            throw new InternalException("Unexpected date type");
        }

        @Override
        public R tuple(List<DataType> inner) throws InternalException, UserException
        {
            throw new InternalException("Unexpected tuple type");
        }

        @Override
        public R array(@Nullable DataType inner) throws InternalException, UserException
        {
            throw new InternalException("Unexpected array type");
        }
    }

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    public final <R, E extends Throwable> R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        switch (kind)
        {
            case NUMBER:
                return visitor.number(numberInfo);
            case TEXT:
                return visitor.text();
            case DATETIME:
                return visitor.date(dateTimeInfo);
            case BOOLEAN:
                return visitor.bool();
            case TAGGED:
                return visitor.tagged(taggedTypeName, tagTypes);
            case ARRAY:
                if (memberType.isEmpty())
                    return visitor.array(null);
                else
                    return visitor.array(memberType.get(0));
            case TUPLE:
                return visitor.tuple(memberType);
            default:
                throw new InternalException("Missing kind case");
        }
    }

    public static class TagType<T extends DataType>
    {
        private final String name;
        private final @Nullable T inner;

        public TagType(String name, @Nullable T inner)
        {
            this.name = name;
            this.inner = inner;
        }

        public String getName()
        {
            return name;
        }

        @Pure
        public @Nullable T getInner()
        {
            return inner;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TagType tag = (TagType) o;

            if (!name.equals(tag.name)) return false;
            return inner != null ? inner.equals(tag.inner) : tag.inner == null;

        }

        @Override
        public int hashCode()
        {
            int result = name.hashCode();
            result = 31 * result + (inner != null ? inner.hashCode() : 0);
            return result;
        }

        @Override
        public String toString()
        {
            return name + (inner == null ? "" : (":" + inner.toString()));
        }
    }

    @OnThread(Tag.Any)
    @SuppressWarnings("i18n") // Because of the "ERR"
    public @Localized String getSafeHeaderDisplay()
    {
        try
        {
            return getHeaderDisplay();
        }
        catch (InternalException | UserException e)
        {
            Utility.log(e);
            return "ERR";
        }
    }

    @OnThread(Tag.Any)
    @SuppressWarnings("i18n")
    public @Localized String getHeaderDisplay() throws UserException, InternalException
    {
        return apply(new DataTypeVisitor<String>()
        {
            @Override
            public String number(NumberInfo displayInfo) throws InternalException, UserException
            {
                String unit = displayInfo.getUnit().forDisplay();
                return "Number" + (unit.isEmpty() ? "" : "{" + unit + "}");
            }

            @Override
            public String text() throws InternalException, UserException
            {
                return "Text";
            }

            @Override
            public String tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return typeName.getRaw();
            }

            @Override
            public String tuple(List<DataType> inner) throws InternalException, UserException
            {
                StringBuilder s = new StringBuilder("(");
                boolean first = true;
                for (DataType t : inner)
                {
                    if (!first)
                        s.append(", ");
                    first = false;
                    s.append(t.apply(this));
                }
                s.append(")");
                return s.toString();
            }

            @Override
            public String array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return "[]";
                else
                    return "[" + inner.apply(this) + "]";
            }

            @Override
            public String date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                switch (dateTimeInfo.type)
                {
                    case YEARMONTHDAY:
                        return "DateYMD";
                    case YEARMONTH:
                        return "DateYM";
                    case TIMEOFDAY:
                        return "Time";
                    case TIMEOFDAYZONED:
                        return "TimeZ";
                    case DATETIME:
                        return "DateTime";
                    case DATETIMEZONED:
                        return "DateTimeZ";
                }
                throw new InternalException("Unknown date type: " + dateTimeInfo.type);
            }

            @Override
            public String bool() throws InternalException, UserException
            {
                return "Boolean";
            }
        });
    }

    @Override
    public String toString()
    {
        try
        {
            return getHeaderDisplay();
        }
        catch (UserException | InternalException e)
        {
            return "Error";
        }
    }

    // package-visible
    static DataType tagged(TypeId name, List<TagType<DataType>> tagTypes)
    {
        return new DataType(Kind.TAGGED, null, null, new Pair<>(name, tagTypes), null);
    }


    public static DataType number(NumberInfo numberInfo)
    {
        return new DataType(Kind.NUMBER, numberInfo, null, null, null);
    }

    public static <T extends DataType> boolean canFitInOneNumeric(List<? extends TagType<T>> tags) throws InternalException, UserException
    {
        // Can fit in one numeric if there is no inner types,
        // or if the only inner type is a single numeric
        boolean foundNumeric = false;
        for (TagType<T> t : tags)
        {
            if (t.getInner() != null)
            {
                if (t.getInner().kind == Kind.NUMBER)
                {
                    if (foundNumeric)
                        return false; // Can't have two numeric
                    foundNumeric = true;
                }
                else
                    return false; // Can't have anything non-numeric
            }
        }
        return foundNumeric;
    }

    /*
    @OnThread(Tag.Any)
    public DataTypeValue copy(GetValue<Object> get) throws InternalException
    {
        @Nullable Pair<TypeId, List<TagType<DataTypeValue>>> newTagTypes = null;
        if (this.taggedTypeName != null && this.tagTypes != null)
        {
            newTagTypes = new Pair<>(taggedTypeName, new ArrayList<>());
            for (TagType tagType : this.tagTypes)
                newTagTypes.getSecond().add(new TagType<>(tagType.getName(), tagType.getInner() == null ? null : tagType.getInner().copy(get)));
        }
        List<DataType> memberTypes = null;
        if (this.memberType != null)
        {
            memberTypes = new ArrayList<>();
            for (int tupleMember = 0; tupleMember < this.memberType.size(); tupleMember++)
            {
                int tupleMemberFinal = tupleMember;
                DataType dataType = this.memberType.get(tupleMember);
                memberTypes.add(dataType.copy((i, prog) -> (List<Object>)((Object[])get.getWithProgress(i, prog))[tupleMemberFinal]));
            }
        }
        return new DataTypeValue(kind, numberInfo, dateTimeInfo, newTagTypes,
            memberTypes, (i, prog) -> (Number)get.getWithProgress(i, prog),
            (i, prog) -> (String)get.getWithProgress(i, prog),
            (i, prog) -> (Temporal) get.getWithProgress(i, prog),
            (i, prog) -> (Boolean) get.getWithProgress(i, prog),
            (i, prog) -> (Integer) get.getWithProgress(i, prog), null);
    }
    */

    public boolean isTuple()
    {
        return kind == Kind.TUPLE;
    }

    public boolean isArray()
    {
        return kind == Kind.ARRAY;
    }

    // For arrays: single item or empty (if empty array).  For tuples, the set of contained types.
    // Everything else: an InternalException
    public List<DataType> getMemberType() throws InternalException
    {
        if (memberType == null)
            throw new InternalException("Fetching member type for non tuple/array: " + kind);
        return memberType;
    }

    public boolean isTagged()
    {
        return kind == Kind.TAGGED;
    }

    public TypeId getTaggedTypeName() throws InternalException
    {
        if (taggedTypeName != null)
            return taggedTypeName;
        throw new InternalException("Trying to get tag type name of non-tag type: " + this);
    }

    public List<TagType<DataType>> getTagTypes() throws InternalException
    {
        if (tagTypes != null)
            return tagTypes;
        throw new InternalException("Trying to get tag types of non-tag type: " + this);
    }


    // Returns (N, null) if there's no inner type.  Returns (-1, null) if no such constructor.
    // Throws internal exception if not a tagged type
    public Pair<Integer, @Nullable DataType> unwrapTag(String constructor) throws InternalException
    {
        if (tagTypes == null)
            throw new InternalException("Type is not a tagged type: " + this);
        List<Pair<Integer, @Nullable DataType>> tags = new ArrayList<>();
        for (int i = 0; i < tagTypes.size(); i++)
        {
            if (tagTypes.get(i).getName().equals(constructor))
                tags.add(new Pair<>(i, tagTypes.get(i).getInner()));
        }
        if (tags.size() > 1)
            throw new InternalException("Duplicate tag names in type: " + this);
        if (tags.size() == 0)
            return new Pair<>(-1, null); // Not found
        return tags.get(0);
    }

    // Note: this is customised from original template
    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        // Don't check for same class; let us be equal to a DataTypeValue
        if (o == null || !(o instanceof DataType)) return false;

        DataType dataType = (DataType) o;

        if (kind != dataType.kind) return false;
        // Don't use equals here, use sameType (weaker, but accurate for type comparison)
        if (numberInfo != null ? !numberInfo.sameType(dataType.numberInfo) : dataType.numberInfo != null) return false;
        if (dateTimeInfo != null ? !dateTimeInfo.sameType(dataType.dateTimeInfo) : dateTimeInfo != null) return false;
        if (memberType != null ? !memberType.equals(dataType.memberType) : dataType.memberType != null) return false;
        return tagTypes != null ? tagTypes.equals(dataType.tagTypes) : dataType.tagTypes == null;
    }

    @Override
    public int hashCode()
    {
        int result = kind.hashCode();
        // Must use specialised hashCode which matches sameType rather than equals
        result = 31 * result + (numberInfo != null ? numberInfo.hashCodeForType() : 0);
        result = 31 * result + (dateTimeInfo != null ? dateTimeInfo.hashCodeForType() : 0);
        result = 31 * result + (tagTypes != null ? tagTypes.hashCode() : 0);
        result = 31 * result + (memberType != null ? memberType.hashCode() : 0);
        return result;
    }

    @Pure
    public boolean isNumber()
    {
        return kind == Kind.NUMBER;
    }

    @Pure
    public boolean isText()
    {
        return kind == Kind.TEXT;
    }

    @Pure
    public boolean isDateTime()
    {
        return kind == Kind.DATETIME;
    }

    // is number, or has number anywhere inside:
    @Pure
    public boolean hasNumber()
    {
        return isNumber() || (tagTypes != null && tagTypes.stream().anyMatch(tt -> tt.getInner() != null && tt.getInner().hasNumber()));
    }

    @Pure
    public NumberInfo getNumberInfo() throws InternalException
    {
        if (numberInfo != null)
            return numberInfo;
        else
            throw new InternalException("Requesting numeric display info for non-numeric type: " + this);
    }

    @Pure
    public DateTimeInfo getDateTimeInfo() throws InternalException
    {
        if (dateTimeInfo != null)
            return dateTimeInfo;
        else
            throw new InternalException("Requesting date/time info for non-date/time type: " + this);
    }

    public static enum TypeRelation
    {
        /**
         * When you have two types which should be the same, but neither is more right
         * than the other, e.g. if you have "a = b", then a and b should be same type,
         * but neither is known to be right.
         */
        SYMMETRIC,
        /**
         * The first type, A, is the expected one, so if they don't match, B is wrong.
         */
        EXPECTED_A;
    }

    public static @Nullable DataType checkSame(@Nullable DataType a, @Nullable DataType b, ExConsumer<String> onError) throws UserException, InternalException
    {
        return checkSame(a, b, TypeRelation.SYMMETRIC, onError);
    }


    public static @Nullable DataType checkSame(@Nullable DataType a, @Nullable DataType b, TypeRelation relation, ExConsumer<String> onError) throws UserException, InternalException
    {
        if (a == null || b == null)
            return null;
        return DataType.<DataType, @Nullable DataType>zipSame(a, b, new ZipVisitor<DataType, @Nullable DataType>()
            {
                @Override
                public @Nullable DataType number(DataType a, DataType b, NumberInfo displayInfoA, NumberInfo displayInfoB) throws InternalException, UserException
                {
                    return displayInfoA.sameType(displayInfoB) ? a : null;
                }

                @Override
                public @Nullable DataType text(DataType a, DataType b) throws InternalException, UserException
                {
                    return a;
                }

                @Override
                public @Nullable DataType date(DataType a, DataType b, DateTimeInfo dateTimeInfoA, DateTimeInfo dateTimeInfoB) throws InternalException, UserException
                {
                    return dateTimeInfoA.sameType(dateTimeInfoB) ? a : null;
                }

                @Override
                public @Nullable DataType bool(DataType a, DataType b) throws InternalException, UserException
                {
                    return a;
                }

                @Override
                public @Nullable DataType tagged(DataType a, DataType b, TypeId typeNameA, List<TagType<DataType>> tagsA, TypeId typeNameB, List<TagType<DataType>> tagsB) throws InternalException, UserException
                {
                    // Because of the way tagged types work, there's no need to dig any deeper.  The types in a
                    // tagged type are known a priori because they're declared upfront, so as soon as you know
                    // what tag it is, you know what the full type is.  Thus the only question is: are these
                    // two types the same type?  It should be enough to compare type names usually, but because
                    // of the tricks we pull in testing, we also check tags for sanity:
                    boolean same = typeNameA.equals(typeNameB) && tagsA.equals(tagsB);
                    return same ? a : null;
                }

                @Override
                public @Nullable DataType tuple(DataType a, DataType b, List<DataType> innerA, List<DataType> innerB) throws InternalException, UserException
                {
                    if (innerA.size() != innerB.size())
                        return null;
                    List<DataType> innerR = new ArrayList<>();
                    for (int i = 0; i < innerA.size(); i++)
                    {
                        DataType t = checkSame(innerA.get(i), innerB.get(i), onError);
                        if (t == null)
                            return null;
                        innerR.add(t);
                    }
                    return DataType.tuple(innerR);
                }

                @Override
                public @Nullable DataType array(DataType a, DataType b, @Nullable DataType innerA, @Nullable DataType innerB) throws InternalException, UserException
                {
                    @Nullable DataType innerR;
                    // If innerA is null, means empty array:
                    if (innerA == null)
                        innerR = innerB;
                    // Same for innerB
                    else if (innerB == null)
                        innerR = innerA;
                    else
                    {
                        innerR = checkSame(innerA, innerB, onError);
                        // At this point, if innerR is null, it means error, not empty array, so we must return null:
                        if (innerR == null)
                            return null;
                    }
                    return innerR == null ? DataType.array() : DataType.array(innerR);
                }

                @Override
                public @Nullable DataType differentKind(DataType a, DataType b) throws InternalException, UserException
                {
                    switch (relation)
                    {
                        case SYMMETRIC:
                            onError.accept("Type mismatch: " + a + " vs " + b);
                            break;
                        case EXPECTED_A:
                            onError.accept("Expected type " + a + " but was " + b);
                            break;
                    }

                    return null;
                }
            });
    }

    @SuppressWarnings("nullness")
    private static <T extends DataType, R> R zipSame(T a, T b, ZipVisitor<T, R> visitor) throws UserException, InternalException
    {
        if (a.kind != b.kind)
            return visitor.differentKind(a, b);
        else
        {
            switch (a.kind)
            {
                case NUMBER:
                    return visitor.number(a, b, a.numberInfo, b.numberInfo);
                case TEXT:
                    return visitor.text(a, b);
                case DATETIME:
                    return visitor.date(a, b, a.dateTimeInfo, b.dateTimeInfo);
                case BOOLEAN:
                    return visitor.bool(a, b);
                case TAGGED:
                    return visitor.tagged(a, b, a.taggedTypeName, a.tagTypes, b.taggedTypeName, b.tagTypes);
                case TUPLE:
                    return visitor.tuple(a, b, a.memberType, b.memberType);
                case ARRAY:
                    return visitor.array(a, b, a.memberType.isEmpty() ? null : a.memberType.get(0), b.memberType.isEmpty() ? null : b.memberType.get(0));
            }
            throw new InternalException("Missing kind case");
        }
    }


    public static <T extends DataType> @Nullable DataType checkAllSame(List<T> types, ExConsumer<String> onError) throws InternalException, UserException
    {
        if (types.isEmpty())
            throw new InternalException("Cannot type-check empty list of types");
        if (types.size() == 1)
            return types.get(0);
        DataType cur = types.get(0);
        for (int i = 1; i < types.size(); i++)
        {
            DataType next = checkSame(cur, types.get(i), onError);
            if (next == null)
                return null; // Bail out early
            cur = next;
        }
        return cur;
    }

    public static class ColumnMaker<C extends Column> implements FunctionInt<RecordSet, Column>
    {
        private @Nullable C column;
        private final FunctionInt<RecordSet, C> makeColumn;
        private final ExBiConsumer<C, ItemContext> loadData;

        private ColumnMaker(FunctionInt<RecordSet, C> makeColumn, ExBiConsumer<C, ItemContext> loadData)
        {
            this.makeColumn = makeColumn;
            this.loadData = loadData;
        }

        @Override
        public final Column apply(RecordSet rs) throws InternalException, UserException
        {
            column = makeColumn.apply(rs);
            return column;
        }

        // Only valid to call after apply:
        public void loadRow(ItemContext ctx) throws InternalException, UserException
        {
            if (column == null)
                throw new InternalException("Calling loadRow before column creation");
            loadData.accept(column, ctx);
        }
    }

    @OnThread(Tag.Simulation)
    public ColumnMaker<?> makeImmediateColumn(ColumnId columnId) throws InternalException, UserException
    {
        return apply(new DataTypeVisitor<ColumnMaker<?>>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryNumericColumn>(rs -> new MemoryNumericColumn(rs, columnId, displayInfo, Collections.emptyList()), (column, data) -> {
                    DataParser.NumberContext number = data.number();
                    if (number == null)
                        throw new UserException("Expected string value but found: \"" + data.getText() + "\"");
                    column.add(number.getText());
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?> text() throws InternalException, UserException
            {
                return new ColumnMaker<MemoryStringColumn>(rs -> new MemoryStringColumn(rs, columnId, Collections.emptyList()), (column, data) -> {
                    StringContext string = data.string();
                    if (string == null)
                        throw new UserException("Expected string value but found: \"" + data.getText() + "\"");
                    column.add(string.getText());
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryTemporalColumn>(rs -> new MemoryTemporalColumn(rs, columnId, dateTimeInfo, Collections.emptyList()), (column, data) ->
                {
                    StringContext c = data.string();
                    if (c == null)
                        throw new UserException("Expected quoted date value but found: \"" + data.getText() + "\"");
                    DateTimeFormatter formatter = dateTimeInfo.getFormatter();
                    try
                    {
                        column.add(formatter.parse(c.getText()));
                    }
                    catch (DateTimeParseException e)
                    {
                        throw new UserException("Problem reading date/time of type " + dateTimeInfo.getType(), e);
                    }
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?> bool() throws InternalException, UserException
            {
                return new ColumnMaker<MemoryBooleanColumn>(rs -> new MemoryBooleanColumn(rs, columnId, Collections.emptyList()), (column, data) -> {
                    BoolContext b = data.bool();
                    if (b == null)
                        throw new UserException("Expected boolean value but found: \"" + data.getText() + "\"");
                    column.add(b.getText().equals("true"));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryTaggedColumn>(rs -> new MemoryTaggedColumn(rs, columnId, typeName, tags, Collections.emptyList()), (column, data) -> {
                    TaggedContext b = data.tagged();
                    if (b == null)
                        throw new UserException("Expected tagged value but found: \"" + data.getText() + "\"");
                    column.add(loadValue(tags, b));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?> tuple(List<DataType> inner) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryTupleColumn>(rs -> new MemoryTupleColumn(rs, columnId, inner), (column, data) -> {
                    TupleContext c = data.tuple();
                    if (c == null)
                        throw new UserException("Expected tuple but found: \"" + data.getText() + "\"");
                    if (c.item().size() != inner.size())
                        throw new UserException("Wrong number of tuples items; expected " + inner.size() + " but found " + c.item().size());
                    Object[] tuple = new Object[inner.size()];
                    for (int i = 0; i < tuple.length; i++)
                    {
                        tuple[i] = loadSingleItem(inner.get(i), c.item(i));
                    }
                    column.add(tuple);
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?> array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot have column with type of empty array");

                DataType innerFinal = inner;
                return new ColumnMaker<MemoryArrayColumn>(rs -> new MemoryArrayColumn(rs, columnId, innerFinal, Collections.emptyList()), (column, data) -> {
                    ArrayContext c = data.array();
                    if (c == null)
                        throw new UserException("Expected array but found: \"" + data.getText() + "\"");
                    List<@Value Object> array = new ArrayList<>();
                    for (ItemContext itemContext : c.item())
                    {
                        array.add(loadSingleItem(innerFinal, itemContext));
                    }
                    //ColumnStorage storage = DataTypeUtility.makeColumnStorage(inner, null);
                    //storage.addAll(array);
                    column.add(new ListEx()
                    {
                        @Override
                        public int size() throws InternalException, UserException
                        {
                            return array.size();
                        }

                        @Override
                        public @Value Object get(int index) throws InternalException, UserException
                        {
                            return array.get(index);
                        }
                    });
                });
            }
        });
    }

    private static TaggedValue loadValue(List<TagType<DataType>> tags, TaggedContext taggedContext) throws UserException, InternalException
    {
        String constructor = taggedContext.tag().getText();
        for (int i = 0; i < tags.size(); i++)
        {
            TagType<DataType> tag = tags.get(i);
            if (tag.getName().equals(constructor))
            {
                ItemContext item = taggedContext.item();
                if (tag.getInner() != null)
                {
                    if (item == null)
                        throw new UserException("Expected inner type but found no inner value: \"" + taggedContext.getText() + "\"");
                    return new TaggedValue(i, loadSingleItem(tag.getInner(), item));
                }
                else if (item != null)
                    throw new UserException("Expected no inner type but found inner value: \"" + taggedContext.getText() + "\"");

                return new TaggedValue(i, null);
            }
        }
        throw new UserException("Could not find matching tag for: \"" + taggedContext.tag().getText() + "\" in: " + tags.stream().map(t -> t.getName()).collect(Collectors.joining(", ")));

    }

    @OnThread(Tag.Any)
    public static @Value Object loadSingleItem(DataType type, final ItemContext item) throws InternalException, UserException
    {
        return type.apply(new DataTypeVisitor<@Value Object>()
        {
            @Override
            public @Value Object number(NumberInfo displayInfo) throws InternalException, UserException
            {
                DataParser.NumberContext number = item.number();
                if (number == null)
                    throw new UserException("Expected number, found: " + item.getText() + item.getStart());
                return Utility.value(Utility.parseNumber(number.getText()));
            }

            @Override
            public @Value Object text() throws InternalException, UserException
            {
                StringContext string = item.string();
                if (string == null)
                    throw new UserException("Expected string, found: " + item.getText() + item.getStart());
                return Utility.value(string.getText());
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                StringContext string = item.string();
                if (string == null)
                    throw new UserException("Expected quoted date, found: " + item.getText() + item.getStart());
                DateTimeFormatter formatter = dateTimeInfo.getFormatter();
                try
                {
                    return Utility.value(dateTimeInfo, formatter.parse(string.getText()));
                }
                catch (DateTimeParseException e)
                {
                    throw new UserException("Error loading date time \"" + string.getText() + "\", type " + dateTimeInfo.getType(), e);
                }
            }

            @Override
            public @Value Object bool() throws InternalException, UserException
            {
                BoolContext bool = item.bool();
                if (bool == null)
                    throw new UserException("Expected bool, found: " + item.getText() + item.getStart());
                return Utility.value(bool.getText().equals("true"));
            }

            @Override
            public @Value Object tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return loadValue(tags, item.tagged());
            }

            @Override
            public @Value Object tuple(List<DataType> inner) throws InternalException, UserException
            {
                TupleContext tupleContext = item.tuple();
                if (tupleContext == null)
                    throw new UserException("Expected tuple, found: " + item.getText() + item.getStart());
                if (tupleContext.item().size() != inner.size())
                    throw new UserException("Expected " + inner.size() + " data values as per type, but found: " + tupleContext.item().size());
                @Value Object[] data = new Object[inner.size()];
                for (int i = 0; i < inner.size(); i++)
                {
                    data[i] = loadSingleItem(inner.get(i), tupleContext.item(i));
                }
                return Utility.value(data);
            }

            @Override
            public @Value Object array(final @Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot load column with value of type empty array");
                else
                {
                    @NonNull DataType innerFinal = inner;
                    return Utility.value(Utility.<ItemContext, @Value Object>mapListEx(item.array().item(), entry -> loadSingleItem(innerFinal, entry)));
                }
            }
        });
    }

    // If topLevelDeclaration is false, save a reference (matters for tagged types)
    @OnThread(Tag.FXPlatform)
    public OutputBuilder save(OutputBuilder b, boolean topLevelDeclaration) throws InternalException
    {
        apply(new DataTypeVisitorEx<UnitType, InternalException>()
        {
            @Override
            public UnitType number(NumberInfo displayInfo) throws InternalException, InternalException
            {
                b.t(FormatLexer.NUMBER, FormatLexer.VOCABULARY);
                b.n(displayInfo.getMinimumDP());
                b.unit(displayInfo.getUnit().toString());
                return UnitType.UNIT;
            }

            @Override
            public UnitType text() throws InternalException, InternalException
            {
                b.t(FormatLexer.TEXT, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        b.t(FormatLexer.YEARMONTHDAY, FormatLexer.VOCABULARY);
                        break;
                    case YEARMONTH:
                        b.t(FormatLexer.YEARMONTH, FormatLexer.VOCABULARY);
                        break;
                    case TIMEOFDAY:
                        b.t(FormatLexer.TIMEOFDAY, FormatLexer.VOCABULARY);
                        break;
                    case TIMEOFDAYZONED:
                        b.t(FormatLexer.TIMEOFDAYZONED, FormatLexer.VOCABULARY);
                        break;
                    case DATETIME:
                        b.t(FormatLexer.DATETIME, FormatLexer.VOCABULARY);
                        break;
                    case DATETIMEZONED:
                        b.t(FormatLexer.DATETIMEZONED, FormatLexer.VOCABULARY);
                        break;
                }
                return UnitType.UNIT;
            }

            @Override
            public UnitType bool() throws InternalException, InternalException
            {
                b.t(FormatLexer.BOOLEAN, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public UnitType tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, InternalException
            {
                b.t(FormatLexer.TAGGED, FormatLexer.VOCABULARY);
                if (topLevelDeclaration)
                {
                    b.t(FormatLexer.OPEN_BRACKET, FormatLexer.VOCABULARY);
                    for (TagType<DataType> tag : tags)
                    {
                        b.kw("\\" + b.quotedIfNecessary(tag.getName()) + (tag.getInner() != null ? ":" : ""));
                        if (tag.getInner() != null)
                            tag.getInner().save(b, false);
                    }
                    b.t(FormatLexer.CLOSE_BRACKET, FormatLexer.VOCABULARY);
                }
                else
                {
                    b.quote(typeName);
                }
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public UnitType tuple(List<DataType> inner) throws InternalException, InternalException
            {
                b.t(FormatLexer.OPEN_BRACKET, FormatLexer.VOCABULARY);
                boolean first = true;
                for (DataType dataType : inner)
                {
                    if (!first)
                        b.raw(",");
                    first = false;
                    dataType.save(b, false);
                }
                b.t(FormatLexer.CLOSE_BRACKET, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public UnitType array(@Nullable DataType inner) throws InternalException, InternalException
            {
                b.t(FormatLexer.OPEN_SQUARE, FormatLexer.VOCABULARY);
                if (inner != null)
                    inner.save(b, false);
                b.t(FormatLexer.CLOSE_SQUARE, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }
        });
        return b;
    }

    public boolean hasTag(String tagName) throws UserException, InternalException
    {
        return apply(new SpecificDataTypeVisitor<Boolean>() {
            @Override
            public Boolean tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return tags.stream().anyMatch(tt -> tt.getName().equals(tagName));
            }
        });
    }

    public static class DateTimeInfo
    {
        public DateTimeFormatter getFormatter() throws InternalException
        {
            DateTimeFormatter formatter;
            switch (getType())
            {
                case YEARMONTHDAY:
                    formatter = DateTimeFormatter.ISO_LOCAL_DATE;
                    break;
                case YEARMONTH:
                    // Not accessible in YearMonth, but taken from there:
                    formatter = new DateTimeFormatterBuilder()
                        .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                        .appendLiteral('-')
                        .appendValue(MONTH_OF_YEAR, 2)
                        .toFormatter();
                    break;
                case TIMEOFDAY:
                    formatter = DateTimeFormatter.ISO_LOCAL_TIME;
                    break;
                case TIMEOFDAYZONED:
                    formatter = DateTimeFormatter.ISO_TIME;
                    break;
                case DATETIME:
                    formatter = DateTimeFormatter.ISO_DATE_TIME;
                    break;
                case DATETIMEZONED:
                    formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;
                    break;
                default:
                    throw new InternalException("Unrecognised date/time type: " + getType());
            }
            return formatter;
        }

        public static enum DateTimeType
        {
            /** LocalDate */
            YEARMONTHDAY,
            /** YearMonth */
            YEARMONTH,
            /** LocalTime */
            TIMEOFDAY,
            /** OffsetTime */
            TIMEOFDAYZONED,
            /** LocalDateTime */
            DATETIME,
            /** ZonedDateTime */
            DATETIMEZONED;
        }

        private final DateTimeType type;

        public DateTimeInfo(DateTimeType type)
        {
            this.type = type;
        }

        public boolean hasYearMonthDay()
        {
            switch (type)
            {
                case YEARMONTHDAY:
                case DATETIME:
                case DATETIMEZONED:
                    return true;
                default:
                    return false;
            }
        }


        public boolean hasTime()
        {
            switch (type)
            {
                case TIMEOFDAY:
                case TIMEOFDAYZONED:
                case DATETIME:
                case DATETIMEZONED:
                    return true;
                default:
                    return false;
            }
        }


        public boolean hasZone()
        {
            switch (type)
            {
                case TIMEOFDAYZONED:
                case DATETIMEZONED:
                    return true;
                default:
                    return false;
            }
        }

        public DateTimeType getType()
        {
            return type;
        }

        public boolean sameType(@Nullable DateTimeInfo dateTimeInfo)
        {
            return dateTimeInfo != null && type == dateTimeInfo.type;
        }

        public int hashCodeForType()
        {
            return type.hashCode();
        }


        public Comparator<@UnknownIfValue TemporalAccessor> getComparator() throws InternalException
        {
            switch (type)
            {
                case YEARMONTHDAY:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH));
                case YEARMONTH:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR));
                case TIMEOFDAY:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND));
                case TIMEOFDAYZONED:
                    return Comparator.comparing((TemporalAccessor t) -> {
                            return OffsetTime.from(t).withOffsetSameInstant(ZoneOffset.UTC);
                        },
                        Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND))
                    );
                case DATETIME:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND));
                case DATETIMEZONED:
                    return Comparator.comparing((TemporalAccessor t) -> ZonedDateTime.from(t).withZoneSameInstant(ZoneOffset.UTC),
                        Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND))
                    );
            }
            throw new InternalException("Unknown date type: " + type);
        }

        @Override
        public String toString()
        {
            return "DateTimeInfo{" +
                "type=" + type +
                '}';
        }
    }

    public static interface ZipVisitor<T extends DataType, R>
    {
        R number(T a, T b, NumberInfo displayInfoA, NumberInfo displayInfoB) throws InternalException, UserException;
        R text(T a, T b) throws InternalException, UserException;
        R date(T a, T b, DateTimeInfo dateTimeInfoA, DateTimeInfo dateTimeInfoB) throws InternalException, UserException;
        R bool(T a, T b) throws InternalException, UserException;

        R tagged(T a, T b, TypeId typeNameA, List<TagType<DataType>> tagsA, TypeId typeNameB, List<TagType<DataType>> tagsB) throws InternalException, UserException;
        R tuple(T a, T b, List<DataType> innerA, List<DataType> innerB) throws InternalException, UserException;
        // If null, array is empty and thus of unknown type
        R array(T a, T b, @Nullable DataType innerA, @Nullable DataType innerB) throws InternalException, UserException;

        R differentKind(T a, T b) throws InternalException, UserException;
    }
}
