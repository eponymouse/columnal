package records.data.datatype;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ArrayColumnStorage;
import records.data.BooleanColumnStorage;
import records.data.Column.ProgressListener;
import records.data.ColumnStorage;
import records.data.ColumnStorage.BeforeGet;
import records.data.NumericColumnStorage;
import records.data.StringColumnStorage;
import records.data.TaggedColumnStorage;
import records.data.TemporalColumnStorage;
import records.data.TupleColumnStorage;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.DataParser;
import records.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Workers;
import utility.Workers.Priority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Created by neil on 22/11/2016.
 */
public class DataTypeUtility
{
    public static @Value Object generateExample(DataType type, int index) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<@Value Object>()
        {

            @Override
            public @Value Object number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return value((long)index);
            }

            @Override
            public @Value Object text() throws InternalException, UserException
            {
                return value(Arrays.asList("Aardvark", "Bear", "Cat", "Dog", "Emu", "Fox").get(index));
            }

            @Override
            public @Value Object bool() throws InternalException, UserException
            {
                return value((index % 2) == 1);
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return value(dateTimeInfo, LocalDate.ofEpochDay(index));
            }

            @Override
            public @Value TaggedValue tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tag = index % tags.size();
                @Nullable DataType inner = tags.get(tag).getInner();
                if (inner != null)
                    return new TaggedValue(tag, generateExample(inner, index - tag));
                else
                    return new TaggedValue(tag, null);
            }

            @Override
            public @Value Object tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return value(Utility.<DataType, @Value Object>mapListEx(inner, t -> generateExample(t, index)).toArray(new @Value Object[0]));
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
            {
                return value(Collections.emptyList());
            }
        });
    }

    @OnThread(Tag.Simulation)
    @SuppressWarnings("unchecked")
    public static ColumnStorage<?> makeColumnStorage(final DataType inner, ColumnStorage.@Nullable BeforeGet<?> beforeGet) throws InternalException
    {
        return inner.apply(new DataTypeVisitorEx<ColumnStorage<?>, InternalException>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumericColumnStorage(displayInfo, (BeforeGet<NumericColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> bool() throws InternalException
            {
                return new BooleanColumnStorage((BeforeGet<BooleanColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> text() throws InternalException
            {
                return new StringColumnStorage((BeforeGet<StringColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return new TemporalColumnStorage(dateTimeInfo, (BeforeGet<TemporalColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return new TaggedColumnStorage(typeName, tags, (BeforeGet<TaggedColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tuple(ImmutableList<DataType> innerTypes) throws InternalException
            {
                return new TupleColumnStorage(innerTypes, beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> array(@Nullable DataType inner) throws InternalException
            {
                return new ArrayColumnStorage(inner, (BeforeGet<ArrayColumnStorage>)beforeGet);
            }
        });
    }

    public static @Value int requireInteger(@Value Object o) throws UserException, InternalException
    {
        return Utility.<@Value Integer>withNumber(o, l -> {
            if (l.longValue() != l.intValue())
                throw new UserException("Number too large: " + l);
            return value(l.intValue());
        }, bd -> {
            try
            {
                return value(bd.intValueExact());
            }
            catch (ArithmeticException e)
            {
                throw new UserException("Number not an integer or too large: " + bd);
            }
        });
    }

    @SuppressWarnings("userindex")
    public static @UserIndex @Value int userIndex(@Value Object value) throws InternalException, UserException
    {
        @Value int integer = requireInteger(value);
        return integer;
    }

    @SuppressWarnings("valuetype")
    public static <T extends Number> @Value T value(@UnknownIfValue T number)
    {
        return number;
    }

    @SuppressWarnings("valuetype")
    public static @Value Boolean value(@UnknownIfValue Boolean bool)
    {
        return bool;
    }

    @SuppressWarnings("valuetype")
    public static @Value String value(@UnknownIfValue String string)
    {
        return string;
    }

    @SuppressWarnings("valuetype")
    public static @Value TemporalAccessor value(DateTimeInfo dest, @UnknownIfValue TemporalAccessor t)
    {
        switch (dest.getType())
        {
            case YEARMONTHDAY:
                if (t instanceof LocalDate)
                    return t;
                else
                    return LocalDate.from(t);
            case YEARMONTH:
                if (t instanceof YearMonth)
                    return t;
                else
                    return YearMonth.from(t);
            case TIMEOFDAY:
                if (t instanceof LocalTime)
                    return t;
                else
                    return LocalTime.from(t);
            case TIMEOFDAYZONED:
                if (t instanceof OffsetTime)
                    return t;
                else
                    return OffsetTime.from(t);
            case DATETIME:
                if (t instanceof LocalDateTime)
                    return t;
                else
                    return LocalDateTime.from(t);
            case DATETIMEZONED:
                if (t instanceof ZonedDateTime)
                    return t;
                else
                    return ZonedDateTime.from(t);
        }
        return t;
    }

    @SuppressWarnings("valuetype")
    public static @Value Object @Value [] value(@Value Object [] tuple)
    {
        return tuple;
    }

    @SuppressWarnings("valuetype")
    public static Utility.@Value ListEx value(Utility.@UnknownIfValue ListEx list)
    {
        return list;
    }

    @SuppressWarnings("valuetype")
    public static Utility.@Value ListEx value(@UnknownIfValue List<@Value ? extends Object> list)
    {
        return new ListEx()
        {
            @Override
            public int size() throws InternalException, UserException
            {
                return list.size();
            }

            @Override
            public @Value Object get(int index) throws InternalException, UserException
            {
                return list.get(index);
            }
        };
    }

    public static String _test_valueToString(@Value Object item)
    {
        if (item instanceof Object[])
        {
            @Value Object[] tuple = (@Value Object[])item;
            return "(" + Arrays.stream(tuple).map(DataTypeUtility::_test_valueToString).collect(Collectors.joining(", ")) + ")";
        }

        return item.toString();
    }

    @OnThread(Tag.Simulation)
    public static String valueToString(DataType dataType, @Value Object item, @Nullable DataType parent) throws UserException, InternalException
    {
        return dataType.apply(new DataTypeVisitor<String>()
        {
            @Override
            public String number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return item.toString();
            }

            @Override
            public String text() throws InternalException, UserException
            {
                System.err.println("Unescaped:\n" + item.toString());
                return "\"" + GrammarUtility.escapeChars(item.toString()) + "\"";
            }

            @Override
            public String date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return dateTimeInfo.getFormatter().format((TemporalAccessor) item);
            }

            @Override
            public String bool() throws InternalException, UserException
            {
                return item.toString();
            }

            @Override
            @OnThread(Tag.Simulation)
            public String tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                TaggedValue tv = (TaggedValue)item;
                String tagName = tags.get(tv.getTagIndex()).getName();
                @Nullable @Value Object tvInner = tv.getInner();
                if (tvInner != null)
                {
                    @Nullable DataType typeInner = tags.get(tv.getTagIndex()).getInner();
                    if (typeInner == null)
                        throw new InternalException("Tag value inner but missing type inner: " + typeName + " " + tagName);
                    return tagName + "(" + valueToString(typeInner, tvInner, dataType) + ")";
                }
                else
                {
                    return tagName;
                }
            }

            @Override
            @OnThread(Tag.Simulation)
            public String tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                @Value Object[] tuple = (@Value Object[])item;
                StringBuilder s = new StringBuilder();
                if (parent == null || !parent.isTagged())
                    s.append("(");
                for (int i = 0; i < tuple.length; i++)
                {
                    if (i != 0)
                        s.append(",");
                    s.append(valueToString(inner.get(i), tuple[i], dataType));
                }
                if (parent == null || !parent.isTagged())
                    s.append(")");
                return s.toString();
            }

            @Override
            @OnThread(Tag.Simulation)
            public String array(@Nullable DataType inner) throws InternalException, UserException
            {
                StringBuilder s = new StringBuilder("[");
                ListEx listEx = (ListEx)item;
                for (int i = 0; i < listEx.size(); i++)
                {
                    if (i != 0)
                        s.append(",");
                    if (inner == null)
                        throw new InternalException("Array has empty type but is not empty");
                    s.append(valueToString(inner, listEx.get(i), dataType));
                }
                return s.append("]").toString();
            }
        });
    }

    public static DataTypeValue listToType(DataType elementType, ListEx listEx) throws InternalException, UserException
    {
        return elementType.fromCollapsed((i, prog) -> listEx.get(i));
    }

    public static GetValue<TaggedValue> toTagged(GetValue<Integer> g, ImmutableList<TagType<DataTypeValue>> tagTypes)
    {
        return new GetValue<TaggedValue>()
        {
            @Override
            public TaggedValue getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
            {
                int tagIndex = g.getWithProgress(index, progressListener);
                @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();
                if (inner == null)
                {
                    return new TaggedValue(tagIndex, null);
                }
                else
                {
                    @Value Object innerVal = inner.getCollapsed(index);
                    return new TaggedValue(tagIndex, innerVal);
                }
            }

            @Override
            public @OnThread(Tag.Simulation) void set(int index, TaggedValue value) throws InternalException, UserException
            {
                g.set(index, value.getTagIndex());
                @Nullable DataTypeValue inner = tagTypes.get(value.getTagIndex()).getInner();
                if (inner != null)
                {
                    @Nullable @Value Object innerVal = value.getInner();
                    if (innerVal == null)
                        throw new InternalException("Inner type present but not inner value " + tagTypes + " #" + value.getTagIndex());
                    inner.setCollapsed(index, innerVal);
                }
            }
        };
    }

    public static <DT extends DataType> TaggedValue makeDefaultTaggedValue(ImmutableList<TagType<DT>> tagTypes) throws InternalException
    {
        OptionalInt noInnerIndex = Utility.findFirstIndex(tagTypes, tt -> tt.getInner() == null);
        if (noInnerIndex.isPresent())
        {
            return new TaggedValue(noInnerIndex.getAsInt(), null);
        }
        else
        {
            @Nullable DataType inner = tagTypes.get(0).getInner();
            if (inner == null)
                throw new InternalException("Impossible: no tags without inner value, yet no inner value!");
            return new TaggedValue(0, makeDefaultValue(inner));
        }
    }

    public static @Value Object makeDefaultValue(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<@Value Object, InternalException>()
        {
            @Override
            public @Value Object number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return DataTypeUtility.value(0);
            }

            @Override
            public @Value Object text() throws InternalException, InternalException
            {
                return DataTypeUtility.value("");
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return dateTimeInfo.getDefaultValue();
            }

            @Override
            public @Value Object bool() throws InternalException, InternalException
            {
                return DataTypeUtility.value(false);
            }

            @Override
            public @Value Object tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                return makeDefaultTaggedValue(tags);
            }

            @Override
            public @Value Object tuple(ImmutableList<DataType> inner) throws InternalException, InternalException
            {
                @Value Object @Value[] tuple = DataTypeUtility.value(new Object[inner.size()]);
                for (int i = 0; i < inner.size(); i++)
                {
                    tuple[i] = makeDefaultValue(inner.get(i));
                }
                return tuple;
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, InternalException
            {
                return DataTypeUtility.value(Collections.emptyList());
            }
        });
    }

    public static GetValue<ListEx> toListEx(DataType innerType, GetValue<Pair<Integer, DataTypeValue>> g)
    {
        return new GetValue<ListEx>()
        {
            @Override
            public ListEx getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
            {
                Pair<Integer, DataTypeValue> p = g.getWithProgress(index, progressListener);
                return new ListEx()
                {
                    @Override
                    public int size() throws InternalException, UserException
                    {
                        return p.getFirst();
                    }

                    @Override
                    public @Value Object get(int index) throws InternalException, UserException
                    {
                        return p.getSecond().getCollapsed(index);
                    }
                };
            }

            @Override
            public @OnThread(Tag.Simulation) void set(int index, ListEx value) throws InternalException, UserException
            {
                g.set(index, new Pair<>(value.size(), innerType.fromCollapsed((i, prog) -> value.get(i))));
            }
        };
    }

    // Fetches a ListEx from the simulation thread and returns it as a flat list on the FX thread.
    @OnThread(Tag.FXPlatform)
    public static List<@Value Object> fetchList(ListEx simList) throws InternalException
    {
        CompletableFuture<Either<Exception, List<@Value Object>>> f = new CompletableFuture<>();
        Workers.onWorkerThread("Fetch list", Priority.FETCH, () -> {
            try
            {
                ArrayList<@Value Object> r = new ArrayList<>(simList.size());
                for (int i = 0; i < simList.size(); i++)
                {
                    r.add(simList.get(i));
                }
                f.complete(Either.right(r));
            }
            catch (InternalException | UserException e)
            {
                f.complete(Either.left(e));
            }
        });
        Either<Exception, List<Object>> either;
        try
        {
            either = f.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new InternalException("Error fetching list", e);
        }
        if (either.isLeft())
            throw new InternalException("Error fetching list", either.getLeft());
        else
            return either.getRight();
    }
}
