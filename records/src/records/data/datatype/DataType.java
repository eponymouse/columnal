package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.jetbrains.annotations.NotNull;
import records.data.Column;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
public abstract class DataType
{
    public final List<Object> getCollapsed(int index) throws UserException, InternalException
    {
        return apply(new DataTypeVisitorGet<List<Object>>()
        {
            @Override
            public List<Object> number(GetValue<Number> g) throws InternalException, UserException
            {
                return Collections.singletonList(g.get(index));
            }

            @Override
            public List<Object> text(GetValue<String> g) throws InternalException, UserException
            {
                return Collections.singletonList(g.get(index));
            }

            @Override
            public List<Object> tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                List<Object> l = new ArrayList<>();
                Integer tagIndex = g.get(index);
                l.add(tagIndex);
                @Nullable DataType inner = tagTypes.get(tagIndex).getInner();
                if (inner != null)
                    l.addAll(inner.apply(this));
                return l;
            }
        });
    }

    public DataType copy(GetValue<List<Object>> get) throws UserException, InternalException
    {
        return copy(get, 0);
    }
    private DataType copy(GetValue<List<Object>> get, int curIndex) throws UserException, InternalException
    {
        return apply(new DataTypeVisitor<DataType>()
        {
            @Override
            public DataType number() throws InternalException, UserException
            {
                return new DataType()
                {
                    @Override
                    public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                    {
                        return visitor.number((i, prog) -> (Number)get.getWithProgress(i, prog).get(curIndex));
                    }
                };
            }

            @Override
            public DataType text() throws InternalException, UserException
            {
                return new DataType()
                {
                    @Override
                    public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                    {
                        return visitor.text((i, prog) -> (String)get.getWithProgress(i, prog).get(curIndex));
                    }
                };
            }

            @Override
            public DataType tagged(List<TagType> tags) throws InternalException, UserException
            {
                return new DataType()
                {
                    @Override
                    public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                    {
                        ArrayList<TagType> tagsCopy = new ArrayList<>();
                        for (TagType tagType : tags)
                            tagsCopy.add(new TagType(tagType.getName(), tagType.inner == null ? null : tagType.inner.copy(get, curIndex + 1)));
                        return visitor.tagged(tagsCopy, (i, prog) -> (Integer)get.getWithProgress(i, prog).get(curIndex));
                    }
                };
            }
        });
    }

    public static interface DataTypeVisitor<R>
    {
        R number() throws InternalException, UserException;
        R text() throws InternalException, UserException;

        R tagged(List<TagType> tags) throws InternalException, UserException;
        //R tuple() throws InternalException, UserException;

        //R array() throws InternalException, UserException;
    }

    /*
    public static class TaggedValue implements Comparable<TaggedValue>
    {
        private final int tagIndex;
        private final String tag;
        private final @Nullable Object inner;

        public TaggedValue(int tagIndex, String tag, @Nullable Object inner)
        {
            this.tagIndex = tagIndex;
            this.tag = tag;
            this.inner = inner;
        }

        public int getTagIndex()
        {
            return tagIndex;
        }

        public String getTagName()
        {
            return tag;
        }

        public Object getInner()
        {
            return inner;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TaggedValue that = (TaggedValue) o;

            if (tagIndex != that.tagIndex) return false;
            return inner != null ? inner.equals(that.inner) : that.inner == null;

        }

        @Override
        public int hashCode()
        {
            int result = tagIndex;
            result = 31 * result + (inner != null ? inner.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(@NotNull TaggedValue o)
        {
            if (o == null)
                return 1;
            int c = Integer.compare(tagIndex, o.tagIndex);
            if (c != 0)
                return c;
            else
                return ((Comparable)inner).compareTo(o.inner);
        }
    }
    */

    public static interface DataTypeVisitorGet<R>
    {
        R number(GetValue<Number> g) throws InternalException, UserException;
        R text(GetValue<String> g) throws InternalException, UserException;

        R tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException;
        //R tuple(List<DataType> types) throws InternalException, UserException;

        //R array(DataType type) throws InternalException, UserException;
    }

    public final <R> R apply(DataTypeVisitor<R> visitor) throws InternalException, UserException
    {
        return apply(new DataTypeVisitorGet<R>()
        {
            @Override
            public R number(GetValue<Number> g) throws InternalException, UserException
            {
                return visitor.number();
            }

            @Override
            public R text(GetValue<String> g) throws InternalException, UserException
            {
                return visitor.text();
            }

            @Override
            public R tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                return visitor.tagged(tagTypes);
            }
        });
    }
    public abstract <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException;

    public static interface GetValue<T>
    {
        T getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException;
        default T get(int index) throws UserException, InternalException { return getWithProgress(index, null); }
    }

    public static class TagType
    {
        private final String name;
        private final @Nullable DataType inner;
        private final int extra;

        public TagType(String name, @Nullable DataType inner)
        {
            this(name, inner, -1);
        }

        public TagType(String name, @Nullable DataType inner, int extra)
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
        public @Nullable DataType getInner()
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
            public String number() throws InternalException, UserException
            {
                return "Number";
            }

            @Override
            public String text() throws InternalException, UserException
            {
                return "Text";
            }

            @Override
            public String tagged(List<TagType> tags) throws InternalException, UserException
            {
                return "TODO";
            }
        });
    }

    public static boolean canFitInOneNumeric(List<TagType> tags) throws InternalException, UserException
    {
        // Can fit in one numeric if there is no inner types,
        // or if the only inner type is a single numeric
        boolean foundNumeric = false;
        for (TagType t : tags)
        {
            if (t.getInner() != null)
            {
                if (isNumber(t.getInner()))
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
                if (isNumber(t.getInner()))
                {
                    return i;
                }
            }
        }
        return -1;
    }

    public static boolean isNumber(DataType t) throws UserException, InternalException
    {
        return t.apply(new DataTypeVisitor<Boolean>()
        {
            @Override
            public Boolean number() throws InternalException, UserException
            {
                return true;
            }

            @Override
            public Boolean text() throws InternalException, UserException
            {
                return false;
            }

            @Override
            public Boolean tagged(List<TagType> tags) throws InternalException, UserException
            {
                return false;
            }
        });
    }
}
