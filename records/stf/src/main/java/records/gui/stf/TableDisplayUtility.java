package records.gui.stf;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import records.data.CellPosition;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table.Display;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue.DataTypeVisitorGetEx;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.flex.FlexibleTextField;
import records.gui.flex.Recogniser;
import records.gui.flex.recognisers.BooleanRecogniser;
import records.gui.flex.recognisers.StringRecogniser;
import records.gui.flex.recognisers.TupleRecogniser;
import records.gui.stable.ColumnHandler;
import records.gui.stable.EditorKitCache;
import records.gui.stable.EditorKitCache.MakeEditorKit;
import records.gui.stable.EditorKitCallback;
import records.gui.stable.ColumnDetails;
import records.gui.flex.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;
import utility.Utility.ListEx;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by neil on 01/05/2017.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplayUtility
{

    @OnThread(Tag.Any)
    public static ImmutableList<ColumnDetails> makeStableViewColumns(RecordSet recordSet, Pair<Display, Predicate<ColumnId>> columnSelection, Function<ColumnId, @Nullable FXPlatformConsumer<ColumnId>> renameColumn, GetDataPosition getTablePos, @Nullable FXPlatformRunnable onModify)
    {
        ImmutableList.Builder<ColumnDetails> r = ImmutableList.builder();
        @TableDataColIndex int displayColumnIndex = displayCol(0);
        for (int origColIndex = 0; origColIndex < recordSet.getColumns().size(); origColIndex++)
        {
            Column col = recordSet.getColumns().get(origColIndex);
            if (col.shouldShow(columnSelection))
            {
                ColumnDetails item;
                try
                {
                    item = getDisplay(displayColumnIndex, col, renameColumn.apply(col.getName()), getTablePos, onModify != null ? onModify : FXPlatformRunnable.EMPTY);
                }
                catch (InternalException | UserException e)
                {
                    final @TableDataColIndex int columnIndexFinal = displayColumnIndex;
                    // Show a dummy column with an error message:
                    item = new ColumnDetails(col.getName(), DataType.TEXT, null, new ColumnHandler()
                    {
                        @Override
                        public @OnThread(Tag.FXPlatform) void modifiedDataItems(int startRowIncl, int endRowIncl)
                        {
                        }

                        @Override
                        public @OnThread(Tag.FXPlatform) void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount)
                        {
                        }

                        @Override
                        public @OnThread(Tag.FXPlatform) void addedColumn(Column newColumn)
                        {
                        }

                        @Override
                        public @OnThread(Tag.FXPlatform) void removedColumn(ColumnId oldColumnId)
                        {
                        }

                        @Override
                        public void fetchValue(@TableDataRowIndex int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setCellContent)
                        {
                            setCellContent.loadedValue(rowIndex, columnIndexFinal, new records.gui.flex.EditorKitSimpleLabel("Error: " + e.getLocalizedMessage()));
                        }

                        @Override
                        public void columnResized(double width)
                        {

                        }

                        @Override
                        public boolean isEditable()
                        {
                            return false;
                        }

                        @Override
                        public @OnThread(Tag.Simulation) @Value Object getValue(int index) throws InternalException, UserException
                        {
                            throw new UserException("No values");
                        }

                        @Override
                        public void styleTogether(Collection<? extends FlexibleTextField> cellsInColumn, double columnSize)
                        {
                        }
                    });
                }
                r.add(item);
                displayColumnIndex += displayCol(1);
            }
        }
        return r.build();
    }

    @OnThread(Tag.Any)
    private static ColumnDetails getDisplay(@TableDataColIndex int columnIndex, @NonNull Column column, @Nullable FXPlatformConsumer<ColumnId> rename, GetDataPosition getTablePos, FXPlatformRunnable onModify) throws UserException, InternalException
    {
        return new ColumnDetails(column.getName(), column.getType(), rename, makeField(columnIndex, column.getType(), column.isEditable(), getTablePos, onModify)) {
            @Override
            protected @OnThread(Tag.FXPlatform) ImmutableList<Node> makeHeaderContent()
            {
                Label typeLabel = null;
                try
                {
                    typeLabel = GUI.labelRaw(column.getType().toDisplay(false), "stable-view-column-type");
                }
                catch (UserException | InternalException e)
                {
                    Log.log(e);
                    typeLabel = GUI.label("column.type.unknown", "stable-view-column-type-unknown");
                }
                // Wrap typeLabel in a BorderPane so that it can centre-align:
                return Utility.concatI(super.makeHeaderContent(), ImmutableList.of(new BorderPane(typeLabel)));
            }
        };
        /*column.getType().<ColumnHandler, UserException>applyGet(new DataTypeVisitorGetEx<ColumnHandler, UserException>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return NumberColumnFormatter.makeDisplayCache(g, displayInfo.getDisplayInfo(), column);
            }

            @Override
            public ColumnHandler text(GetValue<@Value String> g) throws InternalException, UserException
            {
                class StringDisplay extends StackPane
                {
                    private final StyleClassedTextArea textArea;
                    private final BooleanBinding notFocused;
                    private @Nullable FXPlatformRunnable endEdit;

                    @OnThread(Tag.FXPlatform)
                    public StringDisplay(int rowIndex, String originalValue)
                    {
                        Label beginQuote = new Label("\u201C");
                        Label endQuote = new Label("\u201D");
                        beginQuote.getStyleClass().add("string-display-quote");
                        endQuote.getStyleClass().add("string-display-quote");
                        StackPane.setAlignment(beginQuote, Pos.TOP_LEFT);
                        StackPane.setAlignment(endQuote, Pos.TOP_RIGHT);
                        //StackPane.setMargin(beginQuote, new Insets(0, 0, 0, 3));
                        //StackPane.setMargin(endQuote, new Insets(0, 3, 0, 0));
                        textArea = new StyleClassedTextArea(true);
                        textArea.getStyleClass().add("string-display");
                        textArea.replaceText(originalValue);
                        textArea.setWrapText(false);
                        getChildren().addAll(beginQuote, textArea); //endQuote, textArea);
                        // TODO allow editing, and call column.modified when it happens
                        Nodes.addInputMap(textArea, InputMap.consume(EventPattern.keyPressed(KeyCode.ENTER), e -> {
                            if (endEdit != null)
                                endEdit.run();
                            e.consume();
                        }));
                        FXUtility.addChangeListenerPlatformNN(textArea.focusedProperty(), focused ->
                        {
                            if (!focused)
                            {
                                String value = textArea.getText();
                                Workers.onWorkerThread("Storing value " + value, Workers.Priority.SAVE, () -> Utility.alertOnError_(() -> {
                                        g.set(rowIndex, DataTypeUtility.value(value));
                                        column.modified(rowIndex);
                                }));
                                textArea.deselect();
                            }
                        });
                        // Doesn't get mouse events unless focused:
                        notFocused = textArea.focusedProperty().not();
                        textArea.mouseTransparentProperty().bind(notFocused);
                    }

                    public void edit(boolean selectAll, FXPlatformRunnable endEdit)
                    {
                        textArea.requestFocus();
                        if (selectAll)
                            textArea.selectAll();
                        this.endEdit = endEdit;
                    }
                }

                return new EditorKitCache<@Value String, StringDisplay>(g, null, s -> s) {
                    @Override
                    protected StringDisplay makeGraphical(int rowIndex, @Value String value)
                    {
                        return new StringDisplay(rowIndex, value);
                    }

                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable endEdit)
                    {
                        @Nullable StringDisplay display = getRowIfShowing(rowIndex);
                        if (display != null)
                        {
                            display.edit(scenePoint == null, endEdit);
                        }
                    }

                    @Override
                    public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
                    {
                        return null; // TODO allow typing to start editing
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return true;
                    }

                    @Override
                    public boolean editHasFocus(int rowIndex)
                    {
                        @Nullable StringDisplay display = getRowIfShowing(rowIndex);
                        if (display != null)
                            return display.isFocused();
                        return false;
                    }
                };
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler tagged(TypeId typeName, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler tuple(ImmutableList<DataTypeValue> types) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            public ColumnHandler array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }
        }));
        */
    }

    // package-visible
    @SafeVarargs
    static StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> docFromSegments(StyledText<Collection<String>>... segments)
    {
        ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment((StyledText<Collection<String>>)segments[0], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps());
        for (int i = 1; i < segments.length; i++)
        {
            doc = doc.concat(ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment(segments[i], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps()));
        }
        return doc;
    }

    @SuppressWarnings("units")
    @OnThread(Tag.Any)
    public static @TableDataColIndex int displayCol(int col)
    {
        return col;
    }

    public static interface ComponentMaker<T>
    {
        @OnThread(Tag.FXPlatform)
        public Component<T> makeComponent(ImmutableList<Component<?>> parents, T value) throws InternalException, UserException;
    }

    /**
     * Interface for getting position of cell on grid, and getting visible bounds of table
     */
    public interface GetDataPosition
    {
        @OnThread(Tag.FXPlatform)
        public CellPosition getDataPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex);
        
        @OnThread(Tag.FXPlatform)
        public @TableDataRowIndex int getFirstVisibleRowIncl();

        @OnThread(Tag.FXPlatform)
        public @TableDataRowIndex int getLastVisibleRowIncl();
    }
    
    public static class GetValueAndComponent<@Value T>
    {
        public final GetValue<@Value T> g;
        public final Recogniser<@Value T> recogniser;
        private final @Nullable FXPlatformConsumer<EditorKitCache<@Value T>.VisibleDetails> formatter;

        @OnThread(Tag.Any)
        public GetValueAndComponent(GetValue<@Value T> g, Recogniser<@Value T> recogniser)
        {
            this(g, recogniser, null);
        }

        @OnThread(Tag.Any)
        public GetValueAndComponent(GetValue<@Value T> g, Recogniser<@Value T> recogniser, @Nullable FXPlatformConsumer<EditorKitCache<@Value T>.VisibleDetails> formatter)
        {
            this.g = g;
            this.recogniser = recogniser;
            this.formatter = formatter;
        }
        
        @OnThread(Tag.Any)
        public EditorKitCache<@Value T> makeDisplayCache(@TableDataColIndex int columnIndex, boolean isEditable, ImmutableList<String> stfStyles, GetDataPosition getDataPosition, FXPlatformRunnable onModify)
        {
            MakeEditorKit<@Value T> makeEditorKit = (rowIndex, value, relinquishFocus) -> {
                FXPlatformConsumer<Pair<String, @Value T>> saveChange = isEditable ? new FXPlatformConsumer<Pair<String, @Value T>>()
                {
                    @Override
                    public @OnThread(Tag.FXPlatform) void consume(Pair<String, @Value T> p)
                    {
                        Workers.onWorkerThread("Saving", Priority.SAVE, () -> FXUtility.alertOnError_("Error storing data value", () -> g.set(rowIndex, p.getSecond())));
                        onModify.run();
                    }
                } : null;
                FXPlatformRunnable relinquishFocusRunnable = () -> relinquishFocus.consume(getDataPosition.getDataPosition(rowIndex, columnIndex));
                @SuppressWarnings("nullness") // TODO
                EditorKit<@Value T> editorKit = new EditorKit<@Value T>(recogniser); //, saveChange, relinquishFocusRunnable, stfStyles));
                return editorKit;
            };
            return new EditorKitCache<@Value T>(columnIndex, g, formatter != null ? formatter : vis -> {}, getDataPosition, makeEditorKit);
        }
    }

    // public for testing:
    @OnThread(Tag.Any)
    public static EditorKitCache<?> makeField(@TableDataColIndex int columnIndex, DataTypeValue dataTypeValue, boolean isEditable, GetDataPosition getTablePos, FXPlatformRunnable onModify) throws InternalException
    {
        return valueAndComponent(dataTypeValue).makeDisplayCache(columnIndex, isEditable, stfStylesFor(dataTypeValue), getTablePos, onModify);
    }

    @OnThread(Tag.Any)
    public static ImmutableList<String> stfStylesFor(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<ImmutableList<String>, InternalException>()
        {
            @Override
            public ImmutableList<String> number(NumberInfo numberInfo) throws InternalException
            {
                return ImmutableList.of("stf-cell-number");
            }

            @Override
            public ImmutableList<String> text() throws InternalException
            {
                return ImmutableList.of("stf-cell-text");
            }

            @Override
            public ImmutableList<String> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return ImmutableList.of("stf-cell-datetime");
            }

            @Override
            public ImmutableList<String> bool() throws InternalException
            {
                return ImmutableList.of("stf-cell-bool");
            }

            @Override
            public ImmutableList<String> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return ImmutableList.of("stf-cell-tagged");
            }

            @Override
            public ImmutableList<String> tuple(ImmutableList<DataType> inner) throws InternalException
            {
                return ImmutableList.of("stf-cell-tuple");
            }

            @Override
            public ImmutableList<String> array(@Nullable DataType inner) throws InternalException
            {
                return ImmutableList.of("stf-cell-array");
            }
        });
    }

    @OnThread(Tag.Any)
    private static GetValueAndComponent<?> valueAndComponent(DataTypeValue dataTypeValue) throws InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGetEx<GetValueAndComponent<?>, InternalException>()
        {
            @Override
            public GetValueAndComponent<?> number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException
            {
                return new GetValueAndComponent<@Value Number>(g, dummy(), new NumberColumnFormatter());
            }

            @Override
            public GetValueAndComponent<?> text(GetValue<@Value String> g) throws InternalException
            {
                return new GetValueAndComponent<@Value String>(g, new StringRecogniser());
            }

            @Override
            public GetValueAndComponent<?> bool(GetValue<@Value Boolean> g) throws InternalException
            {
                return new GetValueAndComponent<@Value Boolean>(g, new BooleanRecogniser());
            }

            @Override
            public GetValueAndComponent<?> date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy());
                    case YEARMONTH:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy());
                    case TIMEOFDAY:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy());
                    /*
                    case TIMEOFDAYZONED:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, (parents, value) -> new Component2<@Value TemporalAccessor, @Value TemporalAccessor, @Value ZoneOffset>(parents, subParents -> new TimeComponent(subParents, value), null, subParents -> new PlusMinusOffsetComponent(parents, value.get(ChronoField.OFFSET_SECONDS)), (a, b) -> OffsetTime.of((LocalTime)a, b)));
                    */
                    case DATETIME:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy());
                            //(parents, value) -> new Component2<@Value TemporalAccessor/*LocalDateTime*/, @Value TemporalAccessor /*LocalDate*/, @Value TemporalAccessor /*LocalTime*/>(parents, subParents -> new YMD(subParents, value), " ", subParents -> new TimeComponent(subParents, value), (a, b) -> LocalDateTime.of((LocalDate)a, (LocalTime)b)));
                    case DATETIMEZONED:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy()); 
                            //(parents0, value) ->
                            //new Component2<@Value TemporalAccessor /*ZonedDateTime*/, @Value TemporalAccessor /*LocalDateTime*/, @Value ZoneId>(parents0,
