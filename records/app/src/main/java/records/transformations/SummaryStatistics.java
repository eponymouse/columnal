package records.transformations;

import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.SummaryColContext;
import records.grammar.TransformationParser.SummaryContext;
import records.gui.View;
import records.transformations.expression.BracketedStatus;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.Expression.TableLookup;
import records.transformations.expression.TypeState;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * Created by neil on 21/10/2016.
 */
@OnThread(Tag.Simulation)
public class SummaryStatistics extends Transformation
{
    public static final String NAME = "aggregate";
    private final @Nullable Table src;
    private final TableId srcTableId;
    // ColumnId here is the destination column, not source column:
    @OnThread(Tag.Any)
    private final ImmutableList<Pair<ColumnId, Expression>> summaries;

    // Columns to split by:
    @OnThread(Tag.Any)
    private final ImmutableList<ColumnId> splitBy;
    @OnThread(Tag.Any)
    private String error;
    private final @Nullable RecordSet result;

    @OnThread(Tag.Simulation)
    private static class JoinedSplit
    {
        private final List<Column> colName = new ArrayList<>();
        private final List<Object> colValue = new ArrayList<>();

        public JoinedSplit()
        {
        }

        public JoinedSplit(Column column, Object value, JoinedSplit addTo)
        {
            colName.add(column);
            colValue.add(value);
            colName.addAll(addTo.colName);
            colValue.addAll(addTo.colValue);
        }

        public boolean satisfied(int index) throws InternalException, UserException
        {
            for (int c = 0; c < colName.size(); c++)
            {
                if (!colName.get(c).getType().getCollapsed(index).equals(colValue.get(c)))
                    return false;
            }
            return true;
        }
    }

    public SummaryStatistics(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, ImmutableList<Pair<ColumnId, Expression>> summaries, ImmutableList<ColumnId> splitBy) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.summaries = summaries;
        this.splitBy = splitBy;
        if (this.src == null)
        {
            this.result = null;
            error = "Could not find source table: \"" + srcTableId + "\"";
            return;
        }
        else
            error = "Unknown error with table \"" + getId() + "\"";

