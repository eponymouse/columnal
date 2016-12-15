package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.ColumnId;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.RecordSet;
import records.data.columntype.NumericColumnType;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.DataParser;
import records.grammar.DataParser.BoolContext;
import records.grammar.DataParser.ItemContext;
import records.grammar.DataParser.StringContext;
import records.grammar.DataParser.TaggedContext;
import records.grammar.FormatLexer;
import records.grammar.FormatParser.NumberContext;
import records.grammar.FormatParser.TypeContext;
import records.loadsave.OutputBuilder;
import records.transformations.expression.TypeState;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExConsumer;
import utility.Pair;
import utility.UnitType;
import utility.Utility;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
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
    public static enum Kind {NUMBER, TEXT, DATETIME, BOOLEAN, TAGGED }
    final Kind kind;
    final @Nullable String taggedTypeName;
    final @Nullable NumberInfo numberInfo;
    final @Nullable DateTimeInfo dateTimeInfo;
    final @Nullable List<TagType<DataType>> tagTypes;

    DataType(Kind kind, @Nullable NumberInfo numberInfo, @Nullable DateTimeInfo dateTimeInfo, @Nullable Pair<String, List<TagType<DataType>>> tagInfo)
    {
        this.kind = kind;
        this.numberInfo = numberInfo;
        this.dateTimeInfo = dateTimeInfo;
        this.taggedTypeName = tagInfo == null ? null : tagInfo.getFirst();
        this.tagTypes = tagInfo == null ? null : tagInfo.getSecond();
    }

    public static final DataType NUMBER = DataType.number(NumberInfo.DEFAULT);
    public static final DataType INTEGER = NUMBER;
    public static final DataType BOOLEAN = new DataType(Kind.BOOLEAN, null, null, null);
    public static final DataType TEXT = new DataType(Kind.TEXT, null, null, null);
    public static final DataType DATE = DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY));

    public static DataType date(DateTimeInfo dateTimeInfo)
    {
        return new DataType(Kind.DATETIME, null, dateTimeInfo, null);
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

        R tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, E;
        //R tuple() throws InternalException, E;

        //R array() throws InternalException, E;
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
        public R tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
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
            public String tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
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

    public static DataType tagged(String name, List<TagType<DataType>> tagTypes)
    {
        return new DataType(Kind.TAGGED, null, null, new Pair<>(name, tagTypes));
    }


    public static DataType number(NumberInfo numberInfo)
    {
        return new DataType(Kind.NUMBER, numberInfo, null, null);
    }

    public static boolean canFitInOneNumeric(List<? extends TagType> tags) throws InternalException, UserException
    {
        // Can fit in one numeric if there is no inner types,
        // or if the only inner type is a single numeric
        boolean foundNumeric = false;
        for (TagType t : tags)
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

    // Only call if canFitInOneNumeric returned true
    public static int findNumericTag(List<TagType> tags) throws InternalException, UserException
    {
        // Can fit in one numeric if there is no inner types,
        // or if the only inner type is a single numeric
        for (int i = 0; i < tags.size(); i++)
        {
            TagType t = tags.get(i);
            if (t.getInner() != null)
            {
                if (t.getInner().kind == Kind.NUMBER)
                {
                    return i;
                }
            }
        }
        return -1;
    }


    @OnThread(Tag.Any)
    public DataTypeValue copy(GetValue<List<Object>> get) throws UserException, InternalException
    {
        return copy(get, 0);
    }

    @OnThread(Tag.Any)
    private DataTypeValue copy(GetValue<List<Object>> get, int curIndex) throws UserException, InternalException
    {
        @Nullable Pair<String, List<TagType<DataTypeValue>>> newTagTypes = null;
        if (this.taggedTypeName != null && this.tagTypes != null)
        {
            newTagTypes = new Pair<>(taggedTypeName, new ArrayList<>());
            for (TagType tagType : this.tagTypes)
                newTagTypes.getSecond().add(new TagType<>(tagType.getName(), tagType.getInner() == null ? null : tagType.getInner().copy(get, curIndex + 1)));
        }
        return new DataTypeValue(kind, numberInfo, dateTimeInfo, newTagTypes,
            (i, prog) -> (Number)get.getWithProgress(i, prog).get(curIndex),
            (i, prog) -> (String)get.getWithProgress(i, prog).get(curIndex),
            (i, prog) -> (Temporal) get.getWithProgress(i, prog).get(curIndex),
            (i, prog) -> (Boolean) get.getWithProgress(i, prog).get(curIndex),
            (i, prog) -> (Integer) get.getWithProgress(i, prog).get(curIndex));
    }

    public boolean isTagged()
    {
        return kind == Kind.TAGGED;
    }

    public @Nullable String getTaggedTypeName()
    {
        return taggedTypeName;
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
                return new MemoryNumericColumn(rs, columnId, new NumericColumnType(displayInfo.getUnit(), displayInfo.getMinimumDP(), false), column);
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
                List<Temporal> values = new ArrayList<>(allData.size());
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
            public Column tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                List<List<Object>> values = new ArrayList<>(allData.size());
                for (List<ItemContext> row : allData)
                {
                    TaggedContext b = row.get(columnIndex).tagged();
                    if (b == null)
                        throw new UserException("Expected tagged value but found: \"" + row.get(columnIndex).getText() + "\"");
                    values.add(loadValue(tags, b));
                }
                return new MemoryTaggedColumn(rs, columnId, typeName, tags, values);
            }
        });
    }

    private static List<Object> loadValue(List<TagType<DataType>> tags, TaggedContext taggedContext) throws UserException, InternalException
    {
        String constructor = taggedContext.tag().getText();
        for (int i = 0; i < tags.size(); i++)
        {
            TagType<DataType> tag = tags.get(i);
            if (tag.getName().equals(constructor))
            {
                List<Object> r = new ArrayList<>();
                r.add((Integer)i);
                ItemContext item = taggedContext.item();
                if (tag.getInner() != null)
                {
                    if (item == null)
                        throw new UserException("Expected inner type but found no inner value: \"" + taggedContext.getText() + "\"");
                    r.addAll(tag.getInner().apply(new DataTypeVisitor<List<Object>>()
                    {
                        @Override
                        public List<Object> number(NumberInfo displayInfo) throws InternalException, UserException
                        {
                            DataParser.NumberContext number = item.number();
                            if (number == null)
                                throw new UserException("Expected number, found: " + item.getText() + item.getStart());
                            return Collections.singletonList(Utility.parseNumber(number.getText()));
                        }

                        @Override
                        public List<Object> text() throws InternalException, UserException
                        {
                            StringContext string = item.string();
                            if (string == null)
                                throw new UserException("Expected string, found: " + item.getText() + item.getStart());
                            return Collections.singletonList(string.getText());
                        }

                        @Override
                        public List<Object> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                        {
                            StringContext string = item.string();
                            if (string == null)
                                throw new UserException("Expected quoted date, found: " + item.getText() + item.getStart());
                            try
                            {
                                return Collections.singletonList(LocalDate.parse(string.getText()));
                            }
                            catch (DateTimeParseException e)
                            {
                                throw new UserException("Error loading date time \"" + string.getText() + "\"", e);
                            }
                        }

                        @Override
                        public List<Object> bool() throws InternalException, UserException
                        {
                            BoolContext bool = item.bool();
                            if (bool == null)
                                throw new UserException("Expected bool, found: " + item.getText() + item.getStart());
                            return Collections.singletonList(bool.getText().equals("true"));
                        }

                        @Override
                        public List<Object> tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
                        {
                            return loadValue(tags, item.tagged());
                        }
                    }));
                }
                else if (item != null)
                    throw new UserException("Expected no inner type but found inner value: \"" + taggedContext.getText() + "\"");

                return r;
            }
        }
        throw new UserException("Could not find matching tag for: \"" + taggedContext.tag().getText() + "\" in: " + tags.stream().map(t -> t.getName()).collect(Collectors.joining(", ")));

    }

    @OnThread(Tag.FXPlatform)
    public OutputBuilder save(OutputBuilder b) throws InternalException
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
            public UnitType tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, InternalException
            {
                b.t(FormatLexer.TAGGED, FormatLexer.VOCABULARY).t(FormatLexer.OPEN_BRACKET, FormatLexer.VOCABULARY);
                for (TagType<DataType> tag : tags)
                {
                    b.kw("\\" + b.quotedIfNecessary(tag.getName()) + (tag.getInner() != null ? ":" : ""));
                    if (tag.getInner() != null)
                        tag.getInner().apply(this);
                }
                b.t(FormatLexer.CLOSE_BRACKET, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }
        });
        return b;
    }


    public static DataType loadType(UnitManager mgr, TypeState typeState, TypeContext type) throws InternalException, UserException
    {
        if (type.BOOLEAN() != null)
            return BOOLEAN;
        else if (type.TEXT() != null)
            return TEXT;
        else if (type.DATE() != null)
            return DATE;
        else if (type.number() != null)
        {
            NumberContext n = type.number();
            Unit unit = mgr.loadUse(n.UNIT().getText());
            return DataType.number(new NumberInfo(unit, Integer.valueOf(n.DIGITS().getText())));
        }
        else if (type.tagRef() != null)
        {
            //TODO move this to type loading code (alongside unit loading?)
            /*
            List<TagType<DataType>> tags = new ArrayList<>();
            for (TagItemContext item : type.tagged().tagItem())
            {
                String tagName = item.constructor().getText();

                if (tags.stream().anyMatch(t -> t.getName().equals(tagName)))
                    throw new UserException("Duplicate tag names in format: \"" + tagName + "\"");

                if (item.type() != null)
                    tags.add(new TagType<DataType>(tagName, loadType(mgr, typeState, item.type())));
                else
                    tags.add(new TagType<DataType>(tagName, null));
            }

            return new DataType.tagged(tags);
            */
            DataType taggedType = typeState.findTaggedType(type.tagRef().getText());
            if (taggedType == null)
                throw new UserException("Undeclared tagged type: \"" + type.tagRef().getText() + "\"");
            return taggedType;
        }
        else
            throw new InternalException("Unrecognised case: " + type);
    }


    public boolean hasTag(String tagName) throws UserException, InternalException
    {
        return apply(new SpecificDataTypeVisitor<Boolean>() {
            @Override
            public Boolean tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return tags.stream().anyMatch(tt -> tt.getName().equals(tagName));
            }
        });
    }

    public static class DateTimeInfo
    {
        public static enum DateTimeType
        {
            YEARMONTHDAY, YEARMONTH, TIMEOFDAY, TIMEOFDAYZONED, DATETIME, DATETIMEZONED;
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
    }
}
