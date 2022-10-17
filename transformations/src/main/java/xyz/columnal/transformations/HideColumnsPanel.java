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

package xyz.columnal.transformations;

import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.FXUtility.DragHandler;
import xyz.columnal.utility.gui.GUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A panel with two column expression lists, one on the left with columns to hide,
 * and one on the right with shown columns.  Drag and drop is allowed between the
 * two lists, and there's also a button to hide columns.
 */
@OnThread(Tag.FXPlatform)
public final class HideColumnsPanel
{
    private final ListView<ColumnId> hiddenColumns;
    private final ObservableList<ColumnId> allSourceColumns = FXCollections.observableArrayList();
    // All minus hidden:
    private final ListView<ColumnId> shownColumns;
    private final Pane pane;

    public HideColumnsPanel(TableManager mgr, @Nullable TableId srcId, ImmutableList<ColumnId> initiallyHidden)
    {
        this.hiddenColumns = new ListView<>(FXCollections.observableArrayList(initiallyHidden));
        this.shownColumns = new ListView<>();
        Label shownMessage = new Label("Loading...");
        this.shownColumns.setPlaceholder(shownMessage);

        Button add = new Button("Transfer");
        add.getStyleClass().add("add-button");
        add.setMinWidth(Region.USE_PREF_SIZE);
        GridPane.setValignment(add, VPos.TOP);
        GridPane.setMargin(add, new Insets(30, 0, 0, 0));
        GridPane.setHalignment(add, HPos.CENTER);
        
        GridPane gridPane = new GridPane();
        GridPane.setHgrow(shownColumns, Priority.ALWAYS);
        GridPane.setHgrow(hiddenColumns, Priority.ALWAYS);
        Label hiddenLabel = GUI.label("transformEditor.hide.hiddenColumns");
        GridPane.setHalignment(hiddenLabel, HPos.CENTER);
        GridPane.setMargin(hiddenLabel, new Insets(0, 0, 10, 0));
        gridPane.add(hiddenLabel, 0, 0);
        Label showingLabel = GUI.label("transformEditor.hide.srcColumns");
        GridPane.setMargin(showingLabel, new Insets(0, 0, 10, 0));
        GridPane.setHalignment(showingLabel, HPos.CENTER);
        gridPane.add(showingLabel, 2, 0);
        gridPane.add(hiddenColumns, 0, 1);
        gridPane.add(add, 1, 1);
        gridPane.add(shownColumns, 2, 1);
        gridPane.getStyleClass().add("hide-columns-lists");
        this.pane = gridPane;

        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPercentWidth(40);
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPercentWidth(20);
        ColumnConstraints column3 = new ColumnConstraints();
        column3.setPercentWidth(40);
        gridPane.getColumnConstraints().setAll(column1, column2, column3);
        
        updateButton(add);
        
        Workers.onWorkerThread("Fetching column names", Workers.Priority.FETCH, () -> {
            Table src = srcId == null ? null : mgr.getSingleTableOrNull(srcId);
            if (src == null)
            {
                FXUtility.runFX(() -> shownMessage.setText("Could not find source table."));
            }
            else
            {
                try
                {
                    ImmutableList<ColumnId> columnIds = src.getData().getColumns().stream().map(c -> c.getName()).collect(ImmutableList.<ColumnId>toImmutableList());
                    FXUtility.runFX(() -> {
                        allSourceColumns.setAll(columnIds);
                        shownMessage.setText("None");
                    });
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                    FXUtility.runFX(() -> shownMessage.setText("Error:" + e.getLocalizedMessage()));
                }
            }
        });
        updateShownColumns();

        shownColumns.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        shownColumns.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
            {
                addAllItemsToHidden(shownColumns.getSelectionModel().getSelectedItems());
            }
        });
        // Don't need to remove here because we already filter by the items from hiddenColumns
        FXUtility.enableDragFrom(shownColumns, "ColumnId", TransferMode.MOVE);

        this.shownColumns.getStyleClass().add("shown-columns-list-view");
        this.hiddenColumns.getStyleClass().add("hidden-columns-list-view");
        FXUtility.listViewDoubleClick(hiddenColumns,c -> {
            // Take copy to avoid problems with concurrent modifications:
            List<ColumnId> selectedItems = new ArrayList<>(hiddenColumns.getSelectionModel().getSelectedItems());
            if (!selectedItems.isEmpty())
            {
                hiddenColumns.getItems().removeAll(selectedItems);
                updateShownColumns();
            }
        });

        FXUtility.enableDragTo(hiddenColumns, Collections.singletonMap(FXUtility.getTextDataFormat("ColumnId"), new DragHandler()
        {
            @Override
            @SuppressWarnings("unchecked")
            public @OnThread(Tag.FXPlatform) boolean dragEnded(Dragboard db, Point2D pointInScene)
            {
                @Nullable Object content = db.getContent(FXUtility.getTextDataFormat("ColumnId"));
                if (content != null && content instanceof List)
                {
                    FXUtility.mouse(HideColumnsPanel.this).addAllItemsToHidden((List<ColumnId>) content);
                    return true;
                }
                return false;
            }
        }));

        add.setOnAction(e -> {
            ObservableList<ColumnId> selectedItems = shownColumns.getSelectionModel().getSelectedItems();
            if (!selectedItems.isEmpty())
                addAllItemsToHidden(selectedItems);
            else
            {
                selectedItems = hiddenColumns.getSelectionModel().getSelectedItems();
                hiddenColumns.getItems().removeAll(selectedItems);
                updateShownColumns();
            }
        });
        FXUtility.listViewDoubleClick(shownColumns, c -> {
            addAllItemsToHidden(ImmutableList.of(c));
        });
        
        FXUtility.listen(shownColumns.getSelectionModel().getSelectedItems(), c -> {
            if (!shownColumns.getSelectionModel().getSelectedItems().isEmpty())
                hiddenColumns.getSelectionModel().clearSelection();
            
            updateButton(add);
        });
        FXUtility.listen(hiddenColumns.getSelectionModel().getSelectedItems(), c -> {
            if (!hiddenColumns.getSelectionModel().getSelectedItems().isEmpty())
                shownColumns.getSelectionModel().clearSelection();
            updateButton(add);
        });
    }

    private void updateButton(Button add)
    {
        if (!shownColumns.getSelectionModel().isEmpty())
        {
            add.setDisable(false);
            add.setText("<< Hide");
        }
        else if (!hiddenColumns.getSelectionModel().isEmpty())
        {
            add.setDisable(false);
            add.setText("Show >>");
        }
        else
        {
            add.setDisable(true);
            add.setText("Transfer");
        }
    }

    private void updateShownColumns()
    {
        shownColumns.setItems(
            allSourceColumns.filtered(c -> !hiddenColumns.getItems().contains(c))
        );
    }


    @OnThread(Tag.FXPlatform)
    public void addAllItemsToHidden(List<ColumnId> items)
    {
        for (ColumnId selected : items)
        {
            if (!hiddenColumns.getItems().contains(selected))
            {
                hiddenColumns.getItems().add(selected);
            }
        }
        ObservableList<ColumnId> srcList = shownColumns.getItems();
        hiddenColumns.getItems().sort(Comparator.<ColumnId, Pair<Integer, ColumnId>>comparing(col -> {
            // If it's in the original, we sort by original position
            // Otherwise we put it at the top (which will be -1 in original, which
            // works out neatly) and sort by name.
            int srcIndex = srcList.indexOf(col);
            return new Pair<Integer, ColumnId>(srcIndex, col);
        }, Pair.<Integer, ColumnId>comparator()));
        
        updateShownColumns();
    }

    public Node getNode()
    {
        return pane;
    }

    public ImmutableList<ColumnId> getHiddenColumns()
    {
        return ImmutableList.copyOf(hiddenColumns.getItems());
    }
}
