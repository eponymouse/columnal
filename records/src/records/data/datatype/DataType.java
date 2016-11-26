package records.data.datatype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

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
public abstract class DataType
{
    public static final DataType INTEGER = new DataType()
    {
        @Override
        public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
        {
            return visitor.number((i, prog) -> {throw new InternalException("Fetching from in-built type");}, NumberDisplayInfo.DEFAULT);
        }
    };

    @OnThread(Tag.Simulation)
    public final List<Object> getCollapsed(int index) throws UserException, InternalException
    {
        return apply(new DataTypeVisitorGet<List<Object>>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public List<Object> number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return Collections.singletonList(g.get(index));
            }

            @Override
            @OnThread(Tag.Simulation)
            public List<Object> text(GetValue<String> g) throws InternalException, UserException
            {
                return Collections.singletonList(g.get(index));
            }

            @Override
            @OnThread(Tag.Simulation)
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

    public DataType copyReorder(GetValue<Integer> getOriginalIndex) throws UserException, InternalException
    {
        return apply(new DataTypeVisitorGet<DataType>()
        {
            @Override
            public DataType number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return new DataType() {
                    @Override
                    public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                    {
                        return visitor.number(reOrder(getOriginalIndex, g), displayInfo);
                    }
                };
            }

            @Override
            public DataType text(GetValue<String> g) throws InternalException, UserException
            {
                return new DataType()
                {
                    @Override
                    public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                    {
                        return visitor.text(reOrder(getOriginalIndex, g));
                    }
                };
            }

            @Override
            public DataType tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                return new DataType()
                {
                    @Override
                    public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                    {
                        List<TagType> newTagTypes = new ArrayList<>();
                        for (TagType t : tagTypes)
                            newTagTypes.add(new TagType(t.getName(), t.inner == null ? null : t.inner.copyReorder(getOriginalIndex)));
                        return visitor.tagged(newTagTypes, reOrder(getOriginalIndex, g));
                    }
                };
            }
        });
    }

    @SuppressWarnings("nullness")
    private static <T> GetValue<T> reOrder(GetValue<Integer> getOriginalIndex, GetValue<T> g)
    {
        return (int destIndex, final ProgressListener prog) -> {
            int srcIndex = getOriginalIndex.getWithProgress(destIndex, prog == null ? null : (d -> prog.progressUpdate(d*0.5)));
            return g.getWithProgress(srcIndex, prog == null ? null : (d -> prog.progressUpdate(d * 0.5 + 0.5)));
        };
    }

    @OnThread(Tag.Any)
    public DataType copy(GetValue<List<Object>> get) throws UserException, InternalException
    {
        return copy(get, 0);
    }

    @OnThread(Tag.Any)
    private DataType copy(GetValue<List<Object>> get, int curIndex) throws UserException, InternalException
    {
        return apply(new DataTypeVisitor<DataType>()
        {
            @Override
            public DataType number(NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return new DataType()
                {
                    @Override
                    public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                    {
                        return visitor.number((i, prog) -> (Number)get.getWithProgress(i, prog).get(curIndex), displayInfo);
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

        R tagged(List<TagType> tags) throws InternalException, UserException;
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
        public R tagged(List<TagType> tags) throws InternalException, UserException
        {
            if (isBoolean(tags))
                return bool();
            throw new InternalException("Unexpected tagged data type");
        }

        protected R bool() throws InternalException
        {
            throw new InternalException("Unexpected boolean type");
        }
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
        public R number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException
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
        public R text(GetValue<String> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected text data type");
        }

        @Override
        public R tagged(List<TagType> tags, GetValue<Integer> g) throws InternalException, UserException
        {
            if (isBoolean(tags))
                return bool(mapValue(g, x -> x == 1));
            return defaultOp("Unexpected tagged data type");
        }

        protected R bool(GetValue<Boolean> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected boolean type");
        }
    }

    private static boolean isBoolean(List<TagType> tags)
    {
        return tags.size() == 2 && tags.get(0).getName().toLowerCase().equals("false") && tags.get(1).getName().toLowerCase().equals("true");
    }

    public static <T, R> GetValue<R> mapValue(GetValue<T> g, Function<T, @NonNull R> map)
    {
        return (i, prog) -> map.apply(g.getWithProgress(i, prog));
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
        R number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException;
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
            public R number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return visitor.number(displayInfo);
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
        @OnThread(Tag.Simulation)
        @NonNull T getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException;
        @OnThread(Tag.Simulation)
        @NonNull default T get(int index) throws UserException, InternalException { return getWithProgress(index, null); }
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
            public Boolean number(NumberDisplayInfo displayInfo) throws InternalException, UserException
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
