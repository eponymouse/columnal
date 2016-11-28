package records.data.datatype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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

    public static final DataType INTEGER = new DataType(Kind.NUMBER, NumberDisplayInfo.DEFAULT, null);
    public static final DataType BOOLEAN = new DataType(Kind.BOOLEAN, null, null);

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

    public static interface DataTypeVisitor<R>
    {
        R number(NumberDisplayInfo displayInfo) throws InternalException, UserException;
        R text() throws InternalException, UserException;
        R date() throws InternalException, UserException;
        R bool() throws InternalException, UserException;

        R tagged(List<TagType<DataType>> tags) throws InternalException, UserException;
        //R tuple() throws InternalException, UserException;

        //R array() throws InternalException, UserException;
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
    public final <R> R apply(DataTypeVisitor<R> visitor) throws InternalException, UserException
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
    }


    @OnThread(Tag.Simulation)
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
                return "TODO";
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


    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataType dataType = (DataType) o;

        if (kind != dataType.kind) return false;
        if (numberDisplayInfo != null ? !numberDisplayInfo.equals(dataType.numberDisplayInfo) : dataType.numberDisplayInfo != null)
            return false;
        return tagTypes != null ? tagTypes.equals(dataType.tagTypes) : dataType.tagTypes == null;
    }

    @Override
    public int hashCode()
    {
        int result = kind.hashCode();
        result = 31 * result + (numberDisplayInfo != null ? numberDisplayInfo.hashCode() : 0);
        result = 31 * result + (tagTypes != null ? tagTypes.hashCode() : 0);
        return result;
    }
}
