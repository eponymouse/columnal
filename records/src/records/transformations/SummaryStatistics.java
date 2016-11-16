package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CalculatedNumericColumn;
import records.data.CalculatedStringColumn;
import records.data.CalculatedTaggedColumn;
import records.data.Column;
import records.data.ColumnId;
import records.data.ColumnStorage;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorGet;
import records.data.datatype.DataType.GetValue;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.BasicLexer;
import records.grammar.SortParser;
import records.grammar.SortParser.OrderByContext;
import records.grammar.SortParser.SplitByContext;
import records.grammar.SortParser.SummaryColContext;
import records.grammar.SortParser.SummaryContext;
import records.grammar.SortParser.SummaryTypeContext;
import records.gui.TableDisplay;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by neil on 21/10/2016.
 */
@OnThread(Tag.Simulation)
public class SummaryStatistics extends Transformation
{
    public static final String NAME = "stats";
    private final @Nullable Table src;
    private final TableId srcTableId;
    @OnThread(Tag.Any)
    private final Map<ColumnId, Set<SummaryType>> summaries;
    @OnThread(Tag.Any)
    private final List<ColumnId> splitBy;
    @OnThread(Tag.Any)
    private String error;

    public static enum SummaryType
    {
        COUNT, MEAN, MIN, MAX, SUM;
    }

    private final @Nullable RecordSet result;

    @OnThread(Tag.Simulation)
    private static class JoinedSplit
    {
        private final List<Column> colName = new ArrayList<>();
        private final List<List<Object>> colValue = new ArrayList<>();

        public JoinedSplit()
        {
        }

        public JoinedSplit(Column column, List<Object> value, JoinedSplit addTo)
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

