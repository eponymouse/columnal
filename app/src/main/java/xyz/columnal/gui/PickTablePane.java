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
import com.google.common.collect.ImmutableSet;
import javafx.beans.binding.BooleanExpression;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.gui.AutoComplete.CompletionListener;
import xyz.columnal.gui.AutoComplete.SimpleCompletion;
import xyz.columnal.gui.AutoComplete.WhitespacePolicy;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.Instruction;
import xyz.columnal.utility.gui.TimedFocusable;

import java.util.List;

@OnThread(Tag.FXPlatform)
public class PickTablePane extends BorderPane implements TimedFocusable
{
    private final TextField tableField = new TextField();
    private final AutoComplete autoComplete;
    private final FXPlatformConsumer<Table> setResultAndClose;
    private long lastEditTimeMillis = -1;
    private final Instruction instruction;

    public PickTablePane(View view, ImmutableSet<Table> exclude, String initial, FXPlatformConsumer<Table> setResultAndFinishEditing)
    {
        this.setResultAndClose = setResultAndFinishEditing;
        tableField.setText(initial);
        autoComplete = new AutoComplete<TableCompletion>(tableField,
            s -> view.getManager().getAllTables().stream().filter(t -> !exclude.contains(t) && t.getId().getOutput().contains(s)).map(TableCompletion::new),
            getListener(view.getManager().getAllTables()), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM);
        
        setCenter(tableField);
        instruction = new Instruction("pick.table.instruction");
        instruction.showAboveWhenFocused(tableField);
        setMargin(tableField, new Insets(4, 4, 4, 4));
        
        FXUtility.addChangeListenerPlatformNN(tableField.focusedProperty(), focus -> {
            // Update whether focus is arriving or leaving:
            lastEditTimeMillis = System.currentTimeMillis();
        });
        getStyleClass().add("pick-table-pane");
    }

    public void focusEntryField()
    {
        tableField.requestFocus();
    }

    @RequiresNonNull("setResultAndClose")
    private CompletionListener<TableCompletion> getListener(@UnknownInitialization(BorderPane.class) PickTablePane this, List<Table> tables)
    {
        @NonNull FXPlatformConsumer<Table> setResultAndCloseFinal = setResultAndClose;
        return new CompletionListener<TableCompletion>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public String doubleClick(String currentText, TableCompletion selectedItem)
            {
                return complete(selectedItem.t);
            }

            @OnThread(Tag.FXPlatform)
            protected String complete(Table t)
            {
                setResultAndCloseFinal.consume(t);
                return t.getId().getOutput();
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public @Nullable String keyboardSelect(String textBefore, String textAfter, @Nullable TableCompletion selectedItem, boolean wasTab)
            {
                if (selectedItem != null)
                    return complete(selectedItem.t);
                else
                {
                    Table t = tables.stream().filter(table -> table.getId().getRaw().equals(textBefore + textAfter)).findFirst().orElse(null);
                    if (t != null)
                        return complete(t);
                }
                return null;
            }
        };
    }

    public void setContent(@Nullable Table table)
    {
        autoComplete.setContentDirect(table == null ? "" : table.getId().getRaw(), true);
        if (table != null)
            setResultAndClose.consume(table);
    }
    
    public @Nullable TableId getValue()
    {
        @ExpressionIdentifier String valid = IdentifierUtility.asExpressionIdentifier(tableField.getText());
        if (valid == null)
            return null;
        else
            return new TableId(valid);
    }

    public BooleanExpression currentlyEditing()
    {
        return tableField.focusedProperty();
    }
    
    @Override
    @OnThread(Tag.FXPlatform)
    public long lastFocusedTime()
    {
        return tableField.isFocused() ? System.currentTimeMillis() : lastEditTimeMillis;
    }

    public void setFieldPrefWidth(double width)
    {
        tableField.setPrefWidth(width);
    }

    private static class TableCompletion extends SimpleCompletion
    {
        private final Table t;

        public TableCompletion(Table t)
        {
            super(t.getId().getRaw(), null);
            this.t = t;
        }
    }
}