//                                parents1 -> new Component2<@Value TemporalAccessor /*LocalDateTime*/, @Value TemporalAccessor /*LocalDate*/, @Value TemporalAccessor /*LocalTime*/>(
//                                        parents1, parents2 -> new YMD(parents2, value), " ", parents2 -> new TimeComponent(parents2, value), (a, b) -> LocalDateTime.of((LocalDate)a, (LocalTime)b)),
//                                " ",
//                                parents1 -> new ZoneIdComponent(parents1, ((ZonedDateTime)value).getZone()), (a, b) -> ZonedDateTime.of((LocalDateTime)a, b))
//                        );
                }
                throw new InternalException("Unknown type: " + dateTimeInfo.getType());
            }

            @Override
            public GetValueAndComponent<?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException
            {
                GetValue<TaggedValue> getTagged = DataTypeUtility.toTagged(g, tagTypes);
                return new GetValueAndComponent<TaggedValue>(getTagged, dummy()); 
                    //(parents, v) -> (Component<@Value TaggedValue>)new TaggedComponent(parents, tagTypes, v));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<?> tuple(ImmutableList<DataTypeValue> types) throws InternalException
            {
                List<GetValueAndComponent<?>> gvacs = new ArrayList<>(types.size());
                for (DataTypeValue type : types)
                {
                    gvacs.add(valueAndComponent(type));
                }
                GetValue<@Value Object @Value[]> tupleGet = new GetValue<@Value Object @Value[]>()
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public @Value Object @Value @NonNull [] getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        @Value Object[] r = new @Value Object[types.size()];
                        for (int i = 0; i < r.length; i++)
                        {
                            r[i] = gvacs.get(i).g.getWithProgress(index, progressListener);
                        }
                        return DataTypeUtility.value(r);
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    @SuppressWarnings("unchecked")
                    public void set(int index, @Value Object @Value[] value) throws InternalException, UserException
                    {
                        for (int i = 0; i < value.length; i++)
                        {
                            // Cast because we can't express type safety:
                            ((GetValue)gvacs.get(i).g).set(index, value[i]);
                        }
                    }
                };


                return new GetValueAndComponent<@Value Object @Value[]>(tupleGet, new TupleRecogniser(Utility.<GetValueAndComponent<?>, Recogniser<@Value ?>>mapListI(gvacs, gvac -> gvac.recogniser)));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<?> array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException
            {
                if (inner == null)
                    throw new InternalException("Can't make components for the empty list type");

                @NonNull DataType innerType = inner;
                
                return new GetValueAndComponent<@Value ListEx>(DataTypeUtility.toListEx(innerType, g), dummy());
                    /*
                (parents, value) ->
                {
                    List<@Value Object> fxList = DataTypeUtility.fetchList(value);

                    // Have to use some casts because we can't express the type safety:
                    @SuppressWarnings("unchecked")
                    ComponentMaker<@Value Object> makeComponent = (ComponentMaker)valueAndComponent(innerType.fromCollapsed((index, prog) -> DataTypeUtility.value(0))).makeComponent;
                    List<FXPlatformFunctionIntUser<ImmutableList<Component<?>>, Component<? extends @NonNull @Value Object>>> components = new ArrayList<>(fxList.size());
                    for (int i = 0; i < fxList.size(); i++)
                    {
                        int iFinal = i;
                        components.add(subParents -> makeComponent.makeComponent(subParents, fxList.get(iFinal)));
                    }

                    return new VariableLengthComponentList<@Value ListEx, @Value Object>(parents, "[", ",", components, "]", ListExList::new)
                    {
                        @Override
                        protected Component<? extends @Value Object> makeNewEntry(ImmutableList<Component<?>> subParents) throws InternalException
                        {
                            return component(subParents, innerType, null);
                        }
                    };
                });
                */
            }
        });
    }

    
    private static interface EditorKitMaker<V>
    {
        @OnThread(Tag.FXPlatform)
        public EditorKit<V> make(V value, FXPlatformConsumer<V> storeValue, FXPlatformConsumer<Boolean> onFocusChange, FXPlatformRunnable relinquishFocus) throws InternalException, UserException;
    }

    /**
     * A helper implementation of EditorKitCache that uses StructuredTextField as its graphical item.
     * @param <V>
     */
    /*
    public static class DisplayCacheSTF<@Value V> extends EditorKitCache<V, StructuredTextField>
    {
        private final EditorKitMaker<V> makeField;

        public DisplayCacheSTF(GetValue<V> g, EditorKitMaker<V> makeField)
        {
            super(g, cs -> {}, f -> f);
            this.makeField = makeField;
        }

        @Override
        public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
        {
            return null;
        }

        @Override
        public void edit(int rowIndex, @Nullable Point2D scenePoint)
        {
            @Nullable StructuredTextField display = getRowIfShowing(rowIndex);
            if (display != null)
            {
                @Value V startValue = display.getCompletedValue();
                display.edit(scenePoint);
            }
        }

        @Override
        public boolean isEditable()
        {
            return true;
        }

        @Override
        public boolean editHasFocus(int rowIndex)
        {
            @Nullable StructuredTextField display = getRowIfShowing(rowIndex);
            if (display != null)
                return display.isFocused();
            return false;
        }

        @Override
        protected StructuredTextField makeGraphical(int rowIndex, V value, FXPlatformConsumer<Boolean> onFocusChange, FXPlatformRunnable relinquishFocus) throws InternalException, UserException
        {
            StructuredTextField field = makeField.make(value, v -> {
                Workers.onWorkerThread("Saving " + v, Priority.SAVE, () ->
                {
                    Utility.alertOnError_(() -> store(rowIndex, v));
                });
            }, onFocusChange, relinquishFocus);
            //field.mouseTransparentProperty().bind(field.focusedProperty().not());
            return field;
        }
    }
    */
    
    
    public static Recogniser<@Value ?> recogniser(DataType dataType) throws InternalException
    {   
        return dataType.apply(new DataTypeVisitorEx<Recogniser<@Value ?>, InternalException>()
        {
            @Override
            public Recogniser<@Value ?> number(NumberInfo numberInfo) throws InternalException
            {
                return dummy();
            }

            @Override
            public Recogniser<@Value ?> text() throws InternalException
            {
                return new StringRecogniser();
            }

            @Override
            public Recogniser<@Value ?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return dummy();
            }

            @Override
            public Recogniser<@Value ?> bool() throws InternalException
            {
                return new BooleanRecogniser();
            }

            @Override
            public Recogniser<@Value ?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return dummy();
            }

            @Override
            public Recogniser<@Value ?> tuple(ImmutableList<DataType> inner) throws InternalException
            {
                return new TupleRecogniser(Utility.<DataType, Recogniser<@Value ?>>mapListInt(inner, t -> recogniser(t)));
            }

            @Override
            public Recogniser<@Value ?> array(DataType inner) throws InternalException
            {
                return dummy();
            }
        });
    }

    private static <T> Recogniser<@Value T> dummy()
    {
        return new Recogniser<@Value T>()
        {
            @Override
            public Either<ErrorDetails, SuccessDetails<@Value T>> process(ParseProgress parseProgress)
            {
                return error("Unimplemented");
            }
        };
    }
}
