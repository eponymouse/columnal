package records.gui.dtf;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.Column;
import records.data.Column.EditableStatus;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table.Display;
import records.data.Transformation.SilentCancelEditException;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGetEx;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.dtf.RecogniserDocument.Saver;
import records.gui.dtf.recognisers.BooleanRecogniser;
import records.gui.dtf.recognisers.ListRecogniser;
import records.gui.dtf.recognisers.NumberRecogniser;
import records.gui.dtf.recognisers.RecordRecogniser;
import records.gui.dtf.recognisers.StringRecogniser;
import records.gui.dtf.recognisers.TaggedRecogniser;
import records.gui.dtf.recognisers.TemporalRecogniser;
import records.gui.stable.ColumnDetails;
import records.gui.stable.ColumnHandler;
import records.gui.stable.EditorKitCache;
import records.gui.stable.EditorKitCache.MakeEditorKit;
import records.gui.stable.EditorKitCallback;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.SimulationFunctionInt;
import utility.SimulationSupplierInt;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.Record;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by neil on 01/05/2017.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplayUtility
{

    @OnThread(Tag.FXPlatform)
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
                        public void fetchValue(@TableDataRowIndex int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformBiConsumer<KeyCode, CellPosition> relinquishFocus, EditorKitCallback setCellContent)
                        {
                            setCellContent.loadedValue(rowIndex, columnIndexFinal, new ReadOnlyDocument("Error: " + e.getLocalizedMessage(), true));
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
                        public void styleTogether(Collection<? extends DocumentTextField> cellsInColumn, double columnSize)
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

    @OnThread(Tag.FXPlatform)
    private static ColumnDetails getDisplay(@TableDataColIndex int columnIndex, @NonNull Column column, @Nullable FXPlatformConsumer<ColumnId> rename, GetDataPosition getTablePos, FXPlatformRunnable onModify) throws UserException, InternalException
    {
        return new ColumnDetails(column.getName(), column.getType().getType(), rename, makeField(columnIndex, column.getType(), column.getEditableStatus(), getTablePos, onModify)) {
            @Override
            protected @OnThread(Tag.FXPlatform) ImmutableList<Node> makeHeaderContent()
            {
                Label typeLabel = null;
                try
                {
                    typeLabel = GUI.labelRaw(column.getType().getType().toDisplay(false), "stable-view-column-type");
                }
                catch (UserException | InternalException e)
                {
                    Log.log(e);
                    typeLabel = GUI.label("column.type.unknown", "stable-view-column-type-unknown");
                }
                // Wrap typeLabel in a BorderPane so that it can centre-align:
                return Utility.concatI(super.makeHeaderContent(), ImmutableList.<BorderPane>of(new BorderPane(typeLabel)));
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

    @SuppressWarnings("units")
    @OnThread(Tag.Any)
    public static @TableDataColIndex int displayCol(int col)
    {
        return col;
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
        private final DataType dataType;
        private final Class<@UnknownIfValue T> itemClass;
        public final GetValue<@Value T> g;
        public final Recogniser<@Value T> recogniser;
        private final @Nullable FXPlatformConsumer<EditorKitCache<@Value T>.VisibleDetails> formatter;

        @OnThread(Tag.Any)
        public GetValueAndComponent(DataType dataType, Class<@UnknownIfValue T> itemClass, GetValue<@Value T> g, Recogniser<@Value T> recogniser)
        {
            this(dataType, itemClass, g, recogniser, null);
        }

        @OnThread(Tag.Any)
        public GetValueAndComponent(DataType dataType, Class<@UnknownIfValue T> itemClass, GetValue<@Value T> g, Recogniser<@Value T> recogniser, @Nullable FXPlatformConsumer<EditorKitCache<@Value T>.VisibleDetails> formatter)
        {
            this.dataType = dataType;
            this.itemClass = itemClass;
            this.g = g;
            this.recogniser = recogniser;
            this.formatter = formatter;
        }
        
        @OnThread(Tag.Any)
        public EditorKitCache<@Value T> makeDisplayCache(@TableDataColIndex int columnIndex, EditableStatus editableStatus, ImmutableList<String> stfStyles, GetDataPosition getDataPosition, FXPlatformRunnable onModify)
        {
            MakeEditorKit<@Value T> makeEditorKit = (@TableDataRowIndex int rowIndex, Pair<String, @Nullable T> value, FXPlatformBiConsumer<KeyCode, CellPosition> relinquishFocus) -> {
                Saver<@Value T> saveChange = (String s, @Nullable @Value T v, FXPlatformRunnable reset) -> {};
                if (editableStatus.editable)
                    saveChange = new Saver<@Value T>()
                    {
                        @Override
                        public @OnThread(Tag.FXPlatform) void save(String text, @Nullable @Value T v, FXPlatformRunnable reset)
                        {
                            Workers.onWorkerThread("Saving value: " + text, Priority.SAVE, () -> FXUtility.alertOnError_("Error storing data value", () -> {
                                try
                                {
                                    g.set(rowIndex, v == null ? Either.left(text) : Either.right(v));
                                }
                                catch (SilentCancelEditException e)
                                {
                                    FXUtility.runFX(reset);
                                }
                            }));
                            onModify.run();
                        }
                    };
                FXPlatformConsumer<KeyCode> relinquishFocusRunnable = keyCode -> relinquishFocus.consume(keyCode, getDataPosition.getDataPosition(rowIndex, columnIndex));
                Document editorKit;
                if (editableStatus.editable)
                {
                    SimulationSupplierInt<Boolean> checkEditable = makeCheckRow(editableStatus, rowIndex);

                    editorKit = new RecogniserDocument<@Value T>(value.getFirst(), (Class<@Value T>) itemClass, recogniser, checkEditable, saveChange, relinquishFocusRunnable); // stfStyles));
                }
                else
                {
                    editorKit = new ReadOnlyDocument(value.getFirst());
                }
                return editorKit;
            };
            return new EditorKitCache<@Value T>(columnIndex, dataType, g, formatter != null ? formatter : vis -> {}, getDataPosition, makeEditorKit);
        }

        private @Nullable SimulationSupplierInt<Boolean> makeCheckRow(EditableStatus editableStatus, @TableDataRowIndex int rowIndex)
        {
            SimulationFunctionInt<@TableDataRowIndex Integer, Boolean> checkRowEditable = editableStatus.checkEditable;
            return checkRowEditable == null ? null : new SimulationSupplierInt<Boolean>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public Boolean get() throws InternalException
                {
                    return checkRowEditable.apply(rowIndex);
                }
            };
        }
    }

    // public for testing:
    @OnThread(Tag.FXPlatform)
    public static EditorKitCache<?> makeField(@TableDataColIndex int columnIndex, DataTypeValue dataTypeValue, EditableStatus editableStatus, GetDataPosition getTablePos, FXPlatformRunnable onModify) throws InternalException
    {
        return valueAndComponent(dataTypeValue).makeDisplayCache(columnIndex, editableStatus, stfStylesFor(dataTypeValue.getType()), getTablePos, onModify);
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
            public ImmutableList<String> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return ImmutableList.of("stf-cell-record");
            }

            @Override
            public ImmutableList<String> array(@Nullable DataType inner) throws InternalException
            {
                return ImmutableList.of("stf-cell-array");
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    private static GetValueAndComponent<?> valueAndComponent(DataTypeValue dataTypeValue) throws InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGetEx<GetValueAndComponent<?>, InternalException>()
        {
            @Override
            public GetValueAndComponent<?> number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException
            {
                return new GetValueAndComponent<@Value Number>(dataTypeValue.getType(), Number.class, g, new NumberRecogniser(), new NumberColumnFormatter());
            }

            @Override
            public GetValueAndComponent<?> text(GetValue<@Value String> g) throws InternalException
            {
                return new GetValueAndComponent<@Value String>(dataTypeValue.getType(), String.class, g, new StringRecogniser());
            }

            @Override
            public GetValueAndComponent<?> bool(GetValue<@Value Boolean> g) throws InternalException
            {
                return new GetValueAndComponent<@Value Boolean>(dataTypeValue.getType(), Boolean.class, g, new BooleanRecogniser());
            }

            @Override
            public GetValueAndComponent<?> date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
            {
                return new GetValueAndComponent<@Value TemporalAccessor>(dataTypeValue.getType(), TemporalAccessor.class, g, new TemporalRecogniser(dateTimeInfo.getType()));
                
                //switch (dateTimeInfo.getType())
                //{
                //    case YEARMONTHDAY:
                //        return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy());
                //    case YEARMONTH:
                //        return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy());
                //    case TIMEOFDAY:
                //        return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy());
                //    case TIMEOFDAYZONED:
                //        return new GetValueAndComponent<@Value TemporalAccessor>(g, (parents, value) -> new Component2<@Value TemporalAccessor, @Value TemporalAccessor, @Value ZoneOffset>(parents, subParents -> new TimeComponent(subParents, value), null, subParents -> new PlusMinusOffsetComponent(parents, value.get(ChronoField.OFFSET_SECONDS)), (a, b) -> OffsetTime.of((LocalTime)a, b)));
                 //   case DATETIME:
                 //       return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy());
                            //(parents, value) -> new Component2<@Value TemporalAccessor/*LocalDateTime*/, @Value TemporalAccessor /*LocalDate*/, @Value TemporalAccessor /*LocalTime*/>(parents, subParents -> new YMD(subParents, value), " ", subParents -> new TimeComponent(subParents, value), (a, b) -> LocalDateTime.of((LocalDate)a, (LocalTime)b)));
                 //   case DATETIMEZONED:
                 //       return new GetValueAndComponent<@Value TemporalAccessor>(g, dummy()); 
                            //(parents0, value) ->
                            //new Component2<@Value TemporalAccessor /*ZonedDateTime*/, @Value TemporalAccessor /*LocalDateTime*/, @Value ZoneId>(parents0,
//                                parents1 -> new Component2<@Value TemporalAccessor /*LocalDateTime*/, @Value TemporalAccessor /*LocalDate*/, @Value TemporalAccessor /*LocalTime*/>(
//                                        parents1, parents2 -> new YMD(parents2, value), " ", parents2 -> new TimeComponent(parents2, value), (a, b) -> LocalDateTime.of((LocalDate)a, (LocalTime)b)),
//                                " ",
//                                parents1 -> new ZoneIdComponent(parents1, ((ZonedDateTime)value).getZone()), (a, b) -> ZonedDateTime.of((LocalDateTime)a, b))
//                        );
                //}
                //throw new InternalException("Unknown type: " + dateTimeInfo.getType());
            }

            @Override
            public GetValueAndComponent<?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<TaggedValue> getTagged) throws InternalException
            {
                return new GetValueAndComponent<TaggedValue>(dataTypeValue.getType(), TaggedValue.class, getTagged, new TaggedRecogniser(Utility.<TagType<DataType>, TagType<Recogniser<@Value ?>>>mapListInt(tagTypes, tt -> tt.<Recogniser<@Value ?>>mapInt(t -> recogniser(t).recogniser)))); 
                    //(parents, v) -> (Component<@Value TaggedValue>)new TaggedComponent(parents, tagTypes, v));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<?> record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, InternalException
            {
                ImmutableMap<@ExpressionIdentifier String, Recogniser<@Value ?>> recognisers = Utility.<@ExpressionIdentifier String, DataType, Recogniser<@Value ?>>mapValuesInt(types, t -> recogniser(t).recogniser);

                return new GetValueAndComponent<@Value Record>(dataTypeValue.getType(), (Class<@Value Record>)Record.class, g, new RecordRecogniser(recognisers));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<?> array(DataType inner, GetValue<@Value ListEx> g) throws InternalException
            {
                return new GetValueAndComponent<@Value ListEx>(dataTypeValue.getType(), ListEx.class, g, new ListRecogniser(recogniser(inner).recogniser));
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
    
    public static class RecogniserAndType<T>
    {
        public final Recogniser<T> recogniser;
        public final Class<T> itemClass;

        RecogniserAndType(Recogniser<T> recogniser, Class<T> itemClass)
        {
            this.recogniser = recogniser;
            this.itemClass = itemClass;
        }
    }
    
    
    public static RecogniserAndType<@NonNull @Value ?> recogniser(DataType dataType) throws InternalException
    {   
        return dataType.apply(new DataTypeVisitorEx<RecogniserAndType<@NonNull @Value ?>, InternalException>()
        {
            private <T> RecogniserAndType<@NonNull @Value T> r(Recogniser<@NonNull @Value T> recogniser, Class<@UnknownIfValue T> itemClass)
            {
                return new RecogniserAndType<@NonNull @Value T>(recogniser, (Class<@NonNull @Value T>)itemClass);
            }
            
            @Override
            public RecogniserAndType<@NonNull @Value ?> number(NumberInfo numberInfo) throws InternalException
            {
                return r(new NumberRecogniser(), Number.class);
            }

            @Override
            public RecogniserAndType<@NonNull @Value ?> text() throws InternalException
            {
                return r(new StringRecogniser(), String.class);
            }

            @Override
            public RecogniserAndType<@NonNull @Value ?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return r(new TemporalRecogniser(dateTimeInfo.getType()), TemporalAccessor.class);
            }

            @Override
            public RecogniserAndType<@NonNull @Value ?> bool() throws InternalException
            {
                return r(new BooleanRecogniser(), Boolean.class);
            }

            @Override
            public RecogniserAndType<@NonNull @Value ?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return r(new TaggedRecogniser(Utility.<TagType<DataType>, TagType<Recogniser<@Value ?>>>mapListInt(tags, tt -> tt.<Recogniser<@Value ?>>mapInt(t -> recogniser(t).recogniser))), TaggedValue.class);
            }

            @Override
            public RecogniserAndType<@NonNull @Value ?> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return r(new RecordRecogniser(Utility.<@ExpressionIdentifier String, DataType, Recogniser<@Value ?>>mapValuesInt(fields, t -> recogniser(t).recogniser)), (Class<@Value Record>)Record.class);
            }

            @Override
            public RecogniserAndType<@NonNull @Value ?> array(DataType inner) throws InternalException
            {
                return r(new ListRecogniser(recogniser(inner).recogniser), ListEx.class);
            }
        });
    }
}
