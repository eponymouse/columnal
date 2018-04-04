package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
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
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.LightDialog;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class EditSortDialog extends LightDialog<ImmutableList<Pair<ColumnId, Direction>>>
{
    private final ImmutableList<Table> possibleTables;

    public EditSortDialog(View parent, Point2D lastScreenPos, @Nullable Table srcTable, Table destTable, ImmutableList<Pair<ColumnId, Direction>> originalSortBy)
    {
        super(parent.getWindow());
        initModality(Modality.NONE);
        possibleTables = srcTable == null ? ImmutableList.of(destTable) : ImmutableList.of(srcTable, destTable);

        SortList sortList = new SortList(originalSortBy);
        getDialogPane().setContent(sortList.getNode());
        sortList.getNode().setMinWidth(200.0);
        sortList.getNode().setMinHeight(150.0);
        sortList.getNode().setPrefWidth(250.0);
        sortList.getNode().setPrefHeight(200.0);
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
            parent.enableColumnPickingMode(lastScreenPos, p -> possibleTables.contains(p.getFirst()), t -> {
                sortList.pickColumnIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
    }

    private class SortList extends FancyList<Pair<ColumnId, Direction>, SortPane>
    {
        public SortList(ImmutableList<Pair<ColumnId, Direction>> initialItems)
        {
            super(initialItems, true, true, true);
        }

        @Override
        protected Pair<SortPane, ObjectExpression<Pair<ColumnId, Direction>>> makeCellContent(@Nullable Pair<ColumnId, Direction> initialContent, boolean editImmediately)
        {
            SortPane sortPane = new SortPane(initialContent);
            return new Pair<>(sortPane, sortPane.currentValue());
        }

        public void pickColumnIfEditing(Pair<Table, ColumnId> t)
        {
            // TODO
        }
    }

    @OnThread(Tag.FXPlatform)
    private class SortPane extends BorderPane
    {
        private final SimpleObjectProperty<Pair<ColumnId, Direction>> currentValue;
        private final TextField columnField;

        public SortPane(@Nullable Pair<ColumnId, Direction> initialContent)
        {
            currentValue = new SimpleObjectProperty<>(initialContent == null ? new Pair<>(new ColumnId(""), Direction.ASCENDING) : initialContent);
            columnField = new TextField();
            new AutoComplete(columnField,
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
            setTop(new Label("Type table name or click on column"));
            setCenter(columnField);
            BorderPane sortGraphic = new BorderPane();
            sortGraphic.setTop(new Label("A"));
            sortGraphic.setBottom(new Label("Z"));
            setRight(new Button("\u2191", sortGraphic));
            
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

        public ObjectExpression<Pair<ColumnId, Direction>> currentValue()
        {
            return currentValue;
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