        @Nullable RecordSet theResult = null;
        try
        {
            RecordSet src = this.src.getData();
            List<JoinedSplit> splits = calcSplits(src, splitBy);

            List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();

            // Will be zero by default, which we take advantage of:
            int[] splitIndexes = new int[src.getLength()];

            if (!splitBy.isEmpty())
            {
                for (int i = 0; i < splitBy.size(); i++)
                {
                    ColumnId colName = splitBy.get(i);
                    Column orig = src.getColumn(colName);
                    int iFinal = i;
                    columns.add(rs -> new Column(rs, colName)
                    {
                        private Object getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                        {
                            return splits.get(index).colValue.get(iFinal);
                        }

                        @Override
                        public DataTypeValue getType() throws InternalException, UserException
                        {
                            throw new UnimplementedException(); // TODO
                            //return orig.getType().copy(this::getWithProgress);
                        }

                        @Override
                        public boolean isAltered()
                        {
                            return true;
                        }
                    });
                }

                outer:
                for (int i = 0; i < splitIndexes.length; i++)
                {
                    // TODO could do this O(#splitBy) not O(#splits) if we design JoinedSplit right
                    for (int s = 0; s < splits.size(); s++)
                    {
                        if (splits.get(s).satisfied(i))
                        {
                            splitIndexes[i] = s;
                            continue outer;
                        }
                    }
                    throw new InternalException("Split not found for row " + i + ": " + src.debugGetVals(i));
                }
            }

            TableLookup tableLookup = new MultipleTableLookup(mgr, this.src);
            for (Pair<ColumnId, Expression> e : summaries)
            {
                Expression expression = e.getSecond();
                ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
                @Nullable TypeExp type = expression.checkExpression(tableLookup, new TypeState(mgr.getUnitManager(), mgr.getTypeManager()), errors);
                @Nullable DataType concrete = type == null ? null : errors.recordLeftError(mgr.getTypeManager(), expression, type.toConcreteType(mgr.getTypeManager()));
                if (type == null || concrete == null)
                    throw new UserException((@NonNull StyledString)errors.getAllErrors().findFirst().orElse(StyledString.s("Unknown type error")));
                @NonNull DataType typeFinal = concrete;
                columns.add(rs -> typeFinal.makeCalculatedColumn(rs, e.getFirst(), i -> expression.getValue(new EvaluateState(mgr.getTypeManager(), OptionalInt.empty())).getFirst()));
                
            }

            theResult = new RecordSet(columns)
            {
                @Override
                public boolean indexValid(int index) throws UserException
                {
                    return summaries.isEmpty() ? false : index < splits.size();
                }

                @Override
                @SuppressWarnings("units")
                public @TableDataRowIndex int getLength() throws UserException
                {
                    return summaries.isEmpty() ? 0 : splits.size();
                }
            };
        }
        catch (UserException e)
        {
            String msg = e.getLocalizedMessage();
            if (msg != null)
                this.error = msg;
        }
        this.result = theResult;
    }

    private static class SingleSplit
    {
        private Column column;
        private List<@NonNull Object> values;

        public SingleSplit(Column column, List<@NonNull Object> values)
        {
            this.column = column;
            this.values = values;
        }
    }

    private static List<JoinedSplit> calcSplits(RecordSet src, List<ColumnId> splitBy) throws UserException, InternalException
    {
        // Each item in outer is a column.
        // Each item in inner is a possible value of that column;
        List<SingleSplit> splits = new ArrayList<>();
        for (ColumnId colName : splitBy)
        {
            Column c = src.getColumn(colName);
            //Optional<List<@NonNull ?>> fastDistinct = c.fastDistinct();
            //if (fastDistinct.isPresent())
            //    splits.add(new SingleSplit(c, fastDistinct.get()));
            //else
            {
                HashSet<Object> r = new HashSet<>();
                for (int i = 0; c.indexValid(i); i++)
                {
                    r.add(c.getType().getCollapsed(i));
                }
                splits.add(new SingleSplit(c, new ArrayList<>(r)));
            }

        }
        // Now form cross-product:
        return crossProduct(splits, 0);
    }

    private static List<JoinedSplit> crossProduct(List<SingleSplit> allDistincts, int from)
    {
        if (from >= allDistincts.size())
            return Collections.singletonList(new JoinedSplit());
        // Take next list:
        SingleSplit cur = allDistincts.get(from);
        List<JoinedSplit> rest = crossProduct(allDistincts, from + 1);
        List<JoinedSplit> r = new ArrayList<>();
        for (Object o : cur.values)
        {
            for (JoinedSplit js : rest)
            {
                r.add(new JoinedSplit(cur.column, o, js));
            }
        }
        return r;
    }


    //@OnThread(Tag.FXPlatform)
    //public static void withGUICreate(RecordSet src, FXPlatformConsumer<SummaryStatistics> andThen) throws InternalException, UserException
    //{
