package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.jetbrains.annotations.NotNull;
import records.data.Column;
import records.error.InternalException;
import records.error.UserException;

import java.util.ArrayList;
import java.util.Arrays;
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
    public DataType copy(GetValue<List<Object>> get) throws UserException, InternalException
    {
        return copy(get, 0);
    }
    public DataType copy(GetValue<List<Object>> get, int curIndex) throws UserException, InternalException
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
                            tagsCopy.add(new TagType(tagType.getName(), copy(get, curIndex + 1)));
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

        public TagType(String name, @Nullable DataType inner)
        {
            this.name = name;
            this.inner = inner;
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
}
