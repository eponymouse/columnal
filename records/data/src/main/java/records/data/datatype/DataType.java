package records.data.datatype;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import log.Log;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.ArrayColumnStorage;
import records.data.BooleanColumnStorage;
import records.data.CachedCalculatedColumn;
import records.data.Column;
import records.data.ColumnId;
import records.data.ColumnStorage.BeforeGet;
import records.data.EditableColumn;
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
import records.data.TemporalColumnStorage;
import records.data.TupleColumnStorage;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.ParseException;
import records.error.UserException;
import records.grammar.DataParser;
import records.grammar.DataParser.BoolContext;
import records.grammar.DataParser.NumberContext;
import records.grammar.DataParser.StringContext;
import records.grammar.DataParser.TagContext;
import records.grammar.FormatLexer;
import records.loadsave.OutputBuilder;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.BiFunctionInt;
import utility.Either;
import utility.ExBiConsumer;
import utility.ExFunction;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.UnitType;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static records.data.datatype.DataType.DateTimeInfo.F.*;

/**
 * A data type can be the following:
 *
 *  - A built-in/primitive type:
 *    - A number.  This has a small bit of dynamic typing: it may be
 *      integers or decimals, but this is a performance optimisation
 *      not a user-visible difference.
 *    - A string.
 *    - A date.
 *    - A boolean
 *  - A composite type:
 *    - A set of 2+ tags.  Each tag may have 0 or 1 arguments (think Haskell's
 *      ADTs, but where you either have a tuple as an arg or nothing).
 *    - A tuple (i.e. list) of 2+ types.
 *    - An array (i.e. variable-length list) of items of a single type.
 *
 *  Written in pseudo-Haskell:
 *  data Type = N Number | T String | D Date | B Boolean
 *            | Tags [(TagName, Maybe Type)]
 *            | Tuple [Type]
 *            | Array Type
 */
