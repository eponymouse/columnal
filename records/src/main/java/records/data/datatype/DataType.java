package records.data.datatype;

import edu.emory.mathcs.backport.java.util.Arrays;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.K;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.DataParser.ItemContext;
import records.grammar.DataParser.StringContext;
import records.grammar.FormatLexer;
import records.grammar.FormatParser.ATypeContext;
import records.grammar.FormatParser.NumberContext;
import records.grammar.FormatParser.TagItemContext;
import records.grammar.FormatParser.TypeContext;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExConsumer;
import utility.Pair;
import utility.UnitType;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
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
    public static enum Kind {NUMBER, TEXT, DATE, BOOLEAN, TAGGED }
    final Kind kind;
    final @Nullable NumberDisplayInfo numberDisplayInfo;
    final @Nullable List<TagType<DataType>> tagTypes;

    DataType(Kind kind, @Nullable NumberDisplayInfo numberDisplayInfo, @Nullable List<TagType<DataType>> tagTypes)
    {
        this.kind = kind;
        this.numberDisplayInfo = numberDisplayInfo;
        this.tagTypes = tagTypes;
    }

    public static final DataType NUMBER = new DataType(Kind.NUMBER, NumberDisplayInfo.DEFAULT, null);
    public static final DataType INTEGER = NUMBER;
    public static final DataType BOOLEAN = new DataType(Kind.BOOLEAN, null, null);
    public static final DataType TEXT = new DataType(Kind.TEXT, null, null);
    public static final DataType DATE = new DataType(Kind.DATE, null, null);

    public static class NumberDisplayInfo
    {
        private final String displayPrefix;
        private final int minimumDP;

        public NumberDisplayInfo(String displayPrefix, int minimumDP)
        {
            this.displayPrefix = displayPrefix;
            this.minimumDP = minimumDP;
        }

        public static final NumberDisplayInfo DEFAULT = new NumberDisplayInfo("", 0);

        public String getDisplayPrefix()
        {
            return displayPrefix;
        }

        public int getMinimumDP()
        {
            return minimumDP;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            NumberDisplayInfo that = (NumberDisplayInfo) o;

            if (minimumDP != that.minimumDP) return false;
            return displayPrefix.equals(that.displayPrefix);

        }

        @Override
        public int hashCode()
        {
            int result = displayPrefix.hashCode();
            result = 31 * result + minimumDP;
            return result;
        }
    }

    public static interface DataTypeVisitorEx<R, E extends Throwable>
    {
        R number(NumberDisplayInfo displayInfo) throws InternalException, E;
        R text() throws InternalException, E;
        R date() throws InternalException, E;
        R bool() throws InternalException, E;

        R tagged(List<TagType<DataType>> tags) throws InternalException, E;
        //R tuple() throws InternalException, E;

        //R array() throws InternalException, E;
    }

    public static interface DataTypeVisitor<R> extends DataTypeVisitorEx<R, UserException>
    {
        
    }

    public static class SpecificDataTypeVisitor<R> implements DataTypeVisitor<R>
    {
        @Override
        public R number(NumberDisplayInfo displayInfo) throws InternalException, UserException
        {
            throw new InternalException("Unexpected number data type");
        }

        @Override
        public R text() throws InternalException, UserException
        {
            throw new InternalException("Unexpected text data type");
        }

        @Override
        public R tagged(List<TagType<DataType>> tags) throws InternalException, UserException
        {
            throw new InternalException("Unexpected tagged data type");
        }

        @Override
        public R bool() throws InternalException
        {
            throw new InternalException("Unexpected boolean type");
        }

        @Override
        public R date() throws InternalException, UserException
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
                return visitor.number(numberDisplayInfo);
            case TEXT:
                return visitor.text();
            case DATE:
                return visitor.date();
            case BOOLEAN:
                return visitor.bool();
            case TAGGED:
                return visitor.tagged(tagTypes);
            default:
                throw new InternalException("Missing kind case");
        }
    }

    public static class TagType<T extends DataType>
    {
        private final String name;
        private final @Nullable T inner;
        private final int extra;

        public TagType(String name, @Nullable T inner)
        {
            this(name, inner, -1);
        }

        public TagType(String name, @Nullable T inner, int extra)
        {
            this.name = name;
            this.inner = inner;
            this.extra = extra;
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

        public int getExtra()
        {
            return extra;
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
            public String number(NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return "Number";
            }

            @Override
            public String text() throws InternalException, UserException
            {
                return "Text";
            }

            @Override
            public String tagged(List<TagType<DataType>> tags) throws InternalException, UserException
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
            public String date() throws InternalException, UserException
            {
                return "Date";
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
        @Nullable List<TagType<DataTypeValue>> newTagTypes = null;
        if (this.tagTypes != null)
        {
            newTagTypes = new ArrayList<>();
            for (TagType tagType : this.tagTypes)
                newTagTypes.add(new TagType<>(tagType.getName(), tagType.getInner() == null ? null : tagType.getInner().copy(get, curIndex + 1)));
        }
        return new DataTypeValue(kind, numberDisplayInfo, newTagTypes,
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


    // Returns (true, null) if there's no inner type.  Returns (false, null) if no such constructor.
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

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || !(o instanceof DataType)) return false;

        DataType dataType = (DataType) o;

        if (kind != dataType.kind) return false;
        return tagTypes != null ? tagTypes.equals(dataType.tagTypes) : dataType.tagTypes == null;
    }

    @Override
    public int hashCode()
    {
        int result = kind.hashCode();
        result = 31 * result + (tagTypes != null ? tagTypes.hashCode() : 0);
        return result;
    }

    public NumberDisplayInfo getNumberDisplayInfo() throws InternalException
    {
        if (numberDisplayInfo != null)
            return numberDisplayInfo;
        else
            throw new InternalException("Requesting numeric display info for non-numeric type: " + this);
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
        //TODO other types
        List<String> column = new ArrayList<>(allData.size());
        for (List<ItemContext> row : allData)
        {
            StringContext value = row.get(columnIndex).string();
            if (value == null)
                throw new UserException("Expected string value but found: \"" + row.get(columnIndex).getText() + "\"");
            column.add(value.getText());
        }
        return new MemoryStringColumn(rs, columnId, column);
    }

    @OnThread(Tag.FXPlatform)
    public OutputBuilder save(OutputBuilder b) throws InternalException
    {
        apply(new DataTypeVisitorEx<UnitType, InternalException>()
        {
            @Override
            public UnitType number(NumberDisplayInfo displayInfo) throws InternalException, InternalException
            {
                //TODO
                return UnitType.UNIT;
            }

            @Override
            public UnitType text() throws InternalException, InternalException
            {
                b.t(FormatLexer.TEXT, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType date() throws InternalException, InternalException
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
            public UnitType tagged(List<TagType<DataType>> tags) throws InternalException, InternalException
            {
                //TODO
                return UnitType.UNIT;
            }
        });
        return b;
    }


    public static DataType loadType(ATypeContext type) throws InternalException
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
            //TODO store the unit
            return new DataType(Kind.NUMBER, new NumberDisplayInfo(n.STRING().getText(), Integer.valueOf(n.DIGITS().getText())), null);
        }
        else if (type.tagged() != null)
        {
            List<TagType<DataType>> tags = new ArrayList<>();
            for (TagItemContext item : type.tagged().tagItem())
            {
                String tagName = item.CONSTRUCTOR().getChild(1).getText();
                if (item.type() != null)
                    tags.add(new TagType<DataType>(tagName, loadType(item.type().aType())));
                else
                    tags.add(new TagType<DataType>(tagName, null));
            }

            return new DataType(Kind.TAGGED, null, tags);
        }
        else
            throw new InternalException("Unrecognised case: " + type);
    }
}
