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

package xyz.columnal.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.ErrorableLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.FancyList;
import xyz.columnal.utility.TranslationUtility;

import java.util.Optional;

// Shows an editable list of table ids
@OnThread(Tag.FXPlatform)
public class TableListDialog extends ErrorableLightDialog<ImmutableList<TableId>>
{
    private final View parent;
    private final ImmutableSet<Table> excludeTables;
    private final TableList tableList;

    public TableListDialog(View parent, Table destTable, ImmutableList<TableId> originalItems, Point2D lastScreenPos)
    {
        super(parent, true);
        initModality(Modality.NONE);
        setResizable(true);
        this.parent = parent;
        this.excludeTables = ImmutableSet.of(destTable);
        tableList = new TableList(originalItems);            
        Region tableListNode = tableList.getNode();
        tableListNode.setMinWidth(200.0);
        tableListNode.setMinHeight(150.0);
        tableListNode.setPrefWidth(250.0);
        tableListNode.setPrefHeight(200.0);
        getDialogPane().setContent(new BorderPane(tableListNode, new Label("Choose the tables to concatenate"), null, getErrorLabel(), null));
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        getDialogPane().getStyleClass().add("table-list-dialog");
        
        setOnShowing(e -> {
            parent.enableTablePickingMode(lastScreenPos, getDialogPane().sceneProperty(), excludeTables, t -> {
                tableList.pickTableIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
        if (originalItems.isEmpty())
        {
            // runAfter to avoid focus stealing:
            FXUtility.runAfter(() -> tableList.addToEnd("", true));
        }
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, ImmutableList<TableId>> calculateResult()
    {
        ImmutableList.Builder<TableId> r = ImmutableList.builder();
        for (String item : tableList.getItems())
        {
            @Nullable @ExpressionIdentifier String s = IdentifierUtility.asExpressionIdentifier(item);
            if (s != null)
                r.add(new TableId(s));
            else
                return Either.left(TranslationUtility.getString("edit.column.invalid.table.name"));
        }
        return Either.right(r.build());
    }

    @OnThread(Tag.FXPlatform)
    private class TableList extends FancyList<String, PickTablePane>
    {
        public TableList(ImmutableList<TableId> originalItems)
        {
            super(Utility.mapListI(originalItems, t -> t.getRaw()), true, true, true);
            getStyleClass().add("table-list");
        }
        
        @Override
        protected Pair<PickTablePane, FXPlatformSupplier<String>> makeCellContent(Optional<String> original, boolean editImmediately)
        {
            if (!original.isPresent())
                original = Optional.of("");
            SimpleObjectProperty<String> curValue = new SimpleObjectProperty<>(original.get());
            PickTablePane pickTablePane = new PickTablePane(parent, excludeTables, original.get(), t -> {
                curValue.set(t.getId().getRaw());
                focusAddButton();
            });
            FXUtility.addChangeListenerPlatformNN(pickTablePane.currentlyEditing(), ed -> {
                if (ed)
                {
                    clearSelection();
                }
            });
            if (editImmediately)
            {
                FXUtility.runAfter(() -> pickTablePane.focusEntryField());
            }
            return new Pair<>(pickTablePane, curValue::get);
        }

        public void pickTableIfEditing(Table t)
        {
            // This is a bit of a hack.  The problem is that clicking the table removes the focus
            // from the edit field, so we can't just ask if the edit field is focused.  Tracking who
            // the focus transfers from/to seems a bit awkward, so we just use a time-based system,
            // where if they were very recently (200ms) editing a field, fill in that field with the table.
            // If they weren't recently editing a field, we append to the end of the list.
            long curTime = System.currentTimeMillis();
            PickTablePane curEditing = streamCells()
                .map(cell -> cell.getContent())
                .filter(p -> p.lastFocusedTime() > curTime - 200L).findFirst().orElse(null);
            if (curEditing != null)
            {
                curEditing.setContent(t);
                focusAddButton();
            }
            else
            {
                // Add to end:
                addToEnd(t.getId().getRaw(), false);
            }
        }
    }
}
