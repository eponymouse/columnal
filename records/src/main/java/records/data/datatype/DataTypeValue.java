package records.data.datatype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Column.ProgressListener;
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
 * Created by neil on 28/11/2016.
 */
public class DataTypeValue extends DataType
{
    private final @Nullable GetValue<Number> getNumber;
    private final @Nullable GetValue<String> getText;
    private final @Nullable GetValue<Temporal> getDate;
    private final @Nullable GetValue<Boolean> getBoolean;
    private final @Nullable GetValue<Integer> getTag;

    public DataTypeValue(Kind kind, @Nullable NumberDisplayInfo numberDisplayInfo, @Nullable List<TagType<DataTypeValue>> tagTypes, @Nullable GetValue<Number> getNumber, @Nullable GetValue<String> getText, @Nullable GetValue<Temporal> getDate, @Nullable GetValue<Boolean> getBoolean, @Nullable GetValue<Integer> getTag)
    {
        super(kind, numberDisplayInfo, (List<TagType<DataType>>)(List)tagTypes);
        this.getNumber = getNumber;
        this.getText = getText;
        this.getDate = getDate;
        this.getBoolean = getBoolean;
        this.getTag = getTag;
    }

    public static DataTypeValue bool(GetValue<Boolean> getValue)
    {
        return new DataTypeValue(Kind.BOOLEAN, null, null, null, null, null, getValue, null);
    }

    public static DataTypeValue tagged(List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> getTag)
    {
        return new DataTypeValue(Kind.TAGGED, null, tagTypes, null, null, null, null, getTag);
    }

    public static DataTypeValue text(GetValue<String> getText)
    {
        return new DataTypeValue(Kind.TEXT, null, null, null, getText, null, null, null);
    }

    public static DataTypeValue number(NumberDisplayInfo numberDisplayInfo, GetValue<Number> getNumber)
    {
        return new DataTypeValue(Kind.NUMBER, numberDisplayInfo, null, getNumber, null, null, null, null);
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
        public R tagged(List<TagType<DataTypeValue>> tags, GetValue<Integer> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected tagged data type");
        }

        @Override
        public R bool(GetValue<Boolean> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected boolean type");
        }

        @Override
        public R date(GetValue<Temporal> g) throws InternalException, UserException
        {
            return defaultOp("Unexpected date type");
        }
    }

    @OnThread(Tag.Simulation)
    public static interface DataTypeVisitorGet<R>
    {
        R number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException;
        R text(GetValue<String> g) throws InternalException, UserException;
        R bool(GetValue<Boolean> g) throws InternalException, UserException;
        R date(GetValue<Temporal> g) throws InternalException, UserException;

        R tagged(List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException;
        //R tuple(List<DataType> types) throws InternalException, UserException;

        //R array(DataType type) throws InternalException, UserException;
    }

    @SuppressWarnings("nullness")
    @OnThread(Tag.Simulation)
    public final <R> R applyGet(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
    {
        switch (kind)
        {
            case NUMBER:
                return visitor.number(getNumber, numberDisplayInfo);
            case TEXT:
                return visitor.text(getText);
            case DATE:
                return visitor.date(getDate);
            case BOOLEAN:
                return visitor.bool(getBoolean);
            case TAGGED:
                return visitor.tagged((List<TagType<DataTypeValue>>)(List)tagTypes, getTag);
            default:
                throw new InternalException("Missing kind case");
        }
    }

    public static interface GetValue<T>
    {
        @OnThread(Tag.Simulation)
        @NonNull T getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException;
        @OnThread(Tag.Simulation)
        @NonNull default T get(int index) throws UserException, InternalException { return getWithProgress(index, null); }
    }


    @OnThread(Tag.Simulation)
    public final List<Object> getCollapsed(int index) throws UserException, InternalException
    {
        return applyGet(new DataTypeVisitorGet<List<Object>>()
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
            public List<Object> tagged(List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                List<Object> l = new ArrayList<>();
                Integer tagIndex = g.get(index);
                l.add(tagIndex);
                @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();
                if (inner != null)
                    l.addAll(inner.applyGet(this));
                return l;
            }

            @Override
            @OnThread(Tag.Simulation)
            public List<Object> bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                return Collections.singletonList(g.get(index));
            }

            @Override
            @OnThread(Tag.Simulation)
            public List<Object> date(GetValue<Temporal> g) throws InternalException, UserException
            {
                return Collections.singletonList(g.get(index));
            }
        });
    }

    public DataTypeValue copyReorder(GetValue<Integer> getOriginalIndex) throws UserException, InternalException
    {
        List<TagType<DataTypeValue>> newTagTypes = null;
        if (this.tagTypes != null)
        {
            newTagTypes = new ArrayList<>();
            for (TagType t : tagTypes)
                newTagTypes.add(new TagType<>(t.getName(), t.getInner() == null ? null : ((DataTypeValue)t.getInner()).copyReorder(getOriginalIndex)));
        }


        return new DataTypeValue(kind, numberDisplayInfo, newTagTypes,
            reOrder(getOriginalIndex, getNumber),
            reOrder(getOriginalIndex, getText),
            reOrder(getOriginalIndex, getDate),
            reOrder(getOriginalIndex, getBoolean),
            reOrder(getOriginalIndex, getTag));
    }

    @SuppressWarnings("nullness")
    private static <T> @Nullable GetValue<T> reOrder(GetValue<Integer> getOriginalIndex, @Nullable GetValue<T> g)
    {
        if (g == null)
            return null;
        return (int destIndex, final ProgressListener prog) -> {
            int srcIndex = getOriginalIndex.getWithProgress(destIndex, prog == null ? null : (d -> prog.progressUpdate(d*0.5)));
            return g.getWithProgress(srcIndex, prog == null ? null : (d -> prog.progressUpdate(d * 0.5 + 0.5)));
        };
    }

    public static <T, R> GetValue<R> mapValue(GetValue<T> g, Function<T, @NonNull R> map)
    {
        return (i, prog) -> map.apply(g.getWithProgress(i, prog));
    }

}
