package records.transformations;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.ComparableValue;
import records.data.datatype.DataTypeValue;
import records.data.datatype.ListExDTV;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.SummaryColContext;
import records.grammar.TransformationParser.SummaryContext;
import records.gui.View;
import records.loadsave.OutputBuilder;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionList;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Created by neil on 21/10/2016.
 */
@OnThread(Tag.Simulation)
public class SummaryStatistics extends Transformation implements SingleSourceTransformation
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
    private final String error;
    private final @Nullable TransformationRecordSet result;
    private final JoinedSplit splits;

    // A BitSet, with a cached array of int indexes indicating the set bits 
    public static class Occurrences
    {
        private final BitSet bitSet;
        private int @MonotonicNonNull [] indexes; // The indexes of the set bits

        public Occurrences(BitSet bitSet)
        {
            this.bitSet = bitSet;
        }

        public int[] getIndexes()
        {
            if (indexes == null)
            {
                indexes = new int[bitSet.cardinality()];
                int dest = 0;
                for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i+1))
                {
                    indexes[dest++] = i;
                }
            }
            return indexes;
        }
    }
    
    @OnThread(Tag.Simulation)
    private static class JoinedSplit
    {
        private final ImmutableList<Column> columns;
        // This list is in sorted order of the value list,
        // because of the way it is constructed
        private final ImmutableList<Pair<List<@Value Object>, Occurrences>> valuesAndOccurrences;

        // Creates an empty split, which has one occurrence at all indexes
        public JoinedSplit(int length)
        {
            columns = ImmutableList.of();
            BitSet bitSet = new BitSet();
            bitSet.set(0, length);
            valuesAndOccurrences = ImmutableList.of(new Pair<List<@Value Object>, Occurrences>(ImmutableList.<@Value Object>of(), new Occurrences(bitSet)));
        }

        public JoinedSplit(Column column, TreeMap<ComparableValue, BitSet> valuesAndOccurrences)
        {
            this.columns = ImmutableList.of(column);
            ImmutableList.Builder<Pair<List<@Value Object>, Occurrences>> valOccBuilder = ImmutableList.builderWithExpectedSize(valuesAndOccurrences.size());
            valuesAndOccurrences.forEach((ComparableValue k, BitSet v) ->
            {
                valOccBuilder.add(new Pair<>(ImmutableList.<@Value Object>of(k.getValue()), new Occurrences(v)));
            });
            this.valuesAndOccurrences = valOccBuilder.build();
        }
        
        public JoinedSplit(ImmutableList<Column> columns, ImmutableList<Pair<List<@Value Object>, Occurrences>> valuesAndOccurrences)
        {
            this.columns = columns;
            this.valuesAndOccurrences = valuesAndOccurrences;
        }

        /*
        public boolean satisfied(int index) throws InternalException, UserException
        {
            for (int c = 0; c < colName.size(); c++)
            {
                if (Utility.compareValues(colName.get(c).getType().getCollapsed(index), colValue.get(c)) != 0)
                    return false;
            }
            return true;
        }
        */
    }

    public SummaryStatistics(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, ImmutableList<Pair<ColumnId, Expression>> summaries, ImmutableList<ColumnId> splitBy) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.summaries = summaries;
        this.splitBy = splitBy;
        RecordSet src = null;
        int srcLength = 0;
        String theError = "Could not find source table: \"" + srcTableId + "\"";
        try
        {
            src = this.src != null ? this.src.getData() : null;
            srcLength = src == null ? 0 : src.getLength();
        }
        catch (UserException e)
        {
            theError = "Error with source table: \"" + srcTableId + "\"";
        }
        if (this.src == null || src == null)
        {
            error = theError;
            splits = new JoinedSplit(srcLength);
            result = null;
            return;
        }
        
        theError = "Unknown error with table \"" + getId() + "\"";

        JoinedSplit theSplits;
        try
        {
            theSplits = calcSplits(src, srcLength, splitBy);
        }
        catch (UserException e)
        {
            this.error = e.getLocalizedMessage();
            this.splits = new JoinedSplit(srcLength);
            this.result = null;
            return;
        }
        
        this.error = theError;
        this.splits = theSplits;
        this.result = new TransformationRecordSet()
        {
            @Override
            public boolean indexValid(int index) throws UserException
            {
                return index < getLength();
            }

            @Override
            @SuppressWarnings("units")
            public @TableDataRowIndex int getLength() throws UserException
            {
                return summaries.isEmpty() && splitBy.isEmpty() ? 0 : splits.valuesAndOccurrences.size();
            }
        };

        
        if (!splitBy.isEmpty())
        {
            for (int i = 0; i < splitBy.size(); i++)
            {
                Column orig = theSplits.columns.get(i);
                int splitColumnIndex = i;
                result.buildColumn(rs -> new Column(rs, orig.getName())
                {
                    private @Value Object getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        return Utility.getI(Utility.getI(splits.valuesAndOccurrences,index).getFirst(), splitColumnIndex);
                    }

                    @Override
                    public DataTypeValue getType() throws InternalException, UserException
                    {
                        return addManualEditSet(getName(), orig.getType().getType().fromCollapsed(this::getWithProgress));
                    }

                    @Override
                    public boolean isAltered()
                    {
                        return true;
                    }
                });
            }
        }
        
        ColumnLookup columnLookup = new AggTableLookup(getManager());
            
        // It's important that our splits and record set are initialised
        // before trying to calculate these expressions:
        for (Pair<ColumnId, Expression> e : summaries)
        {
            Expression expression = e.getSecond();
            ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
            try
            {
                @Nullable TypeExp type = expression.checkExpression(columnLookup, new TypeState(mgr.getUnitManager(), mgr.getTypeManager()), errors);
                @Nullable DataType concrete = type == null ? null : errors.recordLeftError(mgr.getTypeManager(), FunctionList.getFunctionLookup(mgr.getUnitManager()), expression, type.toConcreteType(mgr.getTypeManager()));
                if (type == null || concrete == null)
                    throw new UserException((@NonNull StyledString) errors.getAllErrors().findFirst().orElse(StyledString.s("Unknown type error")));
                @NonNull DataType typeFinal = concrete;
                result.buildColumn(rs -> typeFinal.makeCalculatedColumn(rs, e.getFirst(), i -> expression.calculateValue(new EvaluateState(mgr.getTypeManager(), OptionalInt.of(i), errors)).value));
            }
            catch (UserException ex)
            {
                result.buildColumn(rs -> new ErrorColumn(rs, getManager().getTypeManager(), e.getFirst(), ex.getStyledMessage()));
            }
        }
    }

    private static class SingleSplit
    {
        private final Column column;
        private final TreeMap<@NonNull ComparableValue, BitSet> valuesAndOccurrences;

        public SingleSplit(Column column, TreeMap<@NonNull ComparableValue, BitSet> valuesAndOccurrences)
        {
            this.column = column;
            this.valuesAndOccurrences = valuesAndOccurrences;
        }
    }

    private static JoinedSplit calcSplits(RecordSet src, int srcLength, List<ColumnId> splitBy) throws UserException, InternalException
    {
        // Each item in outer is a column in the set of columns which we are splitting by.
        // Each item in inner.values is a possible value of that column;
        List<SingleSplit> splits = new ArrayList<>();
        for (ColumnId colName : splitBy)
        {
            Column c = src.getColumn(colName);
            //Optional<List<@NonNull ?>> fastDistinct = c.fastDistinct();
            //if (fastDistinct.isPresent())
            //    splits.add(new SingleSplit(c, fastDistinct.get()));
            //else
            {
                // A bit is set if the value occurred at a particular index:
                TreeMap<ComparableValue, BitSet> r = new TreeMap<>();
                try
                {
                    for (int i = 0; c.indexValid(i); i++)
                    {
                        int iFinal = i;
                        r.compute(new ComparableValue(c.getType().getCollapsed(i)), (k, bs) -> {
                            if (bs == null)
                                bs = new BitSet();
                            bs.set(iFinal);
                            return bs;
                        });
                    }
                    splits.add(new SingleSplit(c, r));
                }
                // Unwrap exceptions that occur in the comparator:
                catch (RuntimeException e)
                {
                    if (e.getCause() instanceof UserException)
                        throw (UserException)e.getCause();
                    if (e.getCause() instanceof InternalException)
                        throw (InternalException) e.getCause();
                    throw new InternalException("Unexpected Runtime exception", e);
                }
            }

        }
        // Now form cross-product:
        return crossProduct(splits, srcLength);
    }

    /**
     * Given a list of columns and their values and index occurrences, calculates the 
     * set of all combinations that occur at least once.
     * 
     * @param allDistincts The list of per-single-column values and occurrences (one entry per column)
     * @return The list of all joined value combinations of the split columns that actually occur at least once. 
     */
    private static JoinedSplit crossProduct(List<SingleSplit> allDistincts, int srcLength)
    {
        if (allDistincts.isEmpty())
        {
            return new JoinedSplit(srcLength);
        }
        else if (allDistincts.size() == 1)
        {
            // Turn single split into a joined split:
            SingleSplit single = allDistincts.get(0);
            return new JoinedSplit(single.column, single.valuesAndOccurrences);
        }
        // Take next list:
        SingleSplit first = allDistincts.get(0);
        // Recurse to get cross product of the rest:
        JoinedSplit rest = crossProduct(allDistincts.subList(1, allDistincts.size()), srcLength);
        // Don't know size in advance because not all combinations will occur:
        ImmutableList.Builder<Pair<List<@Value Object>, Occurrences>> valuesAndOccurrences = ImmutableList.builder();
        // Important that the first list is outermost, so that
        // we add all the items with earliest value of first, thus
        // keeping the result in order of first column foremost,
        // then going by order of rest.
        first.valuesAndOccurrences.forEach((firstVal, firstOcc) ->
        {
            for (Pair<List<@Value Object>, Occurrences> restPair : rest.valuesAndOccurrences)
            {
                // BitSet is mutable, so important to clone:
                BitSet jointOccurrence = (BitSet)firstOcc.clone();
                jointOccurrence.and(restPair.getSecond().bitSet);
                if (!jointOccurrence.isEmpty())
                {
                    valuesAndOccurrences.add(new Pair<>(Utility.prependToList(firstVal.getValue(), restPair.getFirst()), new Occurrences(jointOccurrence)));
                }
            }
        });
        return new JoinedSplit(Utility.prependToList(first.column, rest.columns), valuesAndOccurrences.build());
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
                summaryTypes.add(new Pair<>(new ColumnId(sumType.column.getText()), Expression.parse(null, sumType.expression().EXPRESSION().getText(), mgr.getTypeManager(), FunctionList.getFunctionLookup(mgr.getUnitManager()))));
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
            b.id(renames.columnId(getId(), entry.getFirst()).getSecond());
            b.t(TransformationLexer.EXPRESSION_BEGIN, TransformationLexer.VOCABULARY);
            b.raw(entry.getSecond().save(true, BracketedStatus.MISC, renames));
            b.nl();
        }
        for (ColumnId c : splitBy)
        {
            b.kw("SPLIT").id(renames.columnId(srcTableId, c).getSecond()).nl();
        }
        return b.toLines();
    }
    
    @OnThread(Tag.Any)
    public TableId getSrcTableId()
    {
        return srcTableId;
    }

    @Override
    public @OnThread(Tag.Simulation) Transformation withNewSource(TableId newSrcTableId) throws InternalException
    {
        return new SummaryStatistics(getManager(), getDetailsForCopy(), newSrcTableId, summaries, splitBy);
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
    
    public class AggTableLookup implements ColumnLookup
    {
        private final TableManager tableManager;

        @OnThread(Tag.Any)
        private AggTableLookup(TableManager tableManager)
        {
            this.tableManager = tableManager;
        }
        
        @Override
        public @Nullable Pair<TableId, DataTypeValue> getColumn(@Nullable TableId tableId, ColumnId columnId, ColumnReferenceType columnReferenceType)
        {
            boolean grouped = false;
            if (tableId == null || tableId.equals(getId()) || tableId.equals(srcTableId))
            {
                // It's us -- may be a grouped or split column
                grouped = !splitBy.contains(columnId);
            }

            try
            {
                Table table = tableId == null || tableId.equals(getId()) ? SummaryStatistics.this : tableManager.getSingleTableOrNull(tableId);
                if (table == null)
                    return null;
                Column column = table.getData().getColumnOrNull(columnId);
                if (column == null && tableId == null)
                {
                    // Could be in our source table but not copied forwards to us:
                    Table srcTable = tableManager.getSingleTableOrNull(srcTableId);
                    if (srcTable != null)
                    {
                        column = srcTable.getData().getColumnOrNull(columnId);
                        table = srcTable;
                    }
                }
                
                if (column == null)
                    throw new UserException("Could not find column: " + columnId.getRaw());

                @NonNull Column columnFinal = column;
                switch (columnReferenceType)
                {
                    case CORRESPONDING_ROW:
                        if (grouped)
                        {
                            return new Pair<>(table.getId(), DataTypeValue.array(column.getType().getType(), (i, prog) -> {
                                Pair<List<@Value Object>, Occurrences> splitInfo = splits.valuesAndOccurrences.get(i);
                                return DataTypeUtility.value(new ListExDTV(splitInfo.getSecond().getIndexes().length, columnFinal.getType().getType().fromCollapsed((j, prog2) -> columnFinal.getType().getCollapsed(splitInfo.getSecond().getIndexes()[j]))));
                            }));
                        }
                        else
                        {
                            // If not grouped, must be in split by
                            return new Pair<>(table.getId(), columnFinal.getType());
                        }
                    case WHOLE_COLUMN:
                        return new Pair<>(table.getId(), DataTypeValue.array(column.getType().getType(), (i, prog) -> DataTypeUtility.value(new ListExDTV(columnFinal))));
                }
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
            catch (UserException e)
            {
                // Just give back null:
            }
            return null;
        }

        @Override
        public Stream<ColumnReference> getAvailableColumnReferences()
        {
            return
                tableManager.streamAllTablesAvailableTo(getId())
                    .flatMap(t -> {
                        try
                        {
                            @Nullable TableId tableId = t.getId().equals(getId()) || t.getId().equals(srcTableId) ? null : t.getId();
                            return t.getData().getColumns().stream()
                                .flatMap(c -> (tableId != null ? Stream.of(ColumnReferenceType.WHOLE_COLUMN) : Arrays.stream(ColumnReferenceType.values()))
                                .map(rt -> new ColumnReference(tableId, c.getName(), rt)));
                        }
                        catch (UserException e)
                        {
                        }
                        catch (InternalException e)
                        {
                            Log.log(e);
                        }
                        return Stream.empty();
                    }).distinct();
        }
    }

    @OnThread(Tag.Any)
    public ColumnLookup getColumnLookup()
    {
        return new AggTableLookup(getManager());
    }
}
