package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import org.fxmisc.wellbehaved.event.InputMap;
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
import records.error.InternalException;
import records.error.UserException;
import records.gui.stable.VirtScrollStrTextGrid.EditorKitCallback;
import records.gui.stf.*;
import records.gui.stf.StructuredTextField.EditorKit;
import records.gui.stf.StructuredTextField.EndEditActions;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;
import records.gui.stable.StableView.ColumnHandler;
import utility.Utility.ListEx;
import utility.Utility.ListExList;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by neil on 01/05/2017.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplayUtility
{

    public static ImmutableList<Pair<String, ColumnHandler>> makeStableViewColumns(RecordSet recordSet, Pair<Display, Predicate<ColumnId>> columnSelection, @Nullable FXPlatformRunnable onModify)
    {
        ImmutableList.Builder<Pair<String, ColumnHandler>> r = ImmutableList.builder();
        for (int columnIndex = 0; columnIndex < recordSet.getColumns().size(); columnIndex++)
        {
            Column col = recordSet.getColumns().get(columnIndex);
            if (col.shouldShow(columnSelection))
            {
                Pair<String, ColumnHandler> item;
                try
                {
                    item = getDisplay(columnIndex, col, onModify != null ? onModify : FXPlatformRunnable.EMPTY);
                }
                catch (InternalException | UserException e)
                {
                    final int columnIndexFinal = columnIndex;
                    // Show a dummy column with an error message:
                    item = new Pair<>(col.getName().getRaw(), new ColumnHandler()
                    {
                        @Override
                        public void fetchValue(int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformRunnable relinquishFocus, EditorKitCallback setCellContent, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl)
                        {
                            setCellContent.loadedValue(columnIndexFinal, rowIndex, new EditorKitSimpleLabel("Error: " + e.getLocalizedMessage()));
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
                    });
                }
                r.add(item);
            }
        }
        return r.build();
    }

    private static Pair<String, ColumnHandler> getDisplay(int columnIndex, @NonNull Column column, FXPlatformRunnable onModify) throws UserException, InternalException
    {
        return new Pair<>(column.getName().getRaw(), makeField(columnIndex, column.getType(), column.isEditable(), onModify));
        /*column.getType().<ColumnHandler, UserException>applyGet(new DataTypeVisitorGetEx<ColumnHandler, UserException>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return NumberDisplay.makeDisplayCache(g, displayInfo.getDisplayInfo(), column);
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
                                Workers.onWorkerThread("Storing value " + value, Workers.Priority.SAVE_ENTRY, () -> Utility.alertOnError_(() -> {
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

    public static interface ComponentMaker<T>
    {
        @OnThread(Tag.FXPlatform)
        public Component<T> makeComponent(ImmutableList<Component<?>> parents, T value) throws InternalException, UserException;
    }

    public static class GetValueAndComponent<T>
    {
        public final GetValue<T> g;
        public final ComponentMaker<T> makeComponent;

        public GetValueAndComponent(GetValue<T> g, ComponentMaker<T> makeComponent)
        {
            this.g = g;
            this.makeComponent = makeComponent;
        }

        public EditorKitCache<T> makeDisplayCache(int columnIndex, boolean isEditable, FXPlatformRunnable onModify)
        {
            return new EditorKitCache<T>(columnIndex, g, vis -> {}, (rowIndex, value) -> {
                return new EditorKit<T>(makeComponent.makeComponent(ImmutableList.of(), value), isEditable ? (Pair<String, T> p) -> {g.set(rowIndex, p.getSecond()); onModify.run();} : null);
            });
        }
    }

    // public for testing:
    @OnThread(Tag.FXPlatform)
    public static EditorKitCache<?> makeField(int columnIndex, DataTypeValue dataTypeValue, boolean isEditable, FXPlatformRunnable onModify) throws InternalException
    {
        return valueAndComponent(dataTypeValue).makeDisplayCache(columnIndex, isEditable, onModify);
    }

    @OnThread(Tag.FXPlatform)
    private static GetValueAndComponent<?> valueAndComponent(DataTypeValue dataTypeValue) throws InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGetEx<GetValueAndComponent<?>, InternalException>()
        {
            @Override
            public GetValueAndComponent<?> number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException
            {
                return new GetValueAndComponent<@Value Number>(g, NumberEntry::new);
            }

            @Override
            public GetValueAndComponent<?> text(GetValue<@Value String> g) throws InternalException
            {
                return new GetValueAndComponent<@Value String>(g, TextEntry::new);
            }

            @Override
            public GetValueAndComponent<?> bool(GetValue<@Value Boolean> g) throws InternalException
            {
                return new GetValueAndComponent<@Value Boolean>(g, BoolComponent::new);
            }

            @Override
            public GetValueAndComponent<?> date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, YMD::new);
                    case YEARMONTH:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, YM::new);
                    case TIMEOFDAY:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, TimeComponent::new);
                    case TIMEOFDAYZONED:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, (parents, value) -> new Component2<@Value TemporalAccessor/*OffsetTime*/, @Value TemporalAccessor /*LocalTime*/, @Value ZoneOffset>(parents, subParents -> new TimeComponent(subParents, value), null, subParents -> new PlusMinusOffsetComponent(parents, value.get(ChronoField.OFFSET_SECONDS)), (a, b) -> OffsetTime.of((LocalTime)a, b)));
                    case DATETIME:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, (parents, value) -> new Component2<@Value TemporalAccessor/*LocalDateTime*/, @Value TemporalAccessor /*LocalDate*/, @Value TemporalAccessor /*LocalTime*/>(parents, subParents -> new YMD(subParents, value), " ", subParents -> new TimeComponent(subParents, value), (a, b) -> LocalDateTime.of((LocalDate)a, (LocalTime)b)));
                    case DATETIMEZONED:
                        return new GetValueAndComponent<@Value TemporalAccessor>(g, (parents0, value) ->
                            new Component2<@Value TemporalAccessor /*ZonedDateTime*/, @Value TemporalAccessor /*LocalDateTime*/, @Value ZoneId>(parents0,
                                parents1 -> new Component2<@Value TemporalAccessor /*LocalDateTime*/, @Value TemporalAccessor /*LocalDate*/, @Value TemporalAccessor /*LocalTime*/>(
                                        parents1, parents2 -> new YMD(parents2, value), " ", parents2 -> new TimeComponent(parents2, value), (a, b) -> LocalDateTime.of((LocalDate)a, (LocalTime)b)),
                                " ",
                                parents1 -> new ZoneIdComponent(parents1, ((ZonedDateTime)value).getZone()), (a, b) -> ZonedDateTime.of((LocalDateTime)a, b))
                        );
                }
                throw new InternalException("Unknown type: " + dateTimeInfo.getType());
            }

            @Override
            public GetValueAndComponent<?> tagged(TypeId typeName, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException
            {
                GetValue<TaggedValue> getTagged = DataTypeUtility.toTagged(g, tagTypes);
                return new GetValueAndComponent<TaggedValue>(getTagged, (parents, v) -> new TaggedComponent(parents, tagTypes, v));
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
                GetValue<Object[]> tupleGet = new GetValue<Object[]>()
                {
                    @Override
                    public Object @OnThread(Tag.Simulation) @NonNull [] getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        Object[] r = new Object[types.size()];
                        for (int i = 0; i < r.length; i++)
                        {
                            r[i] = gvacs.get(i).g.getWithProgress(index, progressListener);
                        }
                        return r;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public @OnThread(Tag.Simulation) void set(int index, Object[] value) throws InternalException, UserException
                    {
                        for (int i = 0; i < value.length; i++)
                        {
                            // Cast because we can't express type safety:
                            ((GetValue)gvacs.get(i).g).set(index, value[i]);
                        }
                    }
                };


                return new GetValueAndComponent<Object[]>(tupleGet, (parents, value) -> {
                    ArrayList<FXPlatformFunctionIntUser<ImmutableList<Component<?>>, Component<? extends Object>>> components = new ArrayList<>(types.size());
                    for (int i = 0; i < types.size(); i++)
                    {
                        GetValueAndComponent<?> gvac = gvacs.get(i);
                        // Have to use some casts because we can't express the type safety:
                        @SuppressWarnings("unchecked")
                        ComponentMaker<Object> makeComponent = (ComponentMaker)gvac.makeComponent;
                        int iFinal = i;
                        components.add(subParents -> makeComponent.makeComponent(subParents, value[iFinal]));
                    }
                    return new FixedLengthComponentList<Object[], Object>(parents, "(", components, ",", ")", l -> l.toArray());
                });
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<?> array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException
            {
                if (inner == null)
                    throw new InternalException("Can't make components for the empty list type");

                @NonNull DataType innerType = inner;

                return new GetValueAndComponent<@Value ListEx>(DataTypeUtility.toListEx(innerType, g), (parents, value) ->
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
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    public static Component<@NonNull @Value ?> component(ImmutableList<Component<?>> parents, DataType dataType, @Nullable @Value Object value) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<Component<@NonNull @Value ?>, InternalException>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull @Value ?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumberEntry(parents, (Number) value);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull @Value ?> text() throws InternalException
            {
                return new TextEntry(parents, value == null ? "" : (String)value);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull @Value ?> bool() throws InternalException
            {
                return new BoolComponent(parents, (Boolean)value);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull @Value ?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return new YMD(parents, (TemporalAccessor)value);
                    case YEARMONTH:
                        return new YM(parents, (TemporalAccessor)value);
                    case TIMEOFDAY:
                        return new TimeComponent(parents, (TemporalAccessor)value);
                    case TIMEOFDAYZONED:
                        return new Component2<@Value TemporalAccessor /*OffsetTime*/, @Value TemporalAccessor /*LocalTime*/, @Value ZoneOffset>(parents, subParents -> new TimeComponent(subParents, (TemporalAccessor)value), null, subParents -> new PlusMinusOffsetComponent(subParents, value == null ? null : ((OffsetTime)value).getOffset().getTotalSeconds()), (a, b) -> OffsetTime.of((LocalTime) a, b));
                    case DATETIME:
                        return new Component2<@Value TemporalAccessor /*LocalDateTime*/, @Value TemporalAccessor /*LocalDate*/, @Value TemporalAccessor /*LocalTime*/>(parents, subParents -> new YMD(subParents, (TemporalAccessor)value), " ", subParents -> new TimeComponent(subParents, (TemporalAccessor)value), (a, b) -> LocalDateTime.of((LocalDate)a, (LocalTime)b));
                    case DATETIMEZONED:
                        return new Component2<@Value TemporalAccessor /*ZonedDateTime*/, @Value TemporalAccessor /*LocalDateTime*/, @Value ZoneId>(parents,
                                        parents1 -> new Component2<@Value TemporalAccessor /*LocalDateTime*/, @Value TemporalAccessor /*LocalDate*/, @Value TemporalAccessor /*LocalTime*/>(
                                                parents1, parents2 -> new YMD(parents2, (TemporalAccessor)value), " ", parents2 -> new TimeComponent(parents2, (TemporalAccessor)value), (a, b) -> LocalDateTime.of((LocalDate)a, (LocalTime)b)),
                                        " ",
                                        parents1 -> new ZoneIdComponent(parents1, value == null ? null : ((ZonedDateTime)value).getZone()), (a, b) -> ZonedDateTime.of((LocalDateTime)a, b));
                }
                throw new InternalException("Unknown type: " + dateTimeInfo.getType());
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull @Value ?> tagged(TypeId typeName, ImmutableList<TagType<DataType>> tagTypes) throws InternalException
            {
                return new TaggedComponent(parents, tagTypes, (TaggedValue)value);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull @Value ?> tuple(ImmutableList<DataType> types) throws InternalException
            {
                List<FXPlatformFunctionInt<ImmutableList<Component<?>>, Component<? extends @Value Object>>> comps = new ArrayList<>(types.size());
                for (int i = 0; i < types.size(); i++)
                {
                    DataType type = types.get(i);
                    int iFinal = i;
                    comps.add(subParents -> component(subParents, type, value == null ? null : ((@Value Object @Value[]) value)[iFinal]));
                }
                return new FixedLengthComponentList<@Value Object @Value[], @Value Object>(parents, "(", ",", comps, ")", (List<@Value Object> l) -> DataTypeUtility.value(l.toArray(new @Value Object[0])));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull @Value ?> array(@Nullable DataType inner) throws InternalException
            {
                if (inner == null)
                    throw new InternalException("Can't make components for the empty list type");

                @NonNull DataType innerType = inner;
                if (value == null)
                {
                    return new VariableLengthComponentList<@Value ListEx, @Value Object>(parents, "[", ",", "]", ListExList::new)
                    {
                        @Override
                        protected Component<? extends @Value Object> makeNewEntry(ImmutableList<Component<?>> subParents) throws InternalException
                        {
                            return component(subParents, innerType, null);
                        }
                    };
                }
                else
                {
                    try
                    {
                        List<@Value Object> fxList = DataTypeUtility.fetchList((ListEx)value);

                        // Have to use some casts because we can't express the type safety:
                        @SuppressWarnings("unchecked")
                        ComponentMaker<@Value Object> makeComponent = (ComponentMaker) valueAndComponent(innerType.fromCollapsed((index, prog) -> DataTypeUtility.value(0))).makeComponent;
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
                    }
                    catch (UserException e)
                    {
                        throw new InternalException("Unexpected fetch issue", e);
                    }
                }
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
                Workers.onWorkerThread("Saving " + v, Priority.SAVE_ENTRY, () ->
                {
                    Utility.alertOnError_(() -> store(rowIndex, v));
                });
            }, onFocusChange, relinquishFocus);
            //field.mouseTransparentProperty().bind(field.focusedProperty().not());
            return field;
        }
    }
    */
}