    private SummaryStatistics(TableManager mgr, @Nullable TableId thisTableId, TableId srcTableId, Map<ColumnId, Set<SummaryType>> summaries, List<ColumnId> splitBy) throws InternalException, UserException
    {
        super(mgr, thisTableId);
        this.srcTableId = srcTableId;
        this.src = mgr.getTable(srcTableId);
        this.summaries = summaries;
        this.splitBy = splitBy;
        if (this.src == null)
        {
            this.result = null;
            error = "Could not find source table: \"" + srcTableId + "\"";
            throw new WholeTableException(error);
        }
        else
            error = "Unknown error with table \"" + getId() + "\"";
        RecordSet src = this.src.getData();
        List<JoinedSplit> splits = calcSplits(src, splitBy);

        List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();

        // Will be zero by default, which we take advantage of:
        int[] splitIndexes = new int[src.getLength()];

        if (!splitBy.isEmpty())
        {
            for (int i = 0; i < splitBy.size(); i++)
            {
                ColumnId colName = splitBy.get(i);
                Column orig = src.getColumn(colName);
                int iFinal = i;
                columns.add(rs -> new Column(rs)
                {
                    private List<Object> getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        return splits.get(index).colValue.get(iFinal);
                    }

                    @Override
                    @OnThread(Tag.Any)
                    public ColumnId getName()
                    {
                        return colName;
                    }

                    @Override
                    public long getVersion()
                    {
                        return 1;
                    }

                    @Override
                    public DataType getType() throws InternalException, UserException
                    {
                        return orig.getType().copy(this::getWithProgress);
                    }
                });
            }

            outer: for (int i = 0; i < splitIndexes.length; i++ )
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

        for (Entry<ColumnId, Set<SummaryType>> e : summaries.entrySet())
        {
            for (SummaryType summaryType : e.getValue())
            {
                Column srcCol = src.getColumn(e.getKey());
                ColumnId name = new ColumnId(e.getKey() + "." + summaryType);

                columns.add(srcCol.getType().apply(new DataTypeVisitorGet<FunctionInt<RecordSet, Column>>()
                {
                    @Override
                    public FunctionInt<RecordSet, Column> number(GetValue<Number> srcGet, NumberDisplayInfo displayInfo) throws InternalException, UserException
                    {
                        if (summaryType == SummaryType.COUNT)
                            return countColumn(srcGet);
                        return rs -> new CalculatedNumericColumn(rs, name, srcCol.getType(), srcCol)
                        {
                            @Override
                            protected void fillNextCacheChunk() throws UserException, InternalException
                            {
                                int index = getCacheFilled();
                                final FoldOperation<Number, Number> fold;
                                switch (summaryType)
                                {
                                    case MIN:
                                    case MAX:
                                        fold = new MinMaxNumericFold(summaryType);
                                        break;
                                    case MEAN:
                                    case SUM:
                                        fold = new MeanSumFold(summaryType);
                                        break;
                                    default:
                                        throw new InternalException("Unrecognised summary type");
                                }
                                applyFold(cache, fold, srcCol, srcGet, splitIndexes, index);
                            }
                        };
                    }

                    @Override
                    public FunctionInt<RecordSet, Column> text(GetValue<String> srcGet) throws InternalException, UserException
                    {
                        if (summaryType == SummaryType.COUNT)
                            return countColumn(srcGet);
                        return rs -> new CalculatedStringColumn(rs, name, srcCol.getType(), srcCol) {

                            @Override
                            protected void fillNextCacheChunk() throws UserException, InternalException
                            {
                                int index = getCacheFilled();
                                final FoldOperation<String, String> fold;
                                switch (summaryType)
                                {
                                    case MIN:
                                    case MAX:
                                        fold = new MinMaxStringFold(summaryType);
                                        break;
                                    case MEAN:
                                    case SUM:
                                        throw new UserException("Cannot perform " + summaryType + " on String data");
                                    default:
                                        throw new InternalException("Unrecognised summary type");
                                }
                                applyFold(cache, fold, srcCol, srcGet, splitIndexes, index);
                            }
                        };
                    }

                    private <T> FunctionInt<RecordSet,Column> countColumn(GetValue<T> srcGet)
                    {
                        return rs -> new CalculatedNumericColumn(rs, name, DataType.INTEGER, srcCol)
                        {
                            @Override
                            protected void fillNextCacheChunk() throws UserException, InternalException
                            {
                                int index = getCacheFilled();
                                applyFold(cache, new CountFold<T>(), srcCol, srcGet, splitIndexes, index);
                            }
                        };
                    }

                    @Override
                    public FunctionInt<RecordSet, Column> tagged(List<TagType> tagTypes, GetValue<Integer> getTag) throws InternalException, UserException
                    {
                        boolean ignoreNullaryTags = true; //TODO configure through GUI

                        if (summaryType == SummaryType.COUNT && !ignoreNullaryTags)
                            return countColumn(getTag); // Just need to count any entry
                        if (summaryType == SummaryType.MEAN || summaryType == SummaryType.SUM)
                        {
                            return rs -> new CalculatedNumericColumn(rs, name, DataType.INTEGER, srcCol)
                            {
                                @Override
                                protected void fillNextCacheChunk() throws UserException, InternalException
                                {
                                    int index = getCacheFilled();
                                    applyFold(cache, new MeanSumTaggedFold(summaryType, tagTypes), srcCol, getTag, splitIndexes, index);
                                }
                            };
                        }
                        return rs -> new CalculatedTaggedColumn(rs, name, tagTypes, srcCol)
                        {
                            @Override
                            protected void fillNextCacheChunk() throws UserException, InternalException
                            {
                                int index = getCacheFilled();
                                final FoldOperation<Integer, List<Object>> fold;
                                switch (summaryType)
                                {
                                    case MIN:
                                    case MAX:
                                        fold = new MinMaxTaggedFold(summaryType, tagTypes, ignoreNullaryTags);
                                        break;
                                    default:
                                        throw new UnimplementedException();
                                }
                                applyFold(this, fold, srcCol, getTag, splitIndexes, index);
                            }

                        };
                    }
                }));
            }
        }
        result = new RecordSet("Summary", columns) {
            @Override
            public boolean indexValid(int index) throws UserException
            {
                return index < splits.size();
            }

            @Override
            public int getLength() throws UserException
            {
                return splits.size();
            }
        };
    }

    private static class SingleSplit
    {
        private Column column;
        private List<@NonNull List<@NonNull Object>> values;

        public SingleSplit(Column column, List<@NonNull List<@NonNull Object>> values)
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
                HashSet<List<Object>> r = new HashSet<>();
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
        for (List<Object> o : cur.values)
        {
            for (JoinedSplit js : rest)
            {
                r.add(new JoinedSplit(cur.column, o, js));
            }
        }
        return r;
    }

    /*
    @OnThread(Tag.FXPlatform)
    public static void withGUICreate(RecordSet src, FXPlatformConsumer<SummaryStatistics> andThen) throws InternalException, UserException
    {
        // TODO actually show GUI
        Map<String, Set<SummaryType>> summaries = new HashMap<>();
        for (Column c : src.getColumns())
        {
            if (!c.getName().equals("Mistake"))
                summaries.put(c.getName(), new HashSet<>(Arrays.asList(SummaryType.MIN, SummaryType.MAX)));
        }

        Workers.onWorkerThread("Create summary statistics", () -> {
            Utility.alertOnError(() -> {
                SummaryStatistics ss = new SummaryStatistics(src, summaries, Collections.singletonList("Mistake"));
                Platform.runLater(() -> andThen.consume(ss));
                return (Void)null;
            });
        });
    }
    */