public class DataType implements StyledShowable
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
                return new CachedCalculatedColumn<NumericColumnStorage>(rs, name, (BeforeGet<NumericColumnStorage> g) -> new NumericColumnStorage(displayInfo, g), cache -> {
                    cache.add(castTo(Number.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column text() throws InternalException, UserException
            {
                return new CachedCalculatedColumn<StringColumnStorage>(rs, name, (BeforeGet<StringColumnStorage> g) -> new StringColumnStorage(g), cache -> {
                    cache.add(castTo(String.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<TemporalColumnStorage>(rs, name, (BeforeGet<TemporalColumnStorage> g) -> new TemporalColumnStorage(dateTimeInfo, g), cache -> {
                    cache.add(castTo(TemporalAccessor.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column bool() throws InternalException, UserException
            {
                return new CachedCalculatedColumn<BooleanColumnStorage>(rs, name, (BeforeGet<BooleanColumnStorage> g) -> new BooleanColumnStorage(g), cache -> {
                    cache.add(castTo(Boolean.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<TaggedColumnStorage>(rs, name, (BeforeGet<TaggedColumnStorage> g) -> new TaggedColumnStorage(typeName, typeVars, tags, g), cache -> {
                    cache.add(castTo(TaggedValue.class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<TupleColumnStorage>(rs, name, (BeforeGet<TupleColumnStorage> g) -> new TupleColumnStorage(inner, g), cache -> {
                    cache.add(castTo(Object[].class, getItem.apply(cache.filled())));
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column array(@Nullable DataType inner) throws InternalException, UserException
            {
                return new CachedCalculatedColumn<ArrayColumnStorage>(rs, name, (BeforeGet<ArrayColumnStorage> g) -> new ArrayColumnStorage(inner, g), cache -> {
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
            public DataTypeValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                GetValue<TaggedValue> getTaggedValue = castTo(TaggedValue.class);
                return DataTypeValue.tagged(typeName, typeVars, Utility.mapListExI(tags, tag -> {
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
            public DataTypeValue tuple(ImmutableList<DataType> inner) throws InternalException, UserException
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
    
    public static DataType function(DataType argType, DataType resultType)
    {
        return new DataType(Kind.FUNCTION, null, null, null, ImmutableList.of(argType, resultType));
    }

    // Flattened ADT.  kind is the head tag, other bits are null/non-null depending:
    public static enum Kind {NUMBER, TEXT, DATETIME, BOOLEAN, TAGGED, TUPLE, ARRAY, FUNCTION }
    final Kind kind;
    // For NUMBER:
    final @Nullable NumberInfo numberInfo;
    // for DATETIME:
    final @Nullable DateTimeInfo dateTimeInfo;
    // For TAGGED:
    final @Nullable TypeId taggedTypeName;
    // We store the declared type variables here even through they should already be substituted,
    // in case we need to turn this concrete type back into a TypeExp for unification:
    final @Nullable ImmutableList<Either<Unit, DataType>> tagTypeVariableSubstitutions;
    final @Nullable ImmutableList<TagType<DataType>> tagTypes;
    // For TUPLE (2+) and ARRAY (1) and FUNCTION(2).  If ARRAY and memberType is empty, indicates
    // the empty array (which can type-check against any array type)
    final @Nullable ImmutableList<DataType> memberType;

    // package-visible
    DataType(Kind kind, @Nullable NumberInfo numberInfo, @Nullable DateTimeInfo dateTimeInfo, @Nullable TagTypeDetails tagInfo, @Nullable List<DataType> memberType)
    {
        this.kind = kind;
        this.numberInfo = numberInfo;
        this.dateTimeInfo = dateTimeInfo;
        this.taggedTypeName = tagInfo == null ? null : tagInfo.name;
        this.tagTypeVariableSubstitutions = tagInfo == null ? null : tagInfo.typeVariableSubstitutions;
        this.tagTypes = tagInfo == null ? null : tagInfo.tagTypes;
        this.memberType = memberType == null ? null : ImmutableList.copyOf(memberType);
    }

    public static final DataType NUMBER = DataType.number(NumberInfo.DEFAULT);
    public static final DataType BOOLEAN = new DataType(Kind.BOOLEAN, null, null, null, null);
    public static final DataType TEXT = new DataType(Kind.TEXT, null, null, null, null);

    protected static class TagTypeDetails
    {
        private final TypeId name;
        private final ImmutableList<Either<Unit, DataType>> typeVariableSubstitutions;
        private final ImmutableList<TagType<DataType>> tagTypes;

        protected TagTypeDetails(TypeId name, ImmutableList<Either<Unit, DataType>> typeVariableSubstitutions, ImmutableList<TagType<DataType>> tagTypes)
        {
            this.name = name;
            this.typeVariableSubstitutions = typeVariableSubstitutions;
            this.tagTypes = tagTypes;
        }
    }
    
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

    public static interface DataTypeVisitorEx<R, E extends Throwable>
    {
        R number(NumberInfo numberInfo) throws InternalException, E;
        R text() throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo) throws InternalException, E;
        R bool() throws InternalException, E;

        R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, E;
        R tuple(ImmutableList<DataType> inner) throws InternalException, E;
        R array(DataType inner) throws InternalException, E;
        
        default R function(DataType argType, DataType resultType) throws InternalException, E
        {
            throw new InternalException("Functions are unsupported, plain data values expected");
        };
    }

    public static interface DataTypeVisitor<R> extends DataTypeVisitorEx<R, UserException>
    {
        
    }

    public static interface ConcreteDataTypeVisitor<R> extends DataTypeVisitor<R>
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
        public R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
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
        public R tuple(ImmutableList<DataType> inner) throws InternalException, UserException
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
                return visitor.tagged(taggedTypeName, tagTypeVariableSubstitutions, tagTypes);
            case ARRAY:
                if (memberType.isEmpty())
                    return visitor.array(null);
                else
                    return visitor.array(memberType.get(0));
            case TUPLE:
                return visitor.tuple(memberType);
            case FUNCTION:
                return visitor.function(memberType.get(0), memberType.get(1));
            default:
                throw new InternalException("Missing kind case");
        }
    }

    public static class TagType<T>
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

        /*
        public TagType<DataType> upcast()
        {
            return new TagType<>(name, inner);
        }
        */
        
        public <U> TagType<U> map(Function<T, U> changeInner)
        {
            return new TagType<>(name, inner == null ? null : changeInner.apply(inner));
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
            Log.log(e);
            return "ERR";
        }
    }

    @OnThread(Tag.Any)
    public @Localized String getHeaderDisplay() throws UserException, InternalException
    {
        return toDisplay(false);
    }

    @OnThread(Tag.Any)
    @SuppressWarnings("i18n")
    public @Localized String toDisplay(boolean drillIntoTagged) throws UserException, InternalException
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
            public String tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                @Localized String typeStr = typeName.getRaw();
                if (!typeVars.isEmpty())
                    typeStr += typeVars.stream().map(t -> "-" + t.toString()).collect(Collectors.joining());
                if (drillIntoTagged)
                {
                    typeStr += " <";
                    boolean any = false;
                    for (int i = 0; i < tags.size(); i++)
                    {
                        if (i > 0)
                            typeStr += " | ";
                        TagType<DataType> tag = tags.get(i);
                        typeStr += tag.name;
                        if (tag.inner != null)
                        {
                            typeStr += ":" + tag.inner.toDisplay(true);
                        }
                    }
                    typeStr += ">";
                }
                return typeStr;
            }

            @Override
            public String tuple(ImmutableList<DataType> inner) throws InternalException, UserException
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
                        return "Date";
                    case YEARMONTH:
                        return "DateYM";
                    case TIMEOFDAY:
                        return "Time";
                    //case TIMEOFDAYZONED:
                        //return "TimeZoned";
                    case DATETIME:
                        return "DateTime";
                    case DATETIMEZONED:
                        return "DateTimeZoned";
                }
                throw new InternalException("Unknown date type: " + dateTimeInfo.type);
            }

            @Override
            public String bool() throws InternalException, UserException
            {
                return "Boolean";
            }

            @Override
            public String function(DataType argType, DataType resultType) throws InternalException, UserException
            {
                return argType.toDisplay(drillIntoTagged) + " -> " + resultType.toDisplay(drillIntoTagged);
            }
        });
    }

    @Override
    public String toString()
    {
        try
        {
            return toDisplay(true);
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            return "Error: " + e.getLocalizedMessage();
        }
    }

    @Override
    public StyledString toStyledString()
    {
        try
        {
            return StyledString.s(toDisplay(false));
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            return StyledString.s("Error showing type: " + e.getLocalizedMessage());
        }
    }

    // package-visible
    static DataType tagged(TypeId name, ImmutableList<Either<Unit, DataType>> typeVariableSubstitutes, ImmutableList<TagType<DataType>> tagTypes)
    {
        return new DataType(Kind.TAGGED, null, null, new TagTypeDetails(name, typeVariableSubstitutes, tagTypes), null);
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

    public ImmutableList<TagType<DataType>> getTagTypes() throws InternalException
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
        if (taggedTypeName != null ? !taggedTypeName.equals(dataType.taggedTypeName) : dataType.taggedTypeName != null) return false;
        if (!Objects.equals(tagTypeVariableSubstitutions, ((DataType) o).tagTypeVariableSubstitutions)) return false;
        return tagTypes != null ? tagTypes.equals(dataType.tagTypes) : dataType.tagTypes == null;
    }

    @Override
    public int hashCode()
    {
        int result = kind.hashCode();
        // Must use specialised hashCodeForType which matches sameType rather than equals
        result = 31 * result + (numberInfo != null ? numberInfo.hashCodeForType() : 0);
        result = 31 * result + (dateTimeInfo != null ? dateTimeInfo.hashCodeForType() : 0);
        result = 31 * result + (tagTypes != null ? tagTypes.hashCode() : 0);
        result = 31 * result + (memberType != null ? memberType.hashCode() : 0);
        result = 31 * result + (taggedTypeName != null ? taggedTypeName.hashCode() : 0);
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

    public static @Nullable DataType checkSame(@Nullable DataType a, @Nullable DataType b, Consumer<String> onError) throws UserException, InternalException
    {
        return checkSame(a, b, TypeRelation.SYMMETRIC, onError);
    }

    public static @Nullable Either<Unit, DataType> checkSame(@Nullable Either<Unit, DataType> a, @Nullable Either<Unit, DataType> b, Consumer<String> onError) throws UserException, InternalException
    {
        if (a == null || b == null)
            return null;
        else if (a.isRight() && b.isRight())
        {
            DataType t = checkSame(a.getRight("Impossible"), b.getRight("Impossible"), TypeRelation.SYMMETRIC, onError);
            return t == null ? null : Either.right(t);
        }
        else if (a.isLeft() && b.isLeft())
            return a.getLeft("Impossible").equals(b.getLeft("Impossible")) ? a : null;
        else
            return null;
    }


    public static @Nullable DataType checkSame(@Nullable DataType a, @Nullable DataType b, TypeRelation relation, Consumer<String> onError) throws UserException, InternalException
    {
        if (a == null || b == null)
            return null;
        return DataType.<DataType, @Nullable DataType>zipSame(a, b, new ZipVisitor<DataType, @Nullable DataType>()
            {
                @Override
                public @Nullable DataType number(DataType a, DataType b, NumberInfo displayInfoA, NumberInfo displayInfoB) throws InternalException, UserException
                {
                    if (displayInfoA.sameType(displayInfoB))
                        return a;
                    else
                    {
                        onError.accept("Differing number types: " + displayInfoA + " vs " + displayInfoB);
                        return null;
                    }
                }

                @Override
                public @Nullable DataType text(DataType a, DataType b) throws InternalException, UserException
                {
                    return a;
                }

                @Override
                public @Nullable DataType date(DataType a, DataType b, DateTimeInfo dateTimeInfoA, DateTimeInfo dateTimeInfoB) throws InternalException, UserException
                {
                    if (dateTimeInfoA.sameType(dateTimeInfoB))
                        return a;
                    else
                    {
                        switch (relation)
                        {
                            case SYMMETRIC:
                                onError.accept("Types differ: " + dateTimeInfoA + " vs " + dateTimeInfoB);
                                break;
                            case EXPECTED_A:
                                onError.accept("Expected type " + dateTimeInfoA + " but found " + dateTimeInfoB);
                                break;
                        }
                        return null;
                    }
                }

                @Override
                public @Nullable DataType bool(DataType a, DataType b) throws InternalException, UserException
                {
                    return a;
                }

                @Override
                public @Nullable DataType tagged(DataType a, DataType b, TypeId typeNameA, ImmutableList<Either<Unit, DataType>> varsA, List<TagType<DataType>> tagsA, TypeId typeNameB, ImmutableList<Either<Unit, DataType>> varsB, List<TagType<DataType>> tagsB) throws InternalException, UserException
                {
                    // Because of the way tagged types work, there's no need to dig any deeper.  The types in a
                    // tagged type are known a priori because they're declared upfront, so as soon as you know
                    // what tag it is, you know what the full type is.  Thus the only question is: are these
                    // two types the same type?  It should be enough to compare type names and type variables:
                    boolean sameName = typeNameA.equals(typeNameB);
                    if (sameName)
                    {
                        if (varsA.size() != varsB.size())
                        {
                            throw new InternalException("Type vars differ in number for same type: " + a);
                        }
                        for (int i = 0; i < varsA.size(); i++)
                        {
                            Either<Unit, DataType> t = checkSame(varsA.get(i), varsB.get(i), onError);
                            if (t == null)
                                return null;
                        }
                        return a;
                    }
                    else
                    {
                        switch (relation)
                        {
                            case SYMMETRIC:
                                onError.accept("Types differ: " + typeNameA + " vs " + typeNameB);
                                break;
                            case EXPECTED_A:
                                onError.accept("Expected type " + typeNameA + " but found " + typeNameB);
                                break;
                        }
                        return null;
                    }
                }

                @Override
                public @Nullable DataType tuple(DataType a, DataType b, List<DataType> innerA, List<DataType> innerB) throws InternalException, UserException
                {
                    if (innerA.size() != innerB.size())
                    {
                        switch (relation)
                        {
                            case SYMMETRIC:
                                onError.accept("Tuples differ in size: " + innerA.size() + " vs " + innerB.size());
                                break;
                            case EXPECTED_A:
                                onError.accept("Expected tuple of size " + innerA.size() + " but found " + innerB.size());
                                break;
                        }
                        return null;
                    }
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
                    return visitor.tagged(a, b, a.taggedTypeName, a.tagTypeVariableSubstitutions, a.tagTypes, b.taggedTypeName, b.tagTypeVariableSubstitutions, b.tagTypes);
                case TUPLE:
                    return visitor.tuple(a, b, a.memberType, b.memberType);
                case ARRAY:
                    return visitor.array(a, b, a.memberType.isEmpty() ? null : a.memberType.get(0), b.memberType.isEmpty() ? null : b.memberType.get(0));
            }
            throw new InternalException("Missing kind case");
        }
    }

    public static class ColumnMaker<C extends EditableColumn, V> implements SimulationFunction<RecordSet, EditableColumn>
    {
        private final ExBiConsumer<C, V> addToColumn;
        private final ExFunction<DataParser, V> parseValue;
        private final BiFunctionInt<RecordSet, @Value V, C> makeColumn;
        private final @Value V defaultValue;
        private @Nullable C column;

        private ColumnMaker(@Value Object defaultValue, Class<V> valueClass, BiFunctionInt<RecordSet, @Value V, C> makeColumn, ExBiConsumer<C, V> addToColumn, ExFunction<DataParser, V> parseValue) throws UserException, InternalException
        {
            this.makeColumn = makeColumn;
            this.addToColumn = addToColumn;
            this.parseValue = parseValue;
            this.defaultValue = Utility.cast(defaultValue, valueClass);
        }

        public final EditableColumn apply(RecordSet rs) throws InternalException
        {
            column = makeColumn.apply(rs, defaultValue);
            return column;
        }

        // Only valid to call after apply:
        public void loadRow(DataParser p) throws InternalException, UserException
        {
            if (column == null)
                throw new InternalException("Calling loadRow before column creation");
            addToColumn.accept(column, parseValue.apply(p));
        }
    }

    @OnThread(Tag.Simulation)
    public SimulationFunction<RecordSet, EditableColumn> makeImmediateColumn(ColumnId columnId, List<@Value Object> value, @Value Object defaultValue) throws InternalException, UserException
    {
        return apply(new DataTypeVisitor<SimulationFunction<RecordSet, EditableColumn>>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return rs -> new MemoryNumericColumn(rs, columnId, displayInfo, Utility.mapListEx(value, Utility::valueNumber), Utility.cast(defaultValue, Number.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> text() throws InternalException, UserException
            {
                return rs -> new MemoryStringColumn(rs, columnId, Utility.mapListEx(value, Utility::valueString), Utility.cast(defaultValue, String.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return rs -> new MemoryTemporalColumn(rs, columnId, dateTimeInfo, Utility.mapListEx(value, Utility::valueTemporal), Utility.cast(defaultValue, TemporalAccessor.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> bool() throws InternalException, UserException
            {
                return rs -> new MemoryBooleanColumn(rs, columnId, Utility.mapListEx(value, Utility::valueBoolean), Utility.cast(defaultValue, Boolean.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return rs -> new MemoryTaggedColumn(rs, columnId, typeName, typeVars, tags, Utility.mapListEx(value, Utility::valueTagged), Utility.cast(defaultValue, TaggedValue.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return rs -> new MemoryTupleColumn(rs, columnId, inner, Utility.<@Value Object, @Value Object @Value[]>mapListEx(value, t -> Utility.valueTuple(t, inner.size())), Utility.cast(defaultValue, (Class<@Value Object[]>)Object[].class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot create column with empty array type");
                DataType innerFinal = inner;
                return rs -> new MemoryArrayColumn(rs, columnId, innerFinal, Utility.mapListEx(value, Utility::valueList), Utility.cast(defaultValue, ListEx.class));
            }
        });
    }

    @OnThread(Tag.Simulation)
    public ColumnMaker<?, ?> makeImmediateColumn(ColumnId columnId, @Value Object defaultValue) throws InternalException, UserException
    {
        return apply(new DataTypeVisitor<ColumnMaker<?, ?>>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryNumericColumn, Number>(defaultValue, Number.class, (rs, defaultValue) -> new MemoryNumericColumn(rs, columnId, displayInfo, Collections.emptyList(), defaultValue), (c, n) -> c.add(n), p -> {
                    return loadNumber(p);
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> text() throws InternalException, UserException
            {
                return new ColumnMaker<MemoryStringColumn, String>(defaultValue, String.class, (rs, defaultValue) -> new MemoryStringColumn(rs, columnId, Collections.emptyList(), defaultValue), (c, s) -> c.add(s), p -> {
                    return loadString(p);
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryTemporalColumn, TemporalAccessor>(defaultValue, TemporalAccessor.class, (rs, defaultValue) -> new MemoryTemporalColumn(rs, columnId, dateTimeInfo, Collections.emptyList(), defaultValue), (c, t) -> c.add(t), p ->
                {
                    return dateTimeInfo.parse(p);
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> bool() throws InternalException, UserException
            {
                return new ColumnMaker<MemoryBooleanColumn, Boolean>(defaultValue, Boolean.class, (rs, defaultValue) -> new MemoryBooleanColumn(rs, columnId, Collections.emptyList(), defaultValue), (c, b) -> c.add(b), p -> {
                    return loadBool(p);
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryTaggedColumn, TaggedValue>(defaultValue, TaggedValue.class, (rs, defaultValue) -> new MemoryTaggedColumn(rs, columnId, typeName, typeVars, tags, Collections.emptyList(), defaultValue), (c, t) -> c.add(t), p -> {
                    return loadTaggedValue(tags, p);
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryTupleColumn, @Value Object @Value[]>(defaultValue, (Class<@Value Object @Value[]>)Object[].class, (RecordSet rs, @Value Object @Value[] defaultValue) -> new MemoryTupleColumn(rs, columnId, inner, defaultValue), (c, t) -> c.add(t), p -> {
                    return loadTuple(inner, p, false);
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot have column with type of empty array");

                DataType innerFinal = inner;
                return new ColumnMaker<MemoryArrayColumn, ListEx>(defaultValue, ListEx.class, (rs, defaultValue) -> new MemoryArrayColumn(rs, columnId, innerFinal, Collections.emptyList(), defaultValue), (c, v) -> c.add(v), p -> {
                    return loadArray(innerFinal, p);
                });
            }
        });
    }

    // Tries a parse and if it fails, returns null.  Should only be used for single-token parses,
    // or those where you stop trying after failure, because I'm not sure what happens if you're partway
    // through a partial parse and it cancels.
    private static <T> @Nullable T tryParse(Supplier<@Nullable T> parseAction)
    {
        try
        {
            return parseAction.get();
        }
        catch (ParseCancellationException e)
        {
            return null;
        }
    }

    private static @Value ListEx loadArray(DataType innerFinal, DataParser p) throws UserException, InternalException
    {
        if (tryParse(() -> p.openSquare()) == null)
            throw new UserException("Expected array but found: \"" + p.getCurrentToken() + "\"");
        List<@Value Object> array = new ArrayList<>();
        boolean seenComma = true;
        while (tryParse(() -> p.closeSquare()) == null)
        {
            if (!seenComma)
            {
                throw new UserException("Expected comma but found " + p.getCurrentToken());
            }
            array.add(loadSingleItem(innerFinal, p, false));
            seenComma = tryParse(() -> p.comma()) != null;
        }
        return new ListExList(array);
    }

    private static @Value Object @Value[] loadTuple(ImmutableList<DataType> inner, DataParser p, boolean consumedInitialOpen) throws UserException, InternalException
    {
        if (!consumedInitialOpen)
        {
            if (tryParse(() -> p.openRound()) == null)
                throw new UserException("Expected tuple but found: \"" + p.getCurrentToken() + "\"");
        }
        @Value Object @Value[] tuple = DataTypeUtility.value(new Object[inner.size()]);
        for (int i = 0; i < tuple.length; i++)
        {
            tuple[i] = loadSingleItem(inner.get(i), p, false);
            if (i < tuple.length - 1)
            {
                if (tryParse(() -> p.comma()) == null)
                    throw new ParseException("comma", p);
            }
        }
        if (tryParse(() -> p.closeRound()) == null)
            throw new UserException("Expected tuple end but found: " + p.getCurrentToken());
        return tuple;
    }

    private static @Value Boolean loadBool(DataParser p) throws UserException
    {
        BoolContext b = tryParse(() -> p.bool());
        if (b == null)
            throw new UserException("Expected boolean value but found: \"" + p.getCurrentToken() + "\"");
        return DataTypeUtility.value(b.getText().trim().toLowerCase().equals("true"));
    }

    private static @Value String loadString(DataParser p) throws UserException
    {
        StringContext string = tryParse(() -> p.string());
        if (string == null)
            throw new ParseException("string", p);
        return DataTypeUtility.value(string.STRING().getText());
    }

    private static @Value Number loadNumber(DataParser p) throws UserException
    {
        NumberContext number = tryParse(() -> p.number());
        if (number == null)
            throw new UserException("Expected number value but found: \"" + p.getCurrentToken() + "\"");
        return DataTypeUtility.value(Utility.parseNumber(number.getText().trim()));
    }

    private static TaggedValue loadTaggedValue(List<TagType<DataType>> tags, DataParser p) throws UserException, InternalException
    {
        TagContext b = tryParse(() -> p.tag());
        if (b == null)
            throw new ParseException("tagged value", p);

        String constructor = b.STRING() != null ? b.STRING().getText() : b.UNQUOTED_IDENT().getText();
        for (int i = 0; i < tags.size(); i++)
        {
            TagType<DataType> tag = tags.get(i);
            if (tag.getName().equals(constructor))
            {
                @Nullable DataType innerType = tag.getInner();
                if (innerType != null)
                {
                    if (tryParse(() -> p.openRound()) == null)
                        throw new ParseException("Bracketed inner value for " + constructor, p);
                    @Value Object innerValue = loadSingleItem(innerType, p, true);
                    if (tryParse(() -> p.closeRound()) == null)
                        throw new ParseException("Closing tagged value bracket for " + constructor, p);
                    return new TaggedValue(i, innerValue);
                }

                return new TaggedValue(i, null);
            }
        }
        throw new UserException("Could not find matching tag for: \"" + constructor + "\" in: " + tags.stream().map(t -> "\"" + t.getName() + "\"").collect(Collectors.joining(", ")));
    }

    @OnThread(Tag.Any)
    public static @Value Object loadSingleItem(DataType type, final DataParser p, boolean consumedInitialOpen) throws InternalException, UserException
    {
        return type.apply(new DataTypeVisitor<@Value Object>()
        {
            @Override
            public @Value Object number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return loadNumber(p);
            }

            @Override
            public @Value Object text() throws InternalException, UserException
            {
                return loadString(p);
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                ParserRuleContext ctx = DataType.<@Nullable ParserRuleContext>tryParse(() -> {
                    switch (dateTimeInfo.getType())
                    {
                        case YEARMONTHDAY:
                            return p.ymd();
                        case YEARMONTH:
                            return p.ym();
                        case TIMEOFDAY:
                            return p.localTime();
                        //case TIMEOFDAYZONED:
                            //return p.offsetTime();
                        case DATETIME:
                            return p.localDateTime();
                        case DATETIMEZONED:
                            return p.zonedDateTime();
                    }
                    return null;
                });
                if (ctx == null)
                    throw new ParseException(dateTimeInfo.getType().toString(), p);
                DateTimeFormatter formatter = dateTimeInfo.getStrictFormatter();
                try
                {
                    return DataTypeUtility.value(dateTimeInfo, formatter.parse(ctx.getText().trim()));
                }
                catch (DateTimeParseException e)
                {
                    throw new UserException("Error loading date time \"" + ctx.getText() + "\", type " + dateTimeInfo.getType(), e);
                }
            }

            @Override
            public @Value Object bool() throws InternalException, UserException
            {
                return loadBool(p);
            }

            @Override
            public @Value Object tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return loadTaggedValue(tags, p);
            }

            @Override
            public @Value Object tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return loadTuple(inner, p, false);
            }

            @Override
            public @Value Object array(final @Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot load column with value of type empty array");

                return loadArray(inner, p);
            }
        });
    }

    // save the declaration of this type
    // If topLevelDeclaration is false, save a reference (matters for tagged types)
    public OutputBuilder save(OutputBuilder b) throws InternalException
    {
        apply(new DataTypeVisitorEx<UnitType, InternalException>()
        {
            @Override
            public UnitType number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                b.t(FormatLexer.NUMBER, FormatLexer.VOCABULARY);
                if (!numberInfo.getUnit().equals(Unit.SCALAR))
                    b.unit(numberInfo.getUnit().toString());
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
                    //case TIMEOFDAYZONED:
                        //b.t(FormatLexer.TIMEOFDAYZONED, FormatLexer.VOCABULARY);
                        //break;
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
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                b.t(FormatLexer.TAGGED, FormatLexer.VOCABULARY);
                b.quote(typeName);
                for (Either<Unit, DataType> typeVar : typeVars)
                {
                    typeVar.eitherInt(u -> b.unit(u.toString()), t -> {b.raw("("); t.save(b); return b.raw(")");});
                }
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType tuple(ImmutableList<DataType> inner) throws InternalException, InternalException
            {
                b.t(FormatLexer.OPEN_BRACKET, FormatLexer.VOCABULARY);
                boolean first = true;
                for (DataType dataType : inner)
                {
                    if (!first)
                        b.raw(",");
                    first = false;
                    dataType.save(b);
                }
                b.t(FormatLexer.CLOSE_BRACKET, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType array(@Nullable DataType inner) throws InternalException, InternalException
            {
                b.t(FormatLexer.OPEN_SQUARE, FormatLexer.VOCABULARY);
                if (inner != null)
                    inner.save(b);
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
            public Boolean tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return tags.stream().anyMatch(tt -> tt.getName().equals(tagName));
            }
        });
    }

    public static class DateTimeInfo
    {
        private static final Map<DateTimeType, DateTimeFormatter> STRICT_FORMATTERS = new HashMap<>();
        private static final Map<DateTimeType, ImmutableList<ImmutableList
        <DateTimeFormatter>>> FLEXIBLE_FORMATTERS = new HashMap<>();

        public DateTimeFormatter getStrictFormatter()
        {
            return STRICT_FORMATTERS.computeIfAbsent(getType(), DateTimeInfo::makeFormatter);
        }
        
        // Each inner list is a grouping that is likely to have
        // multiple matches and thus show up an ambiguous parse.
        public ImmutableList<ImmutableList<DateTimeFormatter>> getFlexibleFormatters()
        {
            return FLEXIBLE_FORMATTERS.computeIfAbsent(getType(), type -> {
                // Shared among some branches:
                ImmutableList.Builder<ImmutableList<DateTimeFormatter>> r = ImmutableList.builder();
                switch (type)
                {
                    case YEARMONTHDAY:
                        // All the formats here use space as a separator, and assume that
                        // the items have been fed through the pre-process function in here.
                        return ImmutableList.of(
                                l(m(" ", DAY, MONTH_TEXT_SHORT, YEAR4)), // dd MMM yyyy
                                l(m(" ", DAY, MONTH_TEXT_LONG, YEAR4)), // dd MMM yyyy

                                l(m(" ", MONTH_TEXT_SHORT, DAY, YEAR4)), // MMM dd yyyy
                                l(m(" ", MONTH_TEXT_LONG, DAY, YEAR4)), // MMM dd yyyy

                                l(m(" ", YEAR4, MONTH_TEXT_SHORT, DAY)), // yyyy MMM dd

                                l(m(" ", YEAR4, MONTH_NUM, DAY)), // yyyy MM dd

                                l(m(" ", DAY, MONTH_NUM, YEAR4), m(" ", MONTH_NUM, DAY, YEAR4)), // dd MM yyyy or MM dd yyyy

                                l(m(" ", DAY, MONTH_NUM, YEAR2), m(" ", MONTH_NUM, DAY, YEAR2)) // dd MM yy or MM dd yy
                        );
                    case TIMEOFDAY:
                        return ImmutableList.of(
                                l(m(":", HOUR, MIN, SEC_OPT, FRAC_SEC_OPT)), // HH:mm[:ss[.S]]
                                l(m(":", HOUR12, MIN, SEC_OPT, FRAC_SEC_OPT, AMPM)) // hh:mm[:ss[.S]] PM
                        );
                    case DATETIME:
                        for (List<DateTimeFormatter> timeFormats : new DateTimeInfo(DateTimeType.TIMEOFDAY).getFlexibleFormatters())
                        {
                            for (List<DateTimeFormatter> dateFormats : new DateTimeInfo(DateTimeType.YEARMONTHDAY).getFlexibleFormatters())
                            {
                                ImmutableList<DateTimeFormatter> newFormatsSpace = Utility.allPairs(dateFormats, timeFormats, (d, t) -> new DateTimeFormatterBuilder().append(d).appendLiteral(" ").append(t).toFormatter());
                                ImmutableList<DateTimeFormatter> newFormatsT = Utility.allPairs(dateFormats, timeFormats, (d, t) -> new DateTimeFormatterBuilder().append(d).appendLiteral("T").append(t).toFormatter());
                                r.add(newFormatsSpace);
                                r.add(newFormatsT);
                            }
                        }
                        return r.build();
                    case YEARMONTH:
                        return ImmutableList.of(
                            l(m(" ", F.MONTH_NUM, F.YEAR4)),
                            l(m(" ", F.MONTH_TEXT_SHORT, F.YEAR2)),
                            l(m(" ", F.MONTH_TEXT_SHORT, F.YEAR4)),
                            l(m(" ", F.MONTH_TEXT_LONG, F.YEAR4)),
                            l(m(" ", F.YEAR4, F.MONTH_NUM))
                        );
                    case DATETIMEZONED:
                        for (ImmutableList<DateTimeFormatter> dateTimeFormats : new DateTimeInfo(DateTimeType.DATETIME).getFlexibleFormatters())
                        {
                            r.add(Utility.<DateTimeFormatter, DateTimeFormatter>mapListI(dateTimeFormats, dateTimeFormat -> {
                                DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                                b.optionalStart().appendLiteral(" ").optionalEnd();
                                b.appendZoneRegionId();
                                return b.toFormatter();
                            }));
                            r.add(Utility.<DateTimeFormatter, DateTimeFormatter>mapListI(dateTimeFormats, dateTimeFormat -> {
                                DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                                b.optionalStart().appendLiteral(" ").optionalEnd();
                                b.appendZoneText(TextStyle.SHORT);
                                return b.toFormatter();
                            }));
                            r.add(Utility.<DateTimeFormatter, DateTimeFormatter>mapListI(dateTimeFormats, dateTimeFormat -> {
                                DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                                b.optionalStart().appendLiteral(" ").optionalEnd();
                                b.appendOffsetId().appendLiteral("[").appendZoneRegionId().appendLiteral("]");
                                return b.toFormatter();
                            }));
                        }
                        return r.build();
                        /*
                    case TIMEOFDAYZONED:
                        for (List<DateTimeFormatter> formatters : new DateTimeInfo(DateTimeType.TIMEOFDAY).getFlexibleFormatters())
                        {
                            ImmutableList.Builder<DateTimeFormatter> inner = ImmutableList.builder();
                            for (DateTimeFormatter dateTimeFormat : formatters)
                            {
                                // Since we don't have a date, a named zone doesn't help, only a specific
                                // offset time is valid:
                                DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                                b.optionalStart().appendLiteral(" ").optionalEnd();
                                b.appendOffsetId();
                                inner.add(b.toFormatter());
                            }
                            r.add(inner.build());
                        }
                        return r.build();
                        */
                }
                return ImmutableList.of(ImmutableList.of(getStrictFormatter()));
            });
        }

        // public for testing
        public static enum F {FRAC_SEC_OPT, SEC_OPT, MIN, HOUR, HOUR12, AMPM, DAY, MONTH_TEXT_SHORT, MONTH_TEXT_LONG, MONTH_NUM, YEAR2, YEAR4 }

        // public for testing
        public static DateTimeFormatter m(String sep, F... items)
        {
            DateTimeFormatterBuilder b = new DateTimeFormatterBuilder();
            for (int i = 0; i < items.length; i++)
            {
                switch (items[i])
                {
                    case FRAC_SEC_OPT:
                        // From http://stackoverflow.com/questions/30090710/java-8-datetimeformatter-parsing-for-optional-fractional-seconds-of-varying-sign
                        b.appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true);
                        break;
                    case SEC_OPT:
                        b.optionalStart();
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.SECOND_OF_MINUTE, 2, 2, SignStyle.NEVER).optionalEnd();
                        break;
                    case MIN:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.MINUTE_OF_HOUR, 2, 2, SignStyle.NEVER);
                        break;
                    case HOUR:
                        b.appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NEVER);
                        break;
                    case HOUR12:
                        b.appendValue(ChronoField.CLOCK_HOUR_OF_AMPM, 1, 2, SignStyle.NEVER);
                        break;
                    case AMPM:
                        b.optionalStart().appendLiteral(" ").optionalEnd().appendText(ChronoField.AMPM_OF_DAY);
                        break;
                    case DAY:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NEVER);
                        break;
                    case MONTH_TEXT_SHORT:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT);
                        break;
                    case MONTH_TEXT_LONG:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendText(ChronoField.MONTH_OF_YEAR, TextStyle.FULL);
                        break;
                    case MONTH_NUM:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NEVER);
                        break;
                    case YEAR2:
                        if (i != 0) b.appendLiteral(sep);
                        // From http://stackoverflow.com/questions/29490893/parsing-string-to-local-date-doesnt-use-desired-century
                        b.appendValueReduced(ChronoField.YEAR, 2, 2, Year.now().getValue() - 80);
                        break;
                    case YEAR4:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.YEAR, 4, 4, SignStyle.NEVER);
                        break;
                }
            }
            return b.toFormatter();
        }

        static ImmutableList<DateTimeFormatter> l(DateTimeFormatter... args)
        {
            return ImmutableList.copyOf(args);
        }


        private static DateTimeFormatter makeFormatter(DateTimeType type)
        {
            DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
            if (type.hasYearMonth())
            {
                builder.appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                    .appendLiteral('-')
                    .appendValue(MONTH_OF_YEAR, 2);
            }
            if (type.hasDay())
                builder.appendLiteral('-').appendValue(DAY_OF_MONTH, 2);
            if ((type.hasYearMonth() || type.hasDay()) && type.hasTime())
                builder.appendLiteral(' ');
            if (type.hasTime())
                builder.appendValue(HOUR_OF_DAY, 2)
                    .appendLiteral(':')
                    .appendValue(MINUTE_OF_HOUR, 2)
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(SECOND_OF_MINUTE, 2)
                    .optionalStart()
                    .appendFraction(NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .optionalEnd();
            if (type.hasZoneOffset())
                builder.appendOffsetId();
            if (type.hasZoneId())
                builder
                    .appendLiteral(' ')
                    .parseCaseSensitive()
                    .appendZoneOrOffsetId();
            return builder.toFormatter();
        }

        /**
         * This parses in two senses.  First, it parses using the provided DataParser.
         * We don't actually use that directly to get the value info out, instead
         * feeling it to the formatter.  This may seem odd, but we already have the formatter.
         * However, the parser needs to know what data tokens got consumed to parse
         * the next item, which is not easily extracted out of the data/time formatter.
         * Hence this slightly odd double layer.
         * @return
         */
        public TemporalAccessor parse(DataParser p) throws UserException, InternalException
        {
            ParserRuleContext c = tryParse(() -> {
                switch (type)
                {
                    case YEARMONTHDAY:
                        return p.ymd();
                    case YEARMONTH:
                        return p.ym();
                    case TIMEOFDAY:
                        return p.localTime();
                    //case TIMEOFDAYZONED:
                        //return p.offsetTime();
                    case DATETIME:
                        return p.localDateTime();
                    case DATETIMEZONED:
                        return p.zonedDateTime();
                }
                return null;
            });
            if (c == null)
                throw new ParseException("Date value ", p);
            DateTimeFormatter formatter = getStrictFormatter();
            try
            {
                return fromParsed(formatter.parse(c.getText().trim()));
            }
            catch (DateTimeParseException e)
            {
                throw new UserException("Problem reading date/time of type " + getType() + " from {" + c.getText() + "}", e);
            }
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
            //TIMEOFDAYZONED,
            /** LocalDateTime */
            DATETIME,
            /** ZonedDateTime */
            DATETIMEZONED;

            public boolean hasDay()
            {
                switch (this)
                {
                    case YEARMONTHDAY:
                    case DATETIME:
                    case DATETIMEZONED:
                        return true;
                    default:
                        return false;
                }
            }

            public boolean hasYearMonth()
            {
                switch (this)
                {
                    case YEARMONTH:
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
                switch (this)
                {
                    case TIMEOFDAY:
                    //case TIMEOFDAYZONED:
                    case DATETIME:
                    case DATETIMEZONED:
                        return true;
                    default:
                        return false;
                }
            }


            public boolean hasZoneOffset()
            {
                return false; //this == TIMEOFDAYZONED;
            }

            public boolean hasZoneId()
            {
                return this == DATETIMEZONED;
            }
        }

        private final DateTimeType type;

        public DateTimeInfo(DateTimeType type)
        {
            this.type = type;
        }

        public static final TemporalAccessor DEFAULT_VALUE = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

        public @Value TemporalAccessor getDefaultValue() throws InternalException
        {
            switch (type)
            {
                case YEARMONTHDAY:
                    return LocalDate.from(DEFAULT_VALUE);
                case YEARMONTH:
                    return YearMonth.from(DEFAULT_VALUE);
                case TIMEOFDAY:
                    return LocalTime.from(DEFAULT_VALUE);
                //case TIMEOFDAYZONED:
                //    return OffsetTime.from(DEFAULT_VALUE);
                case DATETIME:
                    return LocalDateTime.from(DEFAULT_VALUE);
                case DATETIMEZONED:
                    return  ZonedDateTime.from(DEFAULT_VALUE);
            }
            throw new InternalException("Unknown type: " + type);
        }

        public @Value TemporalAccessor fromParsed(TemporalAccessor t) throws InternalException
        {
            switch (type)
            {
                case YEARMONTHDAY:
                    return DataTypeUtility.value(this, LocalDate.from(t));
                case YEARMONTH:
                    return DataTypeUtility.value(this, YearMonth.from(t));
                case TIMEOFDAY:
                    return DataTypeUtility.value(this, LocalTime.from(t));
                //case TIMEOFDAYZONED:
                //    return DataTypeUtility.value(this, OffsetTime.from(t));
                case DATETIME:
                    return DataTypeUtility.value(this, LocalDateTime.from(t));
                case DATETIMEZONED:
                    return DataTypeUtility.value(this, ZonedDateTime.from(t));
            }
            throw new InternalException("Unknown type: " + type);
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


        /**
         * Gets a comparator for this particular temporal type.
         * @param pool    If true, give a comparator suitable for object pools.  Here, we should only
         *                return zero iff equals(..) is true and the values are completely equal.
         *                If false, give a comparator for user
         *                code.  You can particularly see the difference for OffsetTime, where
         *                11:21+00:00 and 10:21-01:00 are equals for user comparison purposes, but not
         *                for the object pool, since they are not the same value and need to be stored
         *                differently.
         * @return
         */
        public Comparator<@UnknownIfValue TemporalAccessor> getComparator(boolean pool) throws InternalException
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
                /*
                case TIMEOFDAYZONED:
                    return Comparator.comparing((TemporalAccessor t) -> {
                            if (pool)
                                return OffsetTime.from(t);
                            else
                                return OffsetTime.from(t).withOffsetSameInstant(ZoneOffset.UTC);
                        },
                        Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.OFFSET_SECONDS))
                    );
                */
                case DATETIME:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND));
                case DATETIMEZONED:
                    return Comparator.comparing((TemporalAccessor t) -> {
                            if (pool)
                                return ZonedDateTime.from(t);
                            else
                                return ZonedDateTime.from(t).withZoneSameInstant(ZoneOffset.UTC);

                        },
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

        R tagged(T a, T b, TypeId typeNameA, ImmutableList<Either<Unit, DataType>> varsA, List<TagType<DataType>> tagsA, TypeId typeNameB, ImmutableList<Either<Unit, DataType>> varsB, List<TagType<DataType>> tagsB) throws InternalException, UserException;
        R tuple(T a, T b, List<DataType> innerA, List<DataType> innerB) throws InternalException, UserException;
        // If null, array is empty and thus of unknown type
        R array(T a, T b, @Nullable DataType innerA, @Nullable DataType innerB) throws InternalException, UserException;

        R differentKind(T a, T b) throws InternalException, UserException;
    }
}