//        Map<String, Set<SummaryType>> summaries = new HashMap<>();
//        for (Column c : src.getColumns())
//        {
//            if (!c.getName().equals("Mistake"))
//                summaries.put(c.getName(), new HashSet<>(Arrays.asList(SummaryType.MIN, SummaryType.MAX)));
//        }
//
//        Workers.onWorkerThread("Create summary statistics", () -> {
//            Utility.alertOnError(() -> {
//                SummaryStatistics ss = new SummaryStatistics(src, summaries, Collections.singletonList("Mistake"));
//                Platform.runLater(() -> andThen.consume(ss));
//                return (Void)null;
//            });
//        });
//    }


    @Override
    @NonNull
    @OnThread(Tag.Any)
    public RecordSet getData() throws UserException
    {
        if (result == null)
            throw new UserException(error);
        return result;
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return NAME;
    }

    @OnThread(Tag.FXPlatform)
    public static class Info extends SingleSourceTransformationInfo
    {
        @OnThread(Tag.Any)
        public Info()
        {
            super(NAME, "transform.aggregate", "preview-aggregate.png", "aggregate.explanation.short",Arrays.asList("min", "max"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation makeWithSource(View view, TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new SummaryStatistics(mgr, new InitialLoadDetails(null, destination, null), srcTable.getId(), ImmutableList.of(), ImmutableList.of());
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException
        {
            SummaryContext loaded = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, TransformationParser::summary);

            ImmutableList.Builder<Pair<ColumnId, Expression>> summaryTypes = ImmutableList.builder();
            for (SummaryColContext sumType : loaded.summaryCol())
            {
                summaryTypes.add(new Pair<>(new ColumnId(sumType.column.getText()), Expression.parse(null, sumType.expression().EXPRESSION().getText(), mgr.getTypeManager())));
            }
            ImmutableList<ColumnId> splits = loaded.splitBy().stream().map(s -> new ColumnId(s.column.getText())).collect(ImmutableList.<ColumnId>toImmutableList());
            return new SummaryStatistics(mgr, initialLoadDetails, srcTableId, summaryTypes.build(), splits);
        }
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        OutputBuilder b = new OutputBuilder();
        for (Pair<ColumnId, Expression> entry : summaries)
        {
            b.kw("SUMMARY");
            b.id(renames.columnId(getId(), entry.getFirst()));
            b.t(TransformationLexer.EXPRESSION_BEGIN, TransformationLexer.VOCABULARY);
            b.raw(entry.getSecond().save(true, BracketedStatus.MISC, renames));
            b.nl();
        }
        for (ColumnId c : splitBy)
        {
            b.kw("SPLIT").id(renames.columnId(srcTableId, c)).nl();
        }
        return b.toLines();
    }
    
    @OnThread(Tag.Any)
    public TableId getSource()
    {
        return srcTableId;
    }
    
    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getPrimarySources()
    {
        return Stream.of(srcTableId);
    }

    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getSourcesFromExpressions()
    {
        return TransformationUtil.tablesFromExpressions(summaries.stream().map(p -> p.getSecond()));
    }

    @OnThread(Tag.Any)
    public ImmutableList<Pair<ColumnId, Expression>> getColumnExpressions()
    {
        return summaries;
    }

    @OnThread(Tag.Any)
    public ImmutableList<ColumnId> getSplitBy()
    {
        return splitBy;
    }

    /*
    @OnThread(Tag.Simulation)
    private abstract static class MinMaxFold<T> implements FoldOperation<T, T>
    {
        @MonotonicNonNull
        private T cur = null;
        private final SummaryType summaryType;

        public MinMaxFold(SummaryType summaryType)
        {
            this.summaryType = summaryType;
        }

        @Override
        public List<@NonNull T> process(@NonNull T x, int i)
        {
            if (cur == null)
            {
                cur = x;
            }
            else
            {
                int comparison = compare(cur, x);
                if ((summaryType == SummaryType.MIN && comparison > 0)
                    || (summaryType == SummaryType.MAX && comparison < 0))
                    cur = x;
            }
            return Collections.emptyList();
        }

        @Override
        public List<@NonNull T> end() throws UserException
        {
            if (cur != null)
                return Collections.singletonList(cur);
            else
                throw new UserException("No values for " + summaryType);
        }

        protected abstract int compare(T a, T b);
    }

    @OnThread(Tag.Simulation)
    private static class MinMaxNumericFold extends MinMaxFold<Number>
    {
        public MinMaxNumericFold(SummaryType summaryType)
        {
            super(summaryType);
        }

        @Override
        protected int compare(Number a, Number b)
        {
            return Utility.compareNumbers(a, b);
        }
    }

    @OnThread(Tag.Simulation)
    private static class MinMaxComparableFold<T extends Comparable<T>> extends MinMaxFold<T>
    {
        public MinMaxComparableFold(SummaryType summaryType)
        {
            super(summaryType);
        }

        @Override
        protected int compare(T a, T b)
        {
            return a.compareTo(b);
        }
    }

    public static class MinMaxTaggedFold implements FoldOperation<Integer, Object>
    {
        private int bestTag = -1;
        @Nullable @Value Object bestInner = null;
        private final List<TagType<DataTypeValue>> tagTypes;
        private final boolean ignoreNullaryTags;
        private final SummaryType summaryType;

        public MinMaxTaggedFold(SummaryType summaryType, List<TagType<DataTypeValue>> tagTypes, boolean ignoreNullaryTags)
        {
            this.tagTypes = tagTypes;
            this.ignoreNullaryTags = ignoreNullaryTags;
            this.summaryType = summaryType;
        }

        @Override
        public List<Object> process(Integer tagBox, int i) throws InternalException, UserException
        {
            int tag = tagBox;

            @Nullable DataTypeValue innerType = tagTypes.get(tag).getInner();
            if (ignoreNullaryTags && innerType == null)
                return Collections.emptyList();
            if (bestTag != -1 && ((summaryType == SummaryType.MIN && tag > bestTag)
                || (summaryType == SummaryType.MAX && tag < bestTag)))
                return Collections.emptyList(); // We've seen a better tag already, no need to look further

            if (bestTag != tag)
            {
                // Tag is first we've seen, or better than we've seen
                bestTag = tag;
                bestInner = null;
            }
            if (innerType == null)
                return Collections.emptyList(); // Nullary tag, don't need to do any inner comparison

            @NonNull Object x = innerType.getCollapsed(i);
            if (bestInner == null)
            {
                bestInner = x;
            } else
            {
                int comparison = Utility.compareValues(bestInner, x);
                if ((summaryType == SummaryType.MIN && comparison > 0)
                    || (summaryType == SummaryType.MAX && comparison < 0))
                    bestInner = x;
            }
            return Collections.emptyList();
        }

        @Override
        public List<Object> end() throws UserException
        {
            if (bestInner != null)
            {
                return Collections.singletonList(bestInner);
            }
            else
                throw new UserException("No values for " + summaryType);
        }
    }

    public static class CountFold<T> implements FoldOperation<T, Number>
    {
        private long count = 0;

        @Override
        public List<Number> process(T n, int i)
        {
            count += 1;
            return Collections.emptyList();
        }

        @Override
        public List<Number> end() throws UserException
        {
            return Collections.singletonList((Long)count);
        }
    }

    private static <T, R> void applyFold(ColumnStorage<R> cache, FoldOperation<@Value T, @Value R> fold, Column srcCol, GetValue<@Value T> srcGet, int[] splitIndexes, int index) throws InternalException, UserException
    {
        applyFold(cache, x -> x, fold, srcCol, srcGet, splitIndexes, index);
    }

    private static <T, R, S> void applyFold(ColumnStorage<S> cache, Function<@NonNull @Value R, @NonNull @Value S> convert, FoldOperation<T, R> fold, Column srcCol, GetValue<T> srcGet, int[] splitIndexes, int index) throws InternalException, UserException
    {
        cache.addAll(Utility.mapList(fold.start(), convert));
        for (int i = 0; srcCol.indexValid(i); i++)
        {
            if (splitIndexes[i] != index)
                continue;

            cache.addAll(Utility.mapList(fold.process(srcGet.get(i), i), convert));
        }
        cache.addAll(Utility.mapList(fold.end(), convert));
    }

    private static void applyFold(CalculatedTaggedColumn c, FoldOperation<Integer, Object> fold, Column srcCol, GetValue<Integer> srcGet, int[] splitIndexes, int index) throws InternalException, UserException
    {
        c.addAllUnpacked(fold.start());
        for (int i = 0; srcCol.indexValid(i); i++)
        {
            if (splitIndexes[i] != index)
                continue;

            c.addAllUnpacked(fold.process(srcGet.get(i), i));
        }
        c.addAllUnpacked(fold.end());
    }

    private static class MeanSumFold implements FoldOperation<Number, Number>
    {
        private final SummaryType summaryType;
        private long count = 0;
        private BigDecimal total = BigDecimal.ZERO;

        public MeanSumFold(SummaryType summaryType)
        {
            this.summaryType = summaryType;
        }
        @Override
        public List<Number> process(Number n, int i)
        {
            count += 1;
            total = total.add(Utility.toBigDecimal(n));
            return Collections.emptyList();
        }

        @Override
        public List<Number> end() throws UserException
        {
            if (summaryType == SummaryType.MEAN)
                return Collections.singletonList(total.divide(BigDecimal.valueOf(count), Utility.getMathContext()));
            else
                return Collections.singletonList(total);
        }
    }

    // Functionality: add all numbers we see; ignore nullary tags and strings
    @OnThread(Tag.Simulation)
    private class MeanSumTaggedFold implements FoldOperation<Integer,Number>
    {
        private final MeanSumFold numericFold;
        private final List<TagType<DataTypeValue>> tagTypes;

        public MeanSumTaggedFold(SummaryType summaryType, List<TagType<DataTypeValue>> tagTypes)
        {
            this.numericFold = new MeanSumFold(summaryType);
            this.tagTypes = tagTypes;
        }

        @Override
        public List<Number> start()
        {
            return numericFold.start();
        }

        @Override
        public List<Number> process(Integer n, int index) throws InternalException, UserException
        {
            @Nullable DataTypeValue inner = tagTypes.get(n).getInner();
            if (inner != null)
            {
                return inner.applyGet(new DataTypeVisitorGet<List<Number>>()
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public List<Number> number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
                    {
                        return numericFold.process(g.get(index), index);
                    }

                    @Override
                    public List<Number> bool(GetValue<@Value Boolean> g) throws InternalException, UserException
                    {
                        return numericFold.process(g.get(index) ? 1L :0L, index);
                    }

                    @Override
                    public List<Number> text(GetValue<@Value String> g) throws InternalException, UserException
                    {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Number> date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
                    {
                        return Collections.emptyList();
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public List<Number> tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
                    {
                        @Nullable DataTypeValue nestedInner = tagTypes.get(g.get(index)).getInner();
                        if (nestedInner != null)
                            return nestedInner.applyGet(this);
                        else
                            return Collections.emptyList();
                    }

                    @Override
                    public List<Number> tuple(List<DataTypeValue> types) throws InternalException, UserException
                    {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Number> array(DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
                    {
                        return Collections.emptyList();
                    }
                });
            }
            else
                return Collections.emptyList();
        }

        @Override
        public List<Number> end() throws UserException
        {
            return numericFold.end();
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SummaryStatistics that = (SummaryStatistics) o;

        if (!srcTableId.equals(that.srcTableId)) return false;
        if (!summaries.equals(that.summaries)) return false;
        return splitBy.equals(that.splitBy);

    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + srcTableId.hashCode();
        result = 31 * result + summaries.hashCode();
        result = 31 * result + splitBy.hashCode();
        return result;
    }
    */

    @Override
    public boolean transformationEquals(Transformation o)
    {
        SummaryStatistics that = (SummaryStatistics) o;

        if (!srcTableId.equals(that.srcTableId)) return false;
        if (!summaries.equals(that.summaries)) return false;
        return splitBy.equals(that.splitBy);
    }

    @Override
    public int transformationHashCode()
    {
        int result = srcTableId.hashCode();
        result = 31 * result + summaries.hashCode();
        result = 31 * result + splitBy.hashCode();
        return result;
    }
}