    @Override
    @NotNull
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
    public static class Info extends TransformationInfo
    {
        private @MonotonicNonNull TableDisplay src;
        private final BooleanProperty ready = new SimpleBooleanProperty(false);
        private final ObservableList<@NonNull Pair<Column, SummaryType>> ops = FXCollections.observableArrayList();
        private final ObservableList<@NonNull Column> splitBy = FXCollections.observableArrayList();

        @OnThread(Tag.Any)
        public Info()
        {
            super(NAME, Arrays.asList("min", "max"), "Basic Statistics");
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Pane getParameterDisplay(TableDisplay src, FXPlatformConsumer<Exception> reportError)
        {
            this.src = src;
            HBox colsAndSummaries = new HBox();
            ListView<Column> columnListView = getColumnListView(this.src.getRecordSet());
            columnListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            colsAndSummaries.getChildren().add(columnListView);

            VBox buttons = new VBox();
            for (SummaryType summaryType : SummaryType.values())
            {
                Button button = new Button(summaryType.toString() + ">>");
                button.setOnAction(e -> {
                    for (Column column : columnListView.getSelectionModel().getSelectedItems())
                    {
                        Pair<Column, SummaryType> op = new Pair<>(column, summaryType);
                        try
                        {
                            if (valid(op.getFirst(), op.getSecond()) && !ops.contains(op))
                                ops.add(op);
                        }
                        catch (Exception ex)
                        {
                            reportError.consume(ex);
                        }
                    }
                });
                buttons.getChildren().add(button);
            }
            Button button = new Button("Split>>");
            button.setOnAction(e -> {
                splitBy.addAll(columnListView.getSelectionModel().getSelectedItems());
            });
            buttons.getChildren().add(button);
            colsAndSummaries.getChildren().add(buttons);

            ListView<Pair<Column, SummaryType>> opListView = Utility.readOnlyListView(ops, op -> op.getFirst().getName() + "." + op.getSecond().toString());
            ListView<Column> splitListView = Utility.readOnlyListView(splitBy, s -> s.getName().toString());
            colsAndSummaries.getChildren().add(new VBox(opListView, splitListView));
            return colsAndSummaries;
        }

        private static boolean valid(Column src, SummaryType summaryType) throws InternalException, UserException
        {
            return src.getType().apply(new DataTypeVisitor<Boolean>()
            {
                @Override
                public Boolean number(NumberDisplayInfo displayInfo) throws InternalException, UserException
                {
                    return true;
                }

                @Override
                public Boolean text() throws InternalException, UserException
                {
                    switch (summaryType)
                    {
                        case MEAN: case SUM:
                            return false;
                        default:
                            return true;
                    }
                }

                @Override
                public Boolean tagged(List<TagType> tags) throws InternalException, UserException
                {
                    // For MEAN and SUM, as long as one is numeric, it's potentially valid:
                    for (TagType tagType : tags)
                    {
                        @Nullable DataType inner = tagType.getInner();
                        if (inner != null && inner.apply(this))
                            return true;
                    }
                    return false;
                }
            });

        }

        @Override
        public BooleanExpression canPressOk()
        {
            return ready;
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            return () -> {
                if (src == null)
                    throw new InternalException("Null source for transformation");

                Map<ColumnId, Set<SummaryType>> summaries = new HashMap<>();
                for (Pair<Column, SummaryType> op : ops)
                {
                    Set<SummaryType> summaryTypes = summaries.computeIfAbsent(op.getFirst().getName(), s -> new HashSet<SummaryType>());
                    summaryTypes.add(op.getSecond());
                }
                return new SummaryStatistics(mgr, null, src.getTable().getId(), summaries, Utility.<Column, ColumnId>mapList(splitBy, Column::getName));
            };
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, TableId tableId, String detail) throws InternalException, UserException
        {
            SummaryContext loaded = Utility.parseAsOne(detail, BasicLexer::new, SortParser::new).summary();

            Map<ColumnId, Set<SummaryType>> summaryTypes = new HashMap<>();
            for (SummaryColContext sumType : loaded.summaryCol())
            {
                HashSet<SummaryType> summaries = new HashSet<>();
                for (SummaryTypeContext type : sumType.summaryType())
                {
                    summaries.add(SummaryType.valueOf(type.getText()));
                }
                summaryTypes.put(new ColumnId(sumType.column.getText()), summaries);
            }
            List<ColumnId> splits = Utility.<SplitByContext, ColumnId>mapList(loaded.splitBy(), s -> new ColumnId(s.getText()));
            return new SummaryStatistics(mgr, tableId, new TableId(loaded.srcTableId.getText()), summaryTypes, splits);
        }
    }

