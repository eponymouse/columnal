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
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.gui.View.Pick;
import xyz.columnal.transformations.Join;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.ErrorableLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.FancyList;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.LabelledGrid;
import xyz.columnal.utility.gui.LabelledGrid.Row;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Dialog to edit join.  Allows you to edit the two source tables,
 * a tickbox whether to do left join or inner join, and pairs
 * of columns on which to join.
 */
@OnThread(Tag.FXPlatform)
public final class EditJoinDialog extends ErrorableLightDialog<EditJoinDialog.JoinDetails>
{
    private final PickTablePane primaryTableNamePane;
    private final PickTablePane secondaryTableNamePane;
    private final CheckBox isLeftJoin;
    private final ColumnList joinOn;
    
    public EditJoinDialog(View parent, Join join)
    {
        super(parent, true);
        FXUtility.preventCloseOnEscape(getDialogPane());
        initModality(Modality.NONE);
        setResizable(true);
        
        Pair<CheckBox, Row> leftJoinRow = LabelledGrid.tickGridRow("join.isLeftJoin", "edit-join/left-join", new Label());
        isLeftJoin = leftJoinRow.getFirst();
        isLeftJoin.setSelected(join.isKeepPrimaryWithNoMatch());
        joinOn = new ColumnList((ImmutableList<Pair<@Nullable ColumnId, @Nullable ColumnId>>)join.getColumnsToMatch());
        ImmutableSet<Table> exclude = ImmutableSet.of(join); 
        secondaryTableNamePane = new PickTablePane(parent.getManager(), exclude, join.getSecondarySource().getRaw(), t -> {
            joinOn.focusAddButton();
        });
        primaryTableNamePane = new PickTablePane(parent.getManager(), exclude, join.getPrimarySource().getRaw(), t -> {
            secondaryTableNamePane.focusEntryField();
        });
        
        getDialogPane().setPrefWidth(650.0);
        getDialogPane().setContent(setBorderPaneGaps(10, GUI.borderTopCenterBottom(GUI.borderTopCenterBottom(
                GUI.labelWrapHelp("join.edit.explanation", "edit-join/tables"),
                GUI.borderLeftRight(
                    GUI.borderTopCenter(GUI.label("join.edit.table.primary", "table-label"), primaryTableNamePane),
                    GUI.borderTopCenter(GUI.label("join.edit.table.secondary", "table-label"), secondaryTableNamePane)),
                GUI.labelWrapHelp("join.edit.columns", "edit-join/columns"), "edit-join-top"),
                joinOn.getNode(),
                new LabelledGrid(leftJoinRow.getSecond(), LabelledGrid.fullWidthRow(getErrorLabel())),
        "edit-join-content")));
        FXUtility.onceNotNull(primaryTableNamePane.sceneProperty(), s -> FXUtility.runAfter(() -> primaryTableNamePane.focusEntryField()));
        
        setOnShowing(e -> {
            parent.enableTableOrColumnPickingMode(null, getDialogPane().sceneProperty(), p -> {
                Either<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>> pick = pick();
                if (pick == null || p.getFirst() == join)
                    return Pick.NONE;
                else
                    return pick.either(t -> Pick.TABLE, c -> p.getSecond() != null && p.getFirst().getId().equals(c.getFirst()) ? Pick.COLUMN : Pick.NONE);
            }, p -> {
                Either<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>> pick = pick();
                if (pick != null)
                {
                    pick.either_(t -> t.consume(p.getFirst()), c -> {
                        if (p.getFirst().getId().equals(c.getFirst()) && p.getSecond() != null)
                            c.getSecond().consume(p.getSecond());
                    });
                }
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
    }

    private BorderPane setBorderPaneGaps(int pixels, BorderPane pane)
    {
        for (Node child : pane.getChildren())
        {
            if (child != pane.getBottom())
                BorderPane.setMargin(child, new Insets(0, 0, pixels, 0));
            
            if (child instanceof BorderPane)
            {
                setBorderPaneGaps(pixels, (BorderPane) child);
            }
        }
        return pane;
    }

    private @Nullable Either<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>> pick()
    {
        long curTime = System.currentTimeMillis();
        return Stream.<Pair<Long, Either<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>>>>concat(Stream.<Pair<Long, Either<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>>>>of(
                new Pair<>(primaryTableNamePane.lastFocusedTime(), Either.<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>>left(t -> primaryTableNamePane.setContent(t))),
                new Pair<>(secondaryTableNamePane.lastFocusedTime(), Either.<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>>left(t -> secondaryTableNamePane.setContent(t)))
            ),
            joinOn.pick(primaryTableNamePane.getValue(), secondaryTableNamePane.getValue())
        ).filter(p -> p.getFirst() > curTime - 500).sorted(Pair.<Long, Either<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>>>comparatorFirst()).findFirst().map(p -> p.getSecond()).orElse(null);
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, JoinDetails> calculateResult()
    {
        TableId prim = this.primaryTableNamePane.getValue();
        TableId sec = this.secondaryTableNamePane.getValue();
        if (prim == null || sec == null)
            return Either.left(TranslationUtility.getString("join.invalid.table.names"));
        ImmutableList.Builder<Pair<ColumnId, ColumnId>> joinColumns = ImmutableList.builder();
        for (Pair<@Nullable ColumnId, @Nullable ColumnId> item : joinOn.getItems())
        {
            if (item.getFirst() == null || item.getSecond() == null)
                return Either.left(TranslationUtility.getString("join.invalid.column.names"));
            joinColumns.add(new Pair<>(item.getFirst(), item.getSecond()));
        }
        return Either.right(new JoinDetails(prim, sec, isLeftJoin.isSelected(), joinColumns.build()));
    }

    private class ColumnList extends FancyList<Pair<@Nullable ColumnId, @Nullable ColumnId>, DualColumnPane>
    {

        public ColumnList(ImmutableList<Pair<@Nullable ColumnId, @Nullable ColumnId>> initialItems)
        {
            super(initialItems, true, false, true);
        }

        @Override
        protected @OnThread(Tag.FXPlatform) Pair<DualColumnPane, FXPlatformSupplier<Pair<@Nullable ColumnId, @Nullable ColumnId>>> makeCellContent(Optional< Pair<@Nullable ColumnId, @Nullable ColumnId>> initialContent, boolean editImmediately)
        {
            DualColumnPane dualColumnPane = new DualColumnPane(initialContent.orElse(null));
            if (editImmediately)
                FXUtility.onceNotNull(dualColumnPane.sceneProperty(), s -> FXUtility.runAfter(dualColumnPane.primaryColumn::requestFocus));
            return new Pair<DualColumnPane, FXPlatformSupplier<Pair<@Nullable ColumnId, @Nullable ColumnId>>>(dualColumnPane, dualColumnPane::getValue);
        }

        @OnThread(Tag.FXPlatform)
        public Stream<Pair<Long, Either<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>>>> pick(@Nullable TableId primaryTable, @Nullable TableId secondaryTable)
        {
            return streamCells().flatMap(cell -> cell.getContent().pick(primaryTable, secondaryTable));
        }
    }
    
    @OnThread(Tag.FXPlatform)
    private final class DualColumnPane extends BorderPane
    {
        private final TextField primaryColumn;
        private final TextField secondaryColumn;
        private long lastPrimaryEditTime = 0;
        private long lastSecondaryEditTime = 0;

        public DualColumnPane(@Nullable Pair<@Nullable ColumnId, @Nullable ColumnId> initialContent)
        {
            this.primaryColumn = new TextField();
            this.secondaryColumn = new TextField();
            if (initialContent != null && initialContent.getFirst() != null)
                primaryColumn.setText(initialContent.getFirst().getRaw());
            if (initialContent != null && initialContent.getSecond() != null)
                secondaryColumn.setText(initialContent.getSecond().getRaw());
            BorderPane.setMargin(this, new Insets(1, 5, 3, 2));
            setLeft(primaryColumn);
            setCenter(new Label(" = "));
            setRight(secondaryColumn);
            FXUtility.addChangeListenerPlatformNN(primaryColumn.focusedProperty(), f -> {
                if (!f)
                    lastPrimaryEditTime = System.currentTimeMillis();
            });
            FXUtility.addChangeListenerPlatformNN(secondaryColumn.focusedProperty(), f -> {
                if (!f)
                    lastSecondaryEditTime = System.currentTimeMillis();
            });
        }

        public Pair<@Nullable ColumnId, @Nullable ColumnId> getValue()
        {
            @ExpressionIdentifier String primaryIdent = IdentifierUtility.asExpressionIdentifier(primaryColumn.getText());
            @ExpressionIdentifier String secondaryIdent = IdentifierUtility.asExpressionIdentifier(secondaryColumn.getText());
            return new Pair<>(primaryIdent == null ? null : new ColumnId(primaryIdent), secondaryIdent == null ? null : new ColumnId(secondaryIdent));
        }

        public Stream<Pair<Long, Either<FXPlatformConsumer<Table>, Pair<TableId, FXPlatformConsumer<ColumnId>>>>> pick(@Nullable TableId primaryTable, @Nullable TableId secondaryTable)
        {
            return Utility.streamNullable(
                primaryTable == null ? null : new Pair<>(primaryColumn.isFocused() ? System.currentTimeMillis() : lastPrimaryEditTime, Either.right(new Pair<TableId, FXPlatformConsumer<ColumnId>>(primaryTable, c -> primaryColumn.setText(c.getRaw())))),
                secondaryTable == null ? null : new Pair<>(secondaryColumn.isFocused() ? System.currentTimeMillis() : lastSecondaryEditTime, Either.right(new Pair<TableId, FXPlatformConsumer<ColumnId>>(secondaryTable, c -> secondaryColumn.setText(c.getRaw()))))
            );
        }
    }
    
    public static class JoinDetails
    {
        public final TableId primaryTableId;
        public final TableId secondaryTableId;
        public final boolean isLeftJoin;
        public final ImmutableList<Pair<ColumnId, ColumnId>> joinOn;

        public JoinDetails(TableId primaryTableId, TableId secondaryTableId, boolean isLeftJoin, ImmutableList<Pair<ColumnId, ColumnId>> joinOn)
        {
            this.primaryTableId = primaryTableId;
            this.secondaryTableId = secondaryTableId;
            this.isLeftJoin = isLeftJoin;
            this.joinOn = joinOn;
        }
    }
}
