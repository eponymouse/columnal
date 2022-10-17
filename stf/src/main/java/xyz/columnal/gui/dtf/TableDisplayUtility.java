/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.dtf;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.Column.AlteredState;
import xyz.columnal.data.Column.EditableStatus;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table.Display;
import xyz.columnal.data.Transformation.SilentCancelEditException;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.DataTypeValue.DataTypeVisitorGetEx;
import xyz.columnal.data.datatype.DataTypeValue.GetValue;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.dtf.RecogniserDocument.Saver;
import xyz.columnal.gui.dtf.recognisers.BooleanRecogniser;
import xyz.columnal.gui.dtf.recognisers.ListRecogniser;
import xyz.columnal.gui.dtf.recognisers.NumberRecogniser;
import xyz.columnal.gui.dtf.recognisers.RecordRecogniser;
import xyz.columnal.gui.dtf.recognisers.StringRecogniser;
import xyz.columnal.gui.dtf.recognisers.TaggedRecogniser;
import xyz.columnal.gui.dtf.recognisers.TemporalRecogniser;
import xyz.columnal.gui.stable.ColumnDetails;
import xyz.columnal.gui.stable.ColumnHandler;
import xyz.columnal.gui.stable.EditorKitCache;
import xyz.columnal.gui.stable.EditorKitCache.MakeEditorKit;
import xyz.columnal.gui.stable.EditorKitCallback;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformBiConsumer;
import xyz.columnal.utility.function.fx.FXPlatformBiFunction;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunctionInt;
import xyz.columnal.utility.function.simulation.SimulationSupplierInt;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;

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
    
    public static interface GetAdditionalMenuItems
    {
        @OnThread(Tag.FXPlatform)
        public ImmutableList<MenuItem> getAdditionalMenuItems(boolean focused, ColumnId columnId, @TableDataRowIndex int rowIndex);
    }

    @OnThread(Tag.FXPlatform)
    public static ImmutableList<ColumnDetails> makeStableViewColumns(RecordSet recordSet, Pair<Display, Predicate<ColumnId>> columnSelection, Function<ColumnId, @Nullable FXPlatformConsumer<ColumnId>> renameColumn, GetDataPosition getTablePos, @Nullable FXPlatformRunnable onModify, @Nullable GetAdditionalMenuItems getAdditionalMenuItems)
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
                    item = getDisplay(displayColumnIndex, col, renameColumn.apply(col.getName()), getTablePos, col.getAlteredState() == AlteredState.OVERWRITTEN ? ImmutableList.of("column-title-overwritten") : ImmutableList.of(), onModify != null ? onModify : FXPlatformRunnable.EMPTY, getAdditionalMenuItems);
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
                    }, ImmutableList.of());
                }
                r.add(item);
                displayColumnIndex += displayCol(1);
            }
        }
        return r.build();
    }

    @OnThread(Tag.FXPlatform)
    private static ColumnDetails getDisplay(@TableDataColIndex int columnIndex, @NonNull Column column, @Nullable FXPlatformConsumer<ColumnId> rename, GetDataPosition getTablePos, ImmutableList<String> extraColumnStyles, FXPlatformRunnable onModify, @Nullable GetAdditionalMenuItems getAdditionalMenuItems) throws UserException, InternalException
    {
        return new ColumnDetails(column.getName(), column.getType().getType(), rename, makeField(columnIndex, column.getType(), column.getEditableStatus(), getTablePos, onModify, getAdditionalMenuItems == null ? null : (FXPlatformBiFunction<@TableDataRowIndex Integer, Boolean, ImmutableList<MenuItem>>)(@TableDataRowIndex Integer r, Boolean f) -> getAdditionalMenuItems.getAdditionalMenuItems(f, column.getName(), r)), extraColumnStyles);
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
    
    public static class GetValueAndComponent<T extends @NonNull Object>
    {
        private final DataType dataType;
        private final Class<@UnknownIfValue T> itemClass;
        public final GetValue<@Value T> g;
        public final Recogniser<@ImmediateValue T> recogniser;
        private final @Nullable FXPlatformConsumer<EditorKitCache<@ImmediateValue T>.VisibleDetails> formatter;

        @OnThread(Tag.Any)
        public GetValueAndComponent(DataType dataType, Class<@UnknownIfValue T> itemClass, GetValue<@Value T> g, Recogniser<@ImmediateValue T> recogniser)
        {
            this(dataType, itemClass, g, recogniser, null);
        }

        @OnThread(Tag.Any)
        public GetValueAndComponent(DataType dataType, Class<@UnknownIfValue T> itemClass, GetValue<@Value T> g, Recogniser<@ImmediateValue T> recogniser, @Nullable FXPlatformConsumer<EditorKitCache<@ImmediateValue T>.VisibleDetails> formatter)
        {
            this.dataType = dataType;
            this.itemClass = itemClass;
            this.g = g;
            this.recogniser = recogniser;
            this.formatter = formatter;
        }
        
        @OnThread(Tag.Any)
        public EditorKitCache<@Value T> makeDisplayCache(@TableDataColIndex int columnIndex, EditableStatus editableStatus, ImmutableList<String> stfStyles, GetDataPosition getDataPosition, FXPlatformRunnable onModify, @Nullable FXPlatformBiFunction<@TableDataRowIndex Integer, Boolean, ImmutableList<MenuItem>> getAdditionalMenuItems)
        {
            MakeEditorKit<@Value T> makeEditorKit = (@TableDataRowIndex int rowIndex, Pair<String, @Nullable @Value T> value, FXPlatformBiConsumer<KeyCode, CellPosition> relinquishFocus) -> {
                Saver<@Value T> saveChange = (String s, @Nullable @Value T v, FXPlatformRunnable reset) -> {};
                if (editableStatus.editable)
                    saveChange = new Saver<@Value T>()
                    {
                        @Override
                        public @OnThread(Tag.FXPlatform) void save(String text, @Nullable @Value T v, FXPlatformRunnable reset)
                        {
                            Workers.onWorkerThread("Saving value: " + text, Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.storing.data"), () -> {
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

                    editorKit = new RecogniserDocument<@Value T>(value.getFirst(), (Class<@Value T>) itemClass, (Recogniser<@Value T>)recogniser, checkEditable, saveChange, relinquishFocusRunnable, getAdditionalMenuItems == null ? null : focused -> getAdditionalMenuItems.apply(rowIndex, focused)); // stfStyles));
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

    @OnThread(Tag.FXPlatform)
    private static EditorKitCache<?> makeField(@TableDataColIndex int columnIndex, DataTypeValue dataTypeValue, EditableStatus editableStatus, GetDataPosition getTablePos, FXPlatformRunnable onModify, @Nullable FXPlatformBiFunction<@TableDataRowIndex Integer, Boolean, ImmutableList<MenuItem>> getAdditionalMenuItems) throws InternalException
    {
        return valueAndComponent(dataTypeValue, true).makeDisplayCache(columnIndex, editableStatus, stfStylesFor(dataTypeValue.getType()), getTablePos, onModify, getAdditionalMenuItems);
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
    private static GetValueAndComponent<? extends @NonNull Object> valueAndComponent(DataTypeValue dataTypeValue, boolean topLevel) throws InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGetEx<GetValueAndComponent<? extends @NonNull Object>, InternalException>()
        {
            @Override
            public GetValueAndComponent<? extends @NonNull Object> number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException
            {
                return new GetValueAndComponent<@ImmediateValue Number>(dataTypeValue.getType(), Number.class, g, new NumberRecogniser(), new NumberColumnFormatter());
            }

            @Override
            public GetValueAndComponent<? extends @NonNull Object> text(GetValue<@Value String> g) throws InternalException
            {
                return new GetValueAndComponent<@ImmediateValue String>(dataTypeValue.getType(), String.class, g, new StringRecogniser(topLevel));
            }

            @Override
            public GetValueAndComponent<? extends @NonNull Object> bool(GetValue<@Value Boolean> g) throws InternalException
            {
                return new GetValueAndComponent<@Value Boolean>(dataTypeValue.getType(), Boolean.class, g, new BooleanRecogniser());
            }

            @Override
            public GetValueAndComponent<? extends @NonNull Object> date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
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
            public GetValueAndComponent<? extends @NonNull Object> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<TaggedValue> getTagged) throws InternalException
            {
                return new GetValueAndComponent<TaggedValue>(dataTypeValue.getType(), TaggedValue.class, getTagged, new TaggedRecogniser(Utility.<TagType<DataType>, TagType<Recogniser<? extends @NonNull @ImmediateValue Object>>>mapListInt(tagTypes, tt -> tt.<Recogniser<? extends @NonNull @ImmediateValue Object>>mapInt(t -> recogniser(t, false).recogniser))));
                    //(parents, v) -> (Component<@Value TaggedValue>)new TaggedComponent(parents, tagTypes, v));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<? extends @NonNull Object> record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, InternalException
            {
                ImmutableMap<@ExpressionIdentifier String, Recogniser<? extends @NonNull @ImmediateValue Object>> recognisers = Utility.<@ExpressionIdentifier String, DataType, Recogniser<? extends @NonNull @ImmediateValue Object>>mapValuesInt(types, t -> recogniser(t, false).recogniser);

                return new GetValueAndComponent<@Value Record>(dataTypeValue.getType(), (Class<@Value Record>)Record.class, g, new RecordRecogniser(recognisers));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<? extends @NonNull Object> array(DataType inner, GetValue<@Value ListEx> g) throws InternalException
            {
                return new GetValueAndComponent<@ImmediateValue ListEx>(dataTypeValue.getType(), ListEx.class, g, new ListRecogniser(recogniser(inner, false).recogniser));
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
    
    
    public static RecogniserAndType<? extends @NonNull @ImmediateValue Object> recogniser(DataType dataType, boolean topLevel) throws InternalException
    {   
        return dataType.apply(new DataTypeVisitorEx<RecogniserAndType<? extends @ImmediateValue @NonNull Object>, InternalException>()
        {
            private <T> RecogniserAndType<? extends @ImmediateValue @NonNull Object> r(Recogniser<@NonNull @ImmediateValue T> recogniser, Class<@UnknownIfValue T> itemClass)
            {
                return new RecogniserAndType<@NonNull @ImmediateValue T>(recogniser, (Class<@NonNull @ImmediateValue T>)itemClass);
            }
            
            @Override
            public RecogniserAndType<? extends @NonNull @ImmediateValue Object> number(NumberInfo numberInfo) throws InternalException
            {
                return r(new NumberRecogniser(), Number.class);
            }

            @Override
            public RecogniserAndType<? extends @NonNull @ImmediateValue Object> text() throws InternalException
            {
                return r(new StringRecogniser(topLevel), String.class);
            }

            @Override
            public RecogniserAndType<? extends @NonNull @ImmediateValue Object> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return r(new TemporalRecogniser(dateTimeInfo.getType()), TemporalAccessor.class);
            }

            @Override
            public RecogniserAndType<? extends @NonNull @ImmediateValue Object> bool() throws InternalException
            {
                return r(new BooleanRecogniser(), Boolean.class);
            }

            @Override
            public RecogniserAndType<? extends @NonNull @ImmediateValue Object> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return r(new TaggedRecogniser(Utility.<TagType<DataType>, TagType<Recogniser<? extends @NonNull @ImmediateValue Object>>>mapListInt(tags, tt -> tt.<Recogniser<? extends @NonNull @ImmediateValue Object>>mapInt(t -> recogniser(t, false).recogniser))), TaggedValue.class);
            }

            @Override
            public RecogniserAndType<? extends @NonNull @ImmediateValue Object> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return r(new RecordRecogniser(Utility.<@ExpressionIdentifier String, DataType, Recogniser<? extends @NonNull @ImmediateValue Object>>mapValuesInt(fields, t -> recogniser(t, false).recogniser)), (Class<@ImmediateValue Record>)Record.class);
            }

            @Override
            public RecogniserAndType<? extends @NonNull @ImmediateValue Object> array(DataType inner) throws InternalException
            {
                return r(new ListRecogniser(recogniser(inner, false).recogniser), ListEx.class);
            }
        });
    }
}