    @Override
    protected @OnThread(Tag.FXPlatform) List<String> saveDetail(@Nullable File destination)
    {
        List<String> details = new ArrayList<>();
        details.add("SUMMARY " + srcTableId.getOutput());
        for (Entry<ColumnId, Set<SummaryType>> entry : summaries.entrySet())
        {
            if (!entry.getValue().isEmpty())
            {
                details.add("FROM " + entry.getKey().getOutput() + " " + entry.getValue().stream().<String>map(SummaryType::toString).collect(Collectors.joining(" ")));
            }
        }
        for (ColumnId c : splitBy)
        {
            details.add("SPLIT " + c.getOutput());
        }
        return details;
    }

    @Override
    public @OnThread(Tag.FXPlatform) String getTransformationLabel()
    {
        return "Summary";
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Table getSource() throws UserException
    {
        if (src == null)
            throw new UserException(error);
        return src;
    }

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
        public List<T> process(@NonNull T x, int i)
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
        public List<T> end() throws UserException
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
    private static class MinMaxStringFold extends MinMaxFold<String>
    {
        public MinMaxStringFold(SummaryType summaryType)
        {
            super(summaryType);
        }

        @Override
        protected int compare(String a, String b)
        {
            return a.compareTo(b);
        }
    }

    public static class MinMaxTaggedFold implements FoldOperation<Integer, List<Object>>
    {
        private int bestTag = -1;
        @Nullable
        List<Object> bestInner = null;
        private final List<TagType> tagTypes;
        private final boolean ignoreNullaryTags;
        private final SummaryType summaryType;

        public MinMaxTaggedFold(SummaryType summaryType, List<TagType> tagTypes, boolean ignoreNullaryTags)
        {
            this.tagTypes = tagTypes;
            this.ignoreNullaryTags = ignoreNullaryTags;
            this.summaryType = summaryType;
        }

        @Override
        public List<List<Object>> process(Integer tagBox, int i) throws InternalException, UserException
        {
            int tag = tagBox;

            @Nullable DataType innerType = tagTypes.get(tag).getInner();
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

            @NonNull
            List<Object> x = innerType.getCollapsed(i);
            if (bestInner == null)
            {
                bestInner = x;
            } else
            {
                int comparison = Utility.compareLists(bestInner, x);
                if ((summaryType == SummaryType.MIN && comparison > 0)
                    || (summaryType == SummaryType.MAX && comparison < 0))
                    bestInner = x;
            }
            return Collections.emptyList();
        }

        @Override
        public List<List<Object>> end() throws UserException
        {
            if (bestInner != null)
            {
                if (!(bestInner instanceof ArrayList))
                    bestInner = new ArrayList<>(bestInner);
                bestInner.add(0, bestTag);
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

    private static <T, R> void applyFold(ColumnStorage<R> cache, FoldOperation<T, R> fold, Column srcCol, GetValue<T> srcGet, int[] splitIndexes, int index) throws InternalException, UserException
    {
        cache.addAllNoNull(fold.start());
        for (int i = 0; srcCol.indexValid(i); i++)
        {
            if (splitIndexes[i] != index)
                continue;

            cache.addAllNoNull(fold.process(srcGet.get(i), i));
        }
        cache.addAllNoNull(fold.end());
    }

    private static void applyFold(CalculatedTaggedColumn c, FoldOperation<Integer, List<Object>> fold, Column srcCol, GetValue<Integer> srcGet, int[] splitIndexes, int index) throws InternalException, UserException
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
        private final List<TagType> tagTypes;

        public MeanSumTaggedFold(SummaryType summaryType, List<TagType> tagTypes)
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
            @Nullable DataType inner = tagTypes.get(n).getInner();
            if (inner != null)
            {
                return inner.apply(new DataTypeVisitorGet<List<Number>>()
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public List<Number> number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException
                    {
                        return numericFold.process(g.get(index), index);
                    }

                    @Override
                    public List<Number> text(GetValue<String> g) throws InternalException, UserException
                    {
                        return Collections.emptyList();
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public List<Number> tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException
                    {
                        @Nullable DataType nestedInner = tagTypes.get(g.get(index)).getInner();
                        if (nestedInner != null)
                            return nestedInner.apply(this);
                        else
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
}
