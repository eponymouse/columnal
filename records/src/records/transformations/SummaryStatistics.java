package records.transformations;

import javafx.application.Platform;
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
import records.data.CalculatedColumn;
import records.data.CalculatedColumn.FoldOperation;
import records.data.CalculatedNumericColumn;
import records.data.Column;
import records.data.NumericColumnStorage;
import records.data.RecordSet;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorGet;
import records.data.datatype.DataType.GetValue;
import records.data.datatype.DataType.TagType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Workers;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

/**
 * Created by neil on 21/10/2016.
 */
@OnThread(Tag.Simulation)
public class SummaryStatistics extends Transformation
{
    public static enum SummaryType
    {
        COUNT, MEAN, MIN, MAX, SUM;
    }

    private final RecordSet result;

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
                if (!colName.get(c).getCollapsed(index).equals(colValue.get(c)))
                    return false;
            }
            return true;
        }
    }

    public SummaryStatistics(RecordSet src, Map<String, Set<SummaryType>> summaries, List<String> splitBy) throws InternalException, UserException
    {
        List<JoinedSplit> splits = calcSplits(src, splitBy);

        List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();

        // Will be zero by default, which we take advantage of:
        int[] splitIndexes = new int[src.getLength()];

        if (!splitBy.isEmpty())
        {
            for (int i = 0; i < splitBy.size(); i++)
            {
                String colName = splitBy.get(i);
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
                    public String getName()
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

        for (Entry<String, Set<SummaryType>> e : summaries.entrySet())
        {
            for (SummaryType summaryType : e.getValue())
            {
                Column srcCol = src.getColumn(e.getKey());
                String name = e.getKey() + "." + summaryType;

                columns.add(srcCol.getType().apply(new DataTypeVisitorGet<FunctionInt<RecordSet, Column>>()
                {
                    @Override
                    public FunctionInt<RecordSet, Column> number(GetValue<Number> srcGet) throws InternalException, UserException
                    {
                        return rs -> new CalculatedNumericColumn(rs, name, srcCol.getType(), srcCol)
                        {
                            @Override
                            protected void fillNextCacheChunk() throws UserException, InternalException
                            {
                                int index = cache.filled();

                                JoinedSplit split = splits.get(index);
                                switch (summaryType)
                                {
                                    case MIN:
                                    case MAX:
                                        @MonotonicNonNull
                                        Number cur = null;
                                        for (int i = 0; srcCol.indexValid(i); i++)
                                        {
                                            if (splitIndexes[i] != index)
                                                continue;

                                            @NonNull
                                            Number x = srcGet.get(i);
                                            if (cur == null)
                                            {
                                                cur = x;
                                            } else
                                            {
                                                int comparison = Utility.compareNumbers(cur, x);
                                                if ((summaryType == SummaryType.MIN && comparison > 0)
                                                    || (summaryType == SummaryType.MAX && comparison < 0))
                                                    cur = x;
                                            }
                                        }
                                        if (cur != null)
                                            cache.addNumber(cur);
                                        else
                                            throw new UserException("No values for " + summaryType);
                                }
                            }
                        };
                    }

                    @Override
                    public FunctionInt<RecordSet, Column> text(GetValue<String> g) throws InternalException, UserException
                    {
                        throw new UnimplementedException();
                    }

                    @Override
                    public FunctionInt<RecordSet, Column> tagged(List<TagType> tagTypes, GetValue<Integer> getTag) throws InternalException, UserException
                    {
                        if (DataType.canFitInOneNumeric(tagTypes))
                        {
                            boolean ignoreOtherTags = true; //TODO configure through GUI
                            int numericTag = DataType.findNumericTag(tagTypes);
                            return rs -> new CalculatedColumn(rs, name, srcCol)
                            {
                                NumericColumnStorage cache = new NumericColumnStorage(0);

                                @Override
                                protected void fillNextCacheChunk() throws UserException, InternalException
                                {
                                    int index = cache.filled();

                                    JoinedSplit split = splits.get(index);
                                    switch (summaryType)
                                    {
                                        case MIN:
                                        case MAX:
                                            @MonotonicNonNull
                                            Number cur = null;
                                            for (int i = 0; srcCol.indexValid(i); i++)
                                            {
                                                if (splitIndexes[i] != index)
                                                    continue;

                                                Integer tag = getTag.get(i);
                                                if (tagTypes.get(tag).getInner() == null)
                                                    continue;

                                                int iFinal = i;
                                                @NonNull
                                                Number x = tagTypes.get(tag).getInner().apply(new DataTypeVisitorGet<Number>()
                                                {
                                                    @Override
                                                    public Number number(GetValue<Number> g) throws InternalException, UserException
                                                    {
                                                        return g.get(iFinal);
                                                    }

                                                    @Override
                                                    public Number text(GetValue<String> g) throws InternalException, UserException
                                                    {
                                                        throw new InternalException("Text when convinced only numbers");
                                                    }

                                                    @Override
                                                    public Number tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException
                                                    {
                                                        throw new InternalException("Tagged when convinced only numbers");
                                                    }
                                                });
                                                if (cur == null)
                                                {
                                                    cur = x;
                                                } else
                                                {
                                                    int comparison = Utility.compareNumbers(cur, x);
                                                    if ((summaryType == SummaryType.MIN && comparison > 0)
                                                        || (summaryType == SummaryType.MAX && comparison < 0))
                                                        cur = x;
                                                }
                                            }
                                            if (cur != null)
                                                cache.addNumber(cur);
                                            else
                                                throw new UserException("No values for " + summaryType);
                                    }
                                }

                                @Override
                                protected void clearCache() throws InternalException
                                {
                                    cache = new NumericColumnStorage(0);
                                }

                                @Override
                                protected int getCacheFilled()
                                {
                                    int filled = cache.filled();
                                    return filled;
                                }

                                @Override
                                public DataType getType() throws UserException, InternalException
                                {
                                    return srcCol.getType().copy((i, prog) ->
                                    {
                                        fillCacheWithProgress(i, prog);
                                        int tag = cache.getTag(i);
                                        if (tag == -1)
                                            tag = numericTag;
                                        if (tagTypes.get(tag).getInner() == null)
                                            return Collections.<Object>singletonList((Integer)tag);
                                        else
                                            return Arrays.<Object>asList(tag, cache.get(i));
                                    });
                                }
                            };
                        }
                        else
                            throw new UnimplementedException();
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

    private static List<JoinedSplit> calcSplits(RecordSet src, List<String> splitBy) throws UserException, InternalException
    {
        // Each item in outer is a column.
        // Each item in inner is a possible value of that column;
        List<SingleSplit> splits = new ArrayList<>();
        for (String colName : splitBy)
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
                    r.add(c.getCollapsed(i));
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

    @Override
    @NotNull
    @OnThread(Tag.Any)
    public RecordSet getResult()
    {
        return result;
    }

    @OnThread(Tag.FXPlatform)
    public static class Info extends TransformationInfo
    {
        @MonotonicNonNull
        @OnThread(Tag.Any)
        private RecordSet src;
        private final BooleanProperty ready = new SimpleBooleanProperty(false);
        private final ObservableList<@NonNull Pair<Column, SummaryType>> ops = FXCollections.observableArrayList();

        public Info()
        {
            super("stats", Arrays.asList("min", "max"), "Basic Statistics");
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Pane getParameterDisplay(RecordSet src)
        {
            this.src = src;
            HBox colsAndSummaries = new HBox();
            ListView<Column> columnListView = getColumnListView(src);
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
                        if (!ops.contains(op))
                            ops.add(op);
                    }
                });
                buttons.getChildren().add(button);
            }
            colsAndSummaries.getChildren().add(buttons);

            ListView<Pair<Column, SummaryType>> opListView = Utility.readOnlyListView(ops, op -> op.getFirst().getName() + "." + op.getSecond().toString());
            colsAndSummaries.getChildren().add(opListView);
            return colsAndSummaries;
        }

        @Override
        public BooleanExpression canPressOk()
        {
            return ready;
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation()
        {
            return () -> {
                if (src == null)
                    throw new NullPointerException("Null source for transformation");

                Map<String, Set<SummaryType>> summaries = new HashMap<>();
                for (Pair<Column, SummaryType> op : ops)
                {
                    Set<SummaryType> summaryTypes = summaries.computeIfAbsent(op.getFirst().getName(), s -> new HashSet<SummaryType>());
                    summaryTypes.add(op.getSecond());
                }
                return new SummaryStatistics(src, summaries, Collections.emptyList());
            };
        }
    }
}
