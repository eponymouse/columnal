package records.data.datatype;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.ColumnId;
import records.data.ColumnStorage;
import records.data.MemoryArrayColumn;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.MemoryTupleColumn;
import records.data.RecordSet;
import records.data.TaggedValue;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.unit.Unit;
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
import utility.ExConsumer;
import utility.Pair;
import utility.UnitType;
import utility.Utility;

import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

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
    // For TUPLE (2+) and ARRAY (1):
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
    public static final DataType INTEGER = NUMBER;
    public static final DataType BOOLEAN = new DataType(Kind.BOOLEAN, null, null, null, null);
    public static final DataType TEXT = new DataType(Kind.TEXT, null, null, null, null);
    public static final DataType DATE = DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY));

    public static DataType array(DataType inner)
    {
        return new DataType(Kind.ARRAY, null, null, null, Collections.singletonList(inner));
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
        R array(DataType inner) throws InternalException, E;
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
        public R array(DataType inner) throws InternalException, UserException
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
    public String getHeaderDisplay() throws UserException, InternalException
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
                if (tags.size() == 1)
                {
                    return tags.get(0).toString();
                }
                // Look for one tag plus one with content:
                if (tags.size() == 2)
                {
                    return tags.get(0) + "|" + tags.get(1);
                }
                return "...";
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
            public String array(DataType inner) throws InternalException, UserException
            {
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

    // For arrays: single item.  For tuples, the set of contained types.
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

    public static <T extends DataType> @Nullable T checkSame(@Nullable T a, @Nullable T b, ExConsumer<String> onError) throws UserException, InternalException
    {
        ArrayList<T> ts = new ArrayList<T>();
        if (a == null || b == null)
        {
            return null;
        }
        ts.add(a);
        ts.add(b);
        return checkAllSame(ts, onError);
    }


    public static <T extends DataType> @Nullable T checkAllSame(List<T> types, ExConsumer<String> onError) throws InternalException, UserException
    {
        HashSet<T> noDups = new HashSet<>(types);
        if (noDups.size() == 1)
            return noDups.iterator().next();
        onError.accept("Differing types: " + noDups.stream().map(Object::toString).collect(Collectors.joining(" and ")));
        return null;
    }

    @OnThread(Tag.Simulation)
    public Column makeImmediateColumn(RecordSet rs, ColumnId columnId, List<List<ItemContext>> allData, int columnIndex) throws InternalException, UserException
    {
        return apply(new DataTypeVisitor<Column>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public Column number(NumberInfo displayInfo) throws InternalException, UserException
            {
                List<String> column = new ArrayList<>(allData.size());
                for (List<ItemContext> row : allData)
                {
                    DataParser.NumberContext number = row.get(columnIndex).number();
                    if (number == null)
                        throw new UserException("Expected string value but found: \"" + row.get(columnIndex).getText() + "\"");
                    column.add(number.getText());
                }
                return new MemoryNumericColumn(rs, columnId, displayInfo, column.stream());
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column text() throws InternalException, UserException
            {
                List<String> column = new ArrayList<>(allData.size());
                for (List<ItemContext> row : allData)
                {
                    StringContext string = row.get(columnIndex).string();
                    if (string == null)
                        throw new UserException("Expected string value but found: \"" + row.get(columnIndex).getText() + "\"");
                    column.add(string.getText());
                }
                return new MemoryStringColumn(rs, columnId, column);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                List<TemporalAccessor> values = new ArrayList<>(allData.size());
                for (List<ItemContext> row : allData)
                {
                    StringContext c = row.get(columnIndex).string();
                    if (c == null)
                        throw new UserException("Expected quoted date value but found: \"" + row.get(columnIndex).getText() + "\"");
                    values.add(LocalDate.parse(c.getText()));
                }
                return new MemoryTemporalColumn(rs, columnId, dateTimeInfo, values);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column bool() throws InternalException, UserException
            {
                List<Boolean> values = new ArrayList<>(allData.size());
                for (List<ItemContext> row : allData)
                {
                    BoolContext b = row.get(columnIndex).bool();
                    if (b == null)
                        throw new UserException("Expected boolean value but found: \"" + row.get(columnIndex).getText() + "\"");
                    values.add(b.getText().equals("true"));
                }
                return new MemoryBooleanColumn(rs, columnId, values);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                List<TaggedValue> values = new ArrayList<>(allData.size());
                for (List<ItemContext> row : allData)
                {
                    TaggedContext b = row.get(columnIndex).tagged();
                    if (b == null)
                        throw new UserException("Expected tagged value but found: \"" + row.get(columnIndex).getText() + "\"");
                    values.add(loadValue(tags, b));
                }
                return new MemoryTaggedColumn(rs, columnId, typeName, tags, values);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tuple(List<DataType> inner) throws InternalException, UserException
            {
                List<Object[]> values = new ArrayList<>(allData.size());
                for (List<ItemContext> row : allData)
                {
                    TupleContext c = row.get(columnIndex).tuple();
                    if (c == null)
                        throw new UserException("Expected tuple but found: \"" + row.get(columnIndex).getText() + "\"");
                    if (c.item().size() != inner.size())
                        throw new UserException("Wrong number of tuples items; expected " + inner.size() + " but found " + c.item().size());
                    Object[] tuple = new Object[inner.size()];
                    for (int i = 0; i < tuple.length; i++)
                    {
                        tuple[i] = loadSingleItem(inner.get(i), c.item(i));
                    }
                    values.add(tuple);
                }
                return new MemoryTupleColumn(rs, columnId, inner, values);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column array(DataType inner) throws InternalException, UserException
            {
                List<Pair<Integer, DataTypeValue>> values = new ArrayList<>(allData.size());
                for (List<ItemContext> row : allData)
                {
                    ArrayContext c = row.get(columnIndex).array();
                    if (c == null)
                        throw new UserException("Expected array but found: \"" + row.get(columnIndex).getText() + "\"");
                    List<Object> array = new ArrayList<>();
                    for (ItemContext itemContext : c.item())
                    {
                        array.add(loadSingleItem(inner, itemContext));
                    }
                    ColumnStorage storage = DataTypeUtility.makeColumnStorage(inner);
                    storage.addAll(array);
                    values.add(new Pair<>(array.size(), storage.getType()));
                }
                return new MemoryArrayColumn(rs, columnId, inner, values);
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
    private static @Value Object loadSingleItem(DataType type, final ItemContext item) throws InternalException, UserException
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
                try
                {
                    return Utility.value(LocalDate.parse(string.getText()));
                }
                catch (DateTimeParseException e)
                {
                    throw new UserException("Error loading date time \"" + string.getText() + "\"", e);
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
            public @Value Object array(DataType inner) throws InternalException, UserException
            {
                return Utility.value(Utility.<ItemContext, @Value Object>mapListEx(item.array().item(), entry -> loadSingleItem(inner, entry)));
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
                b.unit("");
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
                b.t(FormatLexer.DATE, FormatLexer.VOCABULARY);
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
                b.t(FormatLexer.OPEN_BRACKET);
                for (DataType dataType : inner)
                {
                    dataType.save(b, false);
                }
                b.t(FormatLexer.CLOSE_BRACKET);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public UnitType array(DataType inner) throws InternalException, InternalException
            {
                b.t(FormatLexer.OPEN_SQUARE);
                inner.save(b, false);
                b.t(FormatLexer.CLOSE_SQUARE);
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


        public Comparator<TemporalAccessor> getComparator() throws InternalException
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
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE));
                case TIMEOFDAYZONED:
                    return Comparator.comparing((TemporalAccessor t) -> OffsetTime.from(t).withOffsetSameInstant(ZoneOffset.UTC),
                        Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                    );
                case DATETIME:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE));
                case DATETIMEZONED:
                    return Comparator.comparing((TemporalAccessor t) -> ZonedDateTime.from(t).withZoneSameInstant(ZoneOffset.UTC),
                        Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                    );
            }
            throw new InternalException("Unknown date type: " + type);
        }
    }
}
