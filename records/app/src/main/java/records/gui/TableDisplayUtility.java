package records.gui;

import annotation.qual.Value;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.data.Column;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue.DataTypeVisitorGetEx;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.stf.Component2;
import records.gui.stf.NumberEntry;
import records.gui.stf.PlusMinusOffsetComponent;
import records.gui.stf.StructuredTextField;
import records.gui.stf.TextEntry;
import records.gui.stf.TimeComponent;
import records.gui.stf.YM;
import records.gui.stf.YMD;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import records.gui.stable.StableView.ColumnHandler;
import records.gui.stable.StableView.CellContentReceiver;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 01/05/2017.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplayUtility
{

    public static List<Pair<String, ColumnHandler>> makeStableViewColumns(RecordSet recordSet)
    {
        return Utility.mapList(recordSet.getColumns(), col -> {
            try
            {
                return getDisplay(col);
            }
            catch (InternalException | UserException e)
            {
                // Show a dummy column with an error message:
                return new Pair<>(col.getName().getRaw(), new ColumnHandler()
                {
                    @Override
                    public void fetchValue(int rowIndex, CellContentReceiver receiver, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl)
                    {
                        receiver.setCellContent(rowIndex, new Label("Error: " + e.getLocalizedMessage()));
                    }

                    @Override
                    public void columnResized(double width)
                    {

                    }

                    @Override
                    public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
                    {
                        return null;
                    }

                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable endEdit)
                    {
                        Utility.logStackTrace("Called edit when not editable");
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return false;
                    }

                    @Override
                    public boolean editHasFocus(int rowIndex)
                    {
                        return false;
                    }
                });
            }
        });
    }

    private static Pair<String, ColumnHandler> getDisplay(@NonNull Column column) throws UserException, InternalException
    {
        return new Pair<>(column.getName().getRaw(), column.getType().<ColumnHandler, UserException>applyGet(new DataTypeVisitorGetEx<ColumnHandler, UserException>()
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

                return new DisplayCache<@Value String, StringDisplay>(g, null, s -> s) {
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
            public ColumnHandler bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                @Localized String stringTrue = TranslationUtility.getString("boolean.true");
                @Localized String stringFalse = TranslationUtility.getString("boolean.false");
                return new DisplayCache<Boolean, Label>(g, null, l -> l) {
                    @Override
                    protected Label makeGraphical(int rowIndex, Boolean value)
                    {
                        Label label = new Label(value ? stringTrue : stringFalse);
                        label.setMouseTransparent(true);
                        return label;
                    }

                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable endEdit)
                    {
                        @Nullable Label l = getRowIfShowing(rowIndex);
                        if (l != null)
                            l.getParent().requestFocus();
                    }

                    @Override
                    public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
                    {
                        return InputMap.sequence(
                            InputMap.consume(EventPattern.keyTyped(c -> c.toLowerCase().charAt(0) == stringTrue.toLowerCase().charAt(0)), e -> {
                                Workers.onWorkerThread("Setting true", Priority.SAVE_ENTRY, () -> Utility.alertOnError_(() -> g.set(rowIndex, true)));
                                @Nullable Label l = getRowIfShowing(rowIndex);
                                if (l != null)
                                    l.setText(stringTrue);
                            }),
                            InputMap.consume(EventPattern.keyTyped(c -> c.toLowerCase().charAt(0) == stringFalse.toLowerCase().charAt(0)), e -> {
                                Workers.onWorkerThread("Setting false", Priority.SAVE_ENTRY, () -> Utility.alertOnError_(() -> g.set(rowIndex, false)));
                                @Nullable Label l = getRowIfShowing(rowIndex);
                                if (l != null)
                                    l.setText(stringFalse);
                            })
                        );
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return true;
                    }

                    @Override
                    public boolean editHasFocus(int rowIndex)
                    {
                        @Nullable Label l = getRowIfShowing(rowIndex);
                        if (l != null)
                            l.getParent().isFocused();
                        return false;
                    }
                };
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            public ColumnHandler tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }
        }));
    }

    // package-visible
    static StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> docFromSegments(StyledText<Collection<String>>... segments)
    {
        ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment((StyledText<Collection<String>>)segments[0], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps());
        for (int i = 1; i < segments.length; i++)
        {
            doc = doc.concat(ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment(segments[i], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps()));
        }
        return doc;
    }

    // public for testing:
    @OnThread(Tag.FXPlatform)
    public static DisplayCacheSTF<?> makeField(DataTypeValue dataTypeValue) throws InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGetEx<DisplayCacheSTF<?>, InternalException>()
        {
            @Override
            public DisplayCacheSTF<?> number(GetValue<Number> g, NumberInfo displayInfo) throws InternalException
            {
                return new DisplayCacheSTF<>(g, value -> new StructuredTextField<>(new NumberEntry(value)));
            }

            @Override
            public DisplayCacheSTF<?> text(GetValue<String> g) throws InternalException
            {
                return new DisplayCacheSTF<>(g, value -> new StructuredTextField<>(new TextEntry(value)));
            }

            @Override
            public DisplayCacheSTF<?> bool(GetValue<Boolean> g) throws InternalException
            {
                throw new UnimplementedException();
            }

            @Override
            public DisplayCacheSTF<?> date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> g) throws InternalException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return new DisplayCacheSTF<>(g, value -> new StructuredTextField<>(new YMD(value)));
                    case YEARMONTH:
                        return new DisplayCacheSTF<>(g, value -> new StructuredTextField<>(new YM(value)));
                    case TIMEOFDAY:
                        return new DisplayCacheSTF<>(g, value -> new StructuredTextField<>(new TimeComponent(value)));
                    case TIMEOFDAYZONED:
                        return new DisplayCacheSTF<>(g, value -> new StructuredTextField<>(new PlusMinusOffsetComponent<>(new TimeComponent(value), value.get(ChronoField.OFFSET_SECONDS), OffsetTime::of)));
                    case DATETIME:
                        return new DisplayCacheSTF<>(g, value -> new StructuredTextField<>(new Component2<LocalDateTime, LocalDate, LocalTime>(new YMD(value), " ", new TimeComponent(value), LocalDateTime::of)));
                    case DATETIMEZONED:
                        break;
                }
                throw new InternalException("Unknown type: " + dateTimeInfo.getType());
            }

            @Override
            public DisplayCacheSTF<?> tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException
            {
                throw new UnimplementedException();
            }

            @Override
            public DisplayCacheSTF<?> tuple(List<DataTypeValue> types) throws InternalException
            {
                throw new UnimplementedException();
            }

            @Override
            public DisplayCacheSTF<?> array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException
            {
                throw new UnimplementedException();
            }
        });
    }

    private static interface FieldMaker<V>
    {
        @OnThread(Tag.FXPlatform)
        public StructuredTextField<? extends V> make(V value) throws InternalException;
    }

    public static class DisplayCacheSTF<V> extends DisplayCache<V, StructuredTextField<? extends V>>
    {
        private final FieldMaker<V> makeField;

        public DisplayCacheSTF(GetValue<V> g, FieldMaker<V> makeField)
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
        public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable endEdit)
        {
            @Nullable StructuredTextField<? extends V> display = getRowIfShowing(rowIndex);
            if (display != null)
            {
                display.edit(scenePoint, endEdit);
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
            @Nullable StructuredTextField<? extends V> display = getRowIfShowing(rowIndex);
            if (display != null)
                return display.isFocused();
            return false;
        }

        @Override
        protected StructuredTextField<? extends V> makeGraphical(int rowIndex, V value) throws InternalException
        {
            StructuredTextField<? extends V> field = makeField.make(value);
            //field.mouseTransparentProperty().bind(field.focusedProperty().not());
            return field;
        }
    }
}
