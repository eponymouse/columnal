package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.stage.Modality;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.transformations.Sort.Direction;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.UnitType;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.LightDialog;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class EditSortDialog extends LightDialog<ImmutableList<Pair<ColumnId, Direction>>>
{
    private final ImmutableList<Table> possibleTables;
    private final @Nullable RecordSet dataWithColumns;
    private final SortList sortList;

    public EditSortDialog(View parent, Point2D lastScreenPos, @Nullable Table srcTable, Table destTable, ImmutableList<Pair<ColumnId, Direction>> originalSortBy)
    {
        super(parent.getWindow());
        setResizable(true);
        initModality(Modality.NONE);
        possibleTables = srcTable == null ? ImmutableList.of(destTable) : ImmutableList.of(srcTable, destTable);
        @Nullable RecordSet d = null;
        try
        {
            d = srcTable == null ? destTable.getData() : srcTable.getData();
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
        }
        dataWithColumns = d;

        sortList = new SortList(originalSortBy);
        sortList.getNode().setMinWidth(250.0);
        sortList.getNode().setMinHeight(150.0);
        sortList.getNode().setPrefWidth(300.0);
        sortList.getNode().setPrefHeight(250.0);
        getDialogPane().setContent(new BorderPane(sortList.getNode(), new Label("Choose the columns to sort by"), null, null, null));
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        getDialogPane().getStyleClass().add("sort-list-dialog");
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return sortList.getItems();
            else
                return null;
        });
        setOnShowing(e -> {
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
            parent.enableColumnPickingMode(lastScreenPos, p -> possibleTables.contains(p.getFirst()), t -> {
                sortList.pickColumnIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
    }

    @OnThread(Tag.FXPlatform)
    private class SortList extends FancyList<Pair<ColumnId, Direction>, SortPane>
    {
        public SortList(ImmutableList<Pair<ColumnId, Direction>> initialItems)
        {
            super(initialItems, true, true, true);
            getStyleClass().add("sort-list");
            listenForCellChange(c -> {
                updateButtonWidths();
                // When cell is added, not yet in scene, so run later in case it's the largest one:
                FXUtility.runAfterDelay(Duration.millis(100), () -> updateButtonWidths());
            });
        }

        @Override
        protected Pair<SortPane, ObjectExpression<Pair<ColumnId, Direction>>> makeCellContent(@Nullable Pair<ColumnId, Direction> initialContent, boolean editImmediately)
        {
            SortPane sortPane = new SortPane(initialContent);
            return new Pair<>(sortPane, sortPane.currentValue());
        }

        public void pickColumnIfEditing(Pair<Table, ColumnId> t)
        {
            // This is a bit of a hack.  The problem is that clicking the column removes the focus
            // from the edit field, so we can't just ask if the edit field is focused.  Tracking who
            // the focus transfers from/to seems a bit awkward, so we just use a time-based system,
            // where if they were very recently (200ms) editing a field, fill in that field with the table.
            // If they weren't recently editing a field, we append to the end of the list.
            long curTime = System.currentTimeMillis();
            SortPane curEditing = streamCells()
                    .map(cell -> cell.getContent())
                    .filter(p -> p.lastEditTimeMillis() > curTime - 200L).findFirst().orElse(null);
            if (curEditing != null)
            {
                curEditing.setContent(t.getSecond());
                if (addButton != null)
                    addButton.requestFocus();
            }
            else
            {
                // Add to end:
                addToEnd(new Pair<>(t.getSecond(), Direction.ASCENDING), false);
            }
        }

        public void updateButtonWidths(@UnknownInitialization(FancyList.class) SortList this)
        {
            ImmutableList<SortPane.DirectionButton> buttons = streamCells().map(c -> c.getContent().button).collect(ImmutableList.toImmutableList());
            if (buttons.isEmpty())
                return;
            // Find the largest preferred width:
            double largestPrefWidth = buttons.stream().mapToDouble(b -> b.prefWidth(-1)).max().orElse(30.0);
            for (SortPane.DirectionButton button : buttons)
            {
                // Must set min, not pref, otherwise it screws up our calculation above:
                button.setMinWidth(largestPrefWidth);
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    private class SortPane extends BorderPane
    {
        private final SimpleObjectProperty<Pair<ColumnId, Direction>> currentValue;
        private final TextField columnField;
        private final AutoComplete autoComplete;
        private final DirectionButton button;
        private long lastEditTimeMillis = -1;

        public SortPane(@Nullable Pair<ColumnId, Direction> initialContent)
        {
            currentValue = new SimpleObjectProperty<>(initialContent == null ? new Pair<>(new ColumnId(""), Direction.ASCENDING) : initialContent);
            columnField = new TextField(initialContent == null ? "" : initialContent.getFirst().getRaw());
            BorderPane.setMargin(columnField, new Insets(0, 2, 2, 5));
            autoComplete = new AutoComplete(columnField,
                (s, q) -> possibleTables.stream().flatMap(t -> {
                    try
                    {
                        return t.getData().getColumns().stream();
                    }
                    catch (UserException | InternalException e)
                    {
                        Log.log(e);
                        return Stream.empty();
                    }
                }).filter(c -> c.getName().getOutput().contains(s)).map(ColumnCompletion::new).collect(Collectors.<Completion>toList()),
                getListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, c -> false);
            FXUtility.addChangeListenerPlatformNN(columnField.focusedProperty(), focus -> {
                // Update whether focus is arriving or leaving:
                lastEditTimeMillis = System.currentTimeMillis();
            });
            Label label = new Label("Type table name or click on column");
            label.visibleProperty().bind(columnField.focusedProperty());
            setTop(label);
            setCenter(columnField);
            button = new DirectionButton();
            button.setDirection(initialContent == null ? Direction.ASCENDING : initialContent.getSecond());
            button.setType(calculateTypeOf(initialContent == null ? null : initialContent.getFirst()));
            setRight(button);
            BorderPane.setMargin(button, new Insets(0, 4, 0, 4));
            getStyleClass().add("sort-pane");
            
        }

        public long lastEditTimeMillis()
        {
            return columnField.isFocused() ? System.currentTimeMillis() : lastEditTimeMillis;
        }

        private CompletionListener getListener(@UnknownInitialization SortPane this)
        {
            return new CompletionListener()
            {
                @Override
                public String doubleClick(String currentText, Completion selectedItem)
                {
                    // TODO update the sort button
                    return ((ColumnCompletion) selectedItem).c.getName().getOutput();
                }

                @Override
                public String nonAlphabetCharacter(String textBefore, @Nullable Completion selectedItem, String textAfter)
                {
                    return textBefore + textAfter; // Shouldn't happen as not using alphabets
                }

                @Override
                public String keyboardSelect(String currentText, Completion selectedItem)
                {
                    return doubleClick(currentText, selectedItem);
                }

                @Override
                public String exactCompletion(String currentText, Completion selectedItem)
                {
                    return doubleClick(currentText, selectedItem);
                }

                @Override
                public String focusLeaving(String currentText, @Nullable Completion selectedItem)
                {
                    if (selectedItem != null)
                        return doubleClick(currentText, selectedItem);
                    else
                        return currentText;
                }
            };
        }

        public void setContent(ColumnId columnId)
        {
            autoComplete.setContentDirect(columnId.getRaw());
            currentValue.set(new Pair<>(columnId, currentValue.get().getSecond()));
        }

        public ObjectExpression<Pair<ColumnId, Direction>> currentValue()
        {
            return currentValue;
        }

        @OnThread(Tag.FXPlatform)
        private class DirectionButton extends Button
        {
            private final Label topLabel;
            private final Label bottomLabel;
            private final Shape icon;
            private String smallItem = "A";
            private String largeItem = "Z";
            private Direction direction = Direction.ASCENDING;

            public DirectionButton()
            {
                icon = new Polygon(4, 0, 8, 25, 0, 25);
                icon.getStyleClass().add("sort-direction-icon");
                BorderPane.setMargin(icon, new Insets(0, 1, 0, 3));
                BorderPane sortGraphic = new BorderPane();
                topLabel = new Label(smallItem);
                bottomLabel = new Label(largeItem);
                sortGraphic.setCenter(new BorderPane(null, topLabel, null, bottomLabel, null));
                sortGraphic.setRight(icon);
                setText("");
                setGraphic(sortGraphic);
                setOnAction(e -> {
                    FXUtility.mouse(this).setDirection(direction == Direction.ASCENDING ? Direction.DESCENDING : Direction.ASCENDING);
                });
                getStyleClass().add("sort-direction-button");
            }

            public void setDirection(Direction direction)
            {
                this.direction = direction;
                currentValue.set(new Pair<>(currentValue.get().getFirst(), direction));
                icon.setRotate(direction == Direction.ASCENDING ? 0 : 180);
                topLabel.setText(direction == Direction.ASCENDING ? smallItem : largeItem);
                bottomLabel.setText(direction == Direction.ASCENDING ? largeItem : smallItem);
            }
            
            public void setType(@Nullable DataType dataType)
            {
                if (dataType == null)
                    dataType = DataType.TEXT;
                try
                {
                    dataType.apply(new DataTypeVisitor<UnitType>()
                    {
                        @Override
                        public UnitType number(NumberInfo numberInfo) throws InternalException, UserException
                        {
                            smallItem = "1";
                            largeItem = "99";
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType text() throws InternalException, UserException
                        {
                            smallItem = "A";
                            largeItem = "Z";
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                        {
                            switch (dateTimeInfo.getType())
                            {
                                case YEARMONTHDAY:
                                case YEARMONTH:
                                case DATETIME:
                                case DATETIMEZONED:
                                    smallItem = "1965";
                                    largeItem = "2016";
                                    break;
                                case TIMEOFDAY:
                                case TIMEOFDAYZONED:
                                    smallItem = "00:00";
                                    largeItem = "11:21";
                                    break;
                                
                            }
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType bool() throws InternalException, UserException
                        {
                            smallItem = "false";
                            largeItem = "true";
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                        {
                            if (tags.size() == 1 && tags.get(0).getInner() != null)
                            {
                                // Recurse first:
                                tags.get(0).getInner().apply(this);
                                // Then wrap:
                                smallItem = tags.get(0).getName() + " (" + smallItem + ")";
                                largeItem = tags.get(0).getName() + " (" + largeItem + ")";
                            }
                            else
                            {
                                // Just show tag names:
                                smallItem = tags.get(0).getName() + " \u2026";
                                largeItem = tags.get(tags.size() - 1).getName() + " \u2026";
                            }
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType tuple(ImmutableList<DataType> inner) throws InternalException, UserException
                        {
                            // Recurse first:
                            inner.get(0).apply(this);
                            // Then wrap:
                            smallItem = "(" + smallItem + ", \u2026)";
                            largeItem = "(" + largeItem + ", \u2026)";
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType array(@Nullable DataType inner) throws InternalException, UserException
                        {
                            if (inner == null)
                            {
                                smallItem = "[]";
                                largeItem = "[]";
                            }
                            else
                            {
                                // Recurse first:
                                inner.apply(this);
                                // Then wrap:
                                smallItem = "[" + smallItem + ", \u2026]";
                                largeItem = "[" + largeItem + ", \u2026]";
                            }
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType toInfer() throws InternalException, UserException
                        {
                            return text();
                        }
                    });
                }
                catch (UserException | InternalException e)
                {
                    Log.log(e);
                }
                // Will update labels:
                setDirection(direction);
                sortList.updateButtonWidths();
            }
        }
    }

    private @Nullable DataType calculateTypeOf(@Nullable ColumnId columnId)
    {
        if (columnId == null || dataWithColumns == null)
            return null;
        @Nullable Column c = dataWithColumns.getColumnOrNull(columnId);
        if (c == null)
            return null;
        try
        {
            return c.getType();
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
            return null;
        }
    }

    private static class ColumnCompletion extends Completion
    {
        private final Column c;

        private ColumnCompletion(Column c)
        {
            this.c = c;
        }

        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(c.getName().getOutput()));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return c.getName().getRaw().toLowerCase().contains(input.toLowerCase());
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            boolean match = input.equals(c.getName().getOutput());
            if (match && onlyAvailableCompletion)
                return CompletionAction.COMPLETE_IMMEDIATELY;
            else if (match || onlyAvailableCompletion)
                return CompletionAction.SELECT;
            else
                return CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            // I don't believe this will end up being called anyway as we don't use alphabets:
            return c.getName().getOutput().contains("" + character);
        }
    }
}
