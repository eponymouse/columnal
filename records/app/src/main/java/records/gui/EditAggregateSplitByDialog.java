package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
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
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.transformations.Sort.Direction;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.UnitType;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.Instruction;
import utility.gui.LightDialog;
import utility.gui.TranslationUtility;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class EditAggregateSplitByDialog extends LightDialog<ImmutableList<ColumnId>>
{
    private final @Nullable Table srcTable;
    private final SplitList splitList;

    public EditAggregateSplitByDialog(View parent, @Nullable Point2D lastScreenPos, @Nullable Table srcTable, @Nullable Pair<ColumnId, ImmutableList<String>> example, ImmutableList<ColumnId> originalSplitBy)
    {
        super(parent);
        setResizable(true);
        initModality(Modality.NONE);
        this.srcTable = srcTable;

        splitList = new SplitList(originalSplitBy);
        splitList.getNode().setMinWidth(250.0);
        splitList.getNode().setMinHeight(150.0);
        splitList.getNode().setPrefWidth(300.0);
        splitList.getNode().setPrefHeight(250.0);
        String header = "Aggregate can either calculate once for the whole table, or separately depending on values of column(s) below." + (example == null || example.getSecond().size() < 2 ? "" : "\n\nFor example, if column " + example.getFirst().getRaw() + " is selected , there will be one calculation for rows with value " + example.getSecond().stream().map(EditAggregateSplitByDialog::truncate).collect(Collectors.joining(", one for rows with value ")) + ", etc");
        Label label = new Label(header + "\n ");
        label.setWrapText(true);
        label.setPrefWidth(300.0);
        Label wholeTableLabel = new Label("Calculate for whole table");
        splitList.addEmptyListenerAndCallNow(empty -> {
            wholeTableLabel.setVisible(empty);
        });
        getDialogPane().setContent(new BorderPane(new StackPane(splitList.getNode(), wholeTableLabel), label, null, null, null));
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        getDialogPane().getStyleClass().add("sort-list-dialog");
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return splitList.getItems();
            else
                return null;
        });
        setOnShowing(e -> {
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
            parent.enableColumnPickingMode(lastScreenPos, p -> Objects.equals(srcTable, p.getFirst()), t -> {
                splitList.pickColumnIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
    }

    private static String truncate(String orig)
    {
        if (orig.length() > 20)
            return orig.substring(0, 20) + "\u2026";
        else
            return orig;
    }

    @OnThread(Tag.FXPlatform)
    private class SplitList extends FancyList<ColumnId, ColumnPane>
    {
        public SplitList(ImmutableList<ColumnId> initialItems)
        {
            super(initialItems, true, true, () -> new ColumnId(""));
            getStyleClass().add("split-list");
            setAddButtonText(TranslationUtility.getString("aggregate.add.column"));
            
            // We don't want to do this actually; whole table
            // calculation is a common wish:
            //if (initialItems.isEmpty())
            //    addToEnd(new ColumnId(""), true);
        }

        @Override
        protected Pair<ColumnPane, FXPlatformSupplier<ColumnId>> makeCellContent(@Nullable ColumnId initialContent, boolean editImmediately)
        {
            ColumnPane columnPane = new ColumnPane(initialContent, editImmediately);
            return new Pair<>(columnPane, columnPane.currentValue()::get);
        }

        public void pickColumnIfEditing(Pair<Table, ColumnId> t)
        {
            // This is a bit of a hack.  The problem is that clicking the column removes the focus
            // from the edit field, so we can't just ask if the edit field is focused.  Tracking who
            // the focus transfers from/to seems a bit awkward, so we just use a time-based system,
            // where if they were very recently (200ms) editing a field, fill in that field with the table.
            // If they weren't recently editing a field, we append to the end of the list.
            long curTime = System.currentTimeMillis();
            ColumnPane curEditing = streamCells()
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
                addToEnd(t.getSecond(), false);
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    private class ColumnPane extends BorderPane
    {
        private final SimpleObjectProperty<ColumnId> currentValue;
        private final TextField columnField;
        private final AutoComplete autoComplete;
        private long lastEditTimeMillis = -1;

        public ColumnPane(@Nullable ColumnId initialContent, boolean editImmediately)
        {
            currentValue = new SimpleObjectProperty<>(initialContent == null ? new ColumnId("") : initialContent);
            columnField = new TextField(initialContent == null ? "" : initialContent.getRaw());
            if (editImmediately)
                FXUtility.onceNotNull(columnField.sceneProperty(), s -> FXUtility.runAfter(columnField::requestFocus));
            BorderPane.setMargin(columnField, new Insets(0, 2, 2, 5));
            autoComplete = new AutoComplete<ColumnCompletion>(columnField,
                (s, p, q) -> {
                    try
                    {
                        if (srcTable == null)
                            return Stream.empty();
                        
                        return srcTable.getData().getColumns().stream().filter(c -> c.getName().getOutput().contains(s)).map(ColumnCompletion::new);
                    }
                    catch (UserException | InternalException e)
                    {
                        Log.log(e);
                        return Stream.empty();
                    }
                },
                getListener(), () -> true, WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, (cur, next) -> false);
            FXUtility.addChangeListenerPlatformNN(columnField.focusedProperty(), focus -> {
                // Update whether focus is arriving or leaving:
                lastEditTimeMillis = System.currentTimeMillis();
            });
            FXUtility.addChangeListenerPlatformNN(columnField.textProperty(), t -> {
                currentValue.set(new ColumnId(t));
            });
            Instruction instruction = new Instruction("pick.column.instruction");
            instruction.showAboveWhenFocused(columnField);
            setCenter(columnField);
            getStyleClass().add("column-pane");
            
        }

        public long lastEditTimeMillis()
        {
            return columnField.isFocused() ? System.currentTimeMillis() : lastEditTimeMillis;
        }

        private CompletionListener<ColumnCompletion> getListener(@UnknownInitialization ColumnPane this)
        {
            return new CompletionListener<ColumnCompletion>()
            {
                @Override
                public String doubleClick(String currentText, ColumnCompletion selectedItem)
                {
                    // TODO update the sort button
                    return selectedItem.c.getName().getOutput();
                }

                @Override
                public String nonAlphabetCharacter(String textBefore, @Nullable ColumnCompletion selectedItem, String textAfter, OptionalInt positionCaret)
                {
                    return textBefore + textAfter; // Shouldn't happen as not using alphabets
                }

                @Override
                public String keyboardSelect(String textBefore, String textAfter, ColumnCompletion selectedItem)
                {
                    return doubleClick(textBefore + textAfter, selectedItem);
                }

                @Override
                public String exactCompletion(String currentText, ColumnCompletion selectedItem)
                {
                    return doubleClick(currentText, selectedItem);
                }

                @Override
                public String focusLeaving(String currentText, @Nullable ColumnCompletion selectedItem)
                {
                    if (selectedItem != null)
                        return doubleClick(currentText, selectedItem);
                    else
                        return currentText;
                }

                @Override
                public void tabPressed()
                {
                    // TODO focus next item or add button
                }
            };
        }

        public void setContent(ColumnId columnId)
        {
            autoComplete.setContentDirect(columnId.getRaw(), true);
            currentValue.set(columnId);
        }

        public ObjectExpression<ColumnId> currentValue()
        {
            return currentValue;
        }
    }

    private static class ColumnCompletion extends SimpleCompletion
    {
        private final Column c;

        private ColumnCompletion(Column c)
        {
            super(c.getName().getRaw(), null);
            this.c = c;
        }
    }
}
