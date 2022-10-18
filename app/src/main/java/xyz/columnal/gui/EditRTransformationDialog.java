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
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.util.Duration;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.EditRTransformationDialog.RDetails;
import xyz.columnal.gui.View.Pick;
import xyz.columnal.rinterop.ConvertToR;
import xyz.columnal.rinterop.ConvertFromR;
import xyz.columnal.rinterop.ConvertFromR.TableType;
import xyz.columnal.transformations.RTransformation;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.Clickable;
import xyz.columnal.utility.gui.ErrorableLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.FancyList;
import xyz.columnal.utility.gui.FocusTracker;
import xyz.columnal.utility.gui.LabelledGrid;
import xyz.columnal.utility.gui.ScrollPaneFill;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@OnThread(Tag.FXPlatform)
public class EditRTransformationDialog extends ErrorableLightDialog<RDetails>
{
    private final View parent;
    private final RTransformation existing;
    private final TableList tableList;
    private final TextArea expressionTextArea;
    private final TextField packageField;
    private final FocusTracker focusTracker = new FocusTracker();

    public static class RDetails
    {
        public final ImmutableList<TableId> includedTables;
        public final ImmutableList<String> packages;
        public final String rExpression;

        public RDetails(ImmutableList<TableId> includedTables, ImmutableList<String> packages, String rExpression)
        {
            this.includedTables = includedTables;
            this.packages = packages;
            this.rExpression = rExpression;
        }
    }
    
    public EditRTransformationDialog(View parent, RTransformation existing, boolean selectWholeExpression)
    {
        super(parent, true);
        this.parent = parent;
        this.existing = existing;
        FXUtility.preventCloseOnEscape(getDialogPane());
        initModality(Modality.NONE);
        setResizable(true);
        getDialogPane().getStyleClass().add("edit-r");
        tableList = new TableList(existing.getInputTables());
        packageField = new TextField(existing.getPackagesToLoad().stream().collect(Collectors.joining(", ")));
        // Mainly for testing:
        packageField.getStyleClass().add("r-package-field");
        packageField.setPromptText("None");
        expressionTextArea = new TextArea(existing.getRExpression());
        // For some reason, this seems to produce a width similar to 70 chars:
        expressionTextArea.setPrefColumnCount(30);
        expressionTextArea.setPrefRowCount(6);
        Text text = new Text("Variables: <none>");
        TextFlow textFlow = new TextFlow(text);
        textFlow.setPrefWidth(100); // Stop it enlarging window
        Timeline refresh = new Timeline(new KeyFrame(Duration.millis(1000), e -> {
            Pair<ImmutableList<String>, ImmutableList<String>> vars = getAvailableVars();
            StyledString.Builder b = new StyledString.Builder();
            b.append(StyledString.s("Tables: ").withStyle(new StyledCSS("r-insertable-header")));
            for (int i = 0; i < vars.getFirst().size(); i++)
            {
                if (i != 0)
                    b.append(", ");
                final String content = vars.getFirst().get(i);
                b.append(StyledString.s(content).withStyle(new Clickable("edit.r.clickToInsert") {
                    @Override
                    protected void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {
                        FXUtility.mouse(EditRTransformationDialog.this).insertIntoExpression(content);
                    }
                }).withStyle(new StyledCSS("r-insertable-link")));
            }
            b.append(StyledString.s("\nColumns: ").withStyle(new StyledCSS("r-insertable-header")));
            for (int i = 0; i < vars.getSecond().size(); i++)
            {
                if (i != 0)
                    b.append(", ");
                final String content = vars.getSecond().get(i);
                b.append(StyledString.s(content).withStyle(new Clickable("edit.r.clickToInsert") {
                    @Override
                    protected void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {
                        FXUtility.mouse(EditRTransformationDialog.this).insertIntoExpression(content);
                    }
                }).withStyle(new StyledCSS("r-insertable-link")));
            }
            textFlow.getChildren().setAll(b.build().toGUI());
        }));
        refresh.setCycleCount(Animation.INDEFINITE);
        refresh.playFrom(Duration.millis(900));
        ScrollPaneFill scroll = new ScrollPaneFill(textFlow) {
            @Override
            public void requestFocus()
            {
                // Don't receive focus
            }
        };
        scroll.setMinHeight(75);
        getDialogPane().setContent(new LabelledGrid(
            LabelledGrid.labelledGridRow("edit.r.packages", "edit-r/packages", packageField),
            LabelledGrid.labelledGridRow("edit.r.srcTables", "edit-r/srctables", tableList.getNode()),
            LabelledGrid.labelledGridRow("edit.r.variables", "edit-r/variables", scroll),
            LabelledGrid.labelledGridRow("edit.r.expression", "edit-r/expression", expressionTextArea),
            LabelledGrid.fullWidthRow(getErrorLabel())
        ));
        focusTracker.addNode(expressionTextArea);
        setOnShowing(e -> {
            FXUtility.runAfter(() -> {
                expressionTextArea.requestFocus();
                if (selectWholeExpression)
                    expressionTextArea.selectAll();
                else
                    expressionTextArea.end();
            });
            parent.enableTableOrColumnPickingMode(null, getDialogPane().sceneProperty(), p -> {
                // Can't ever pick ourselves:
                if (existing.getId().equals(p.getFirst().getId()))
                    return Pick.NONE;
                else if (expressionTextArea.isFocused())
                    return Pick.COLUMN;
                else if (focusTracker.getRecentlyFocused() instanceof PickTablePane)
                    return Pick.TABLE;
                else
                    return Pick.NONE;
            }, p -> {
                Node focused = focusTracker.getRecentlyFocused();
                if (expressionTextArea.equals(focused))
                {
                    // Add table to sources if not already there:
                    if (!tableList.getItems().contains(p.getFirst().getId().getRaw()))
                    {
                        tableList.addToEnd(p.getFirst().getId().getRaw(), false);
                    }
                    
                    FXUtility.mouse(EditRTransformationDialog.this).insertIntoExpression(ConvertFromR.usToRTable(p.getFirst().getId()) + (p.getSecond() == null ? "" : "$" + ConvertToR.usToRColumn(p.getSecond(), TableType.TIBBLE, true)));
                }
                else if (focused instanceof PickTablePane)
                {
                    tableList.pickTableIfEditing(p.getFirst());
                }
            });
        });
        setOnHiding(e -> {
            refresh.stop();
            parent.disablePickingMode();
        });
    }
    
    private void insertIntoExpression(String content)
    {
        expressionTextArea.replaceSelection(content);
        FXUtility.runAfter(expressionTextArea::requestFocus);
    }
    
    // First is tables, second is columns
    @RequiresNonNull({"tableList", "parent"})
    private Pair<ImmutableList<String>, ImmutableList<String>> getAvailableVars(@UnknownInitialization(Object.class) EditRTransformationDialog this)
    {
        ImmutableList.Builder<String> tableVars = ImmutableList.builder();
        ImmutableList.Builder<String> columnVars = ImmutableList.builder();
        
        ImmutableList<String> items = tableList.getItems();
        for (String item : items)
        {
            @ExpressionIdentifier String ident = IdentifierUtility.asExpressionIdentifier(item);
            if (ident != null)
            {
                TableId tableId = new TableId(ident);
                tableVars.add(ConvertFromR.usToRTable(tableId));
                try
                {
                    RecordSet rs = parent.getManager().getSingleTableOrThrow(tableId).getData();
                    for (ColumnId columnId : rs.getColumnIds())
                    {
                        columnVars.add(ConvertFromR.usToRTable(tableId) + "$" + ConvertToR.usToRColumn(columnId, TableType.TIBBLE, true));
                    }
                }
                catch (InternalException | UserException e)
                {
                    // Just swallow it, but log if internal
                    if (e instanceof InternalException)
                        Log.log(e);
                }
            }
        }
        return new Pair<>(tableVars.build(), columnVars.build());
    }

    @Override
    @SuppressWarnings("i18n") // For now
    protected Either<@Localized String, RDetails> calculateResult()
    {
        ImmutableList.Builder<TableId> tables = ImmutableList.builder();
        for (String item : tableList.getItems())
        {
            @ExpressionIdentifier String t = IdentifierUtility.asExpressionIdentifier(item);
            if (t == null)
                return Either.<@Localized String, RDetails>left("Invalid table identifier: \"" + t + "\"");
            tables.add(new TableId(t));
        }
        String rExpression = expressionTextArea.getText().trim();
        if (rExpression.isEmpty())
            return Either.<@Localized String, RDetails>left("R expression cannot be blank");
        return Either.<@Localized String, RDetails>right(new RDetails(tables.build(), Arrays.stream(packageField.getText().split(",")).map(s -> s.trim()).filter(s -> !s.isEmpty()).collect(ImmutableList.<String>toImmutableList()), rExpression));
    }


    @OnThread(Tag.FXPlatform)
    private class TableList extends FancyList<@NonNull String, PickTablePane>
    {
        public TableList(ImmutableList<TableId> originalItems)
        {
            super(Utility.mapListI(originalItems, t -> t.getRaw()), true, true, true);
            getStyleClass().add("table-list");
            listenForCellChange(new FXPlatformConsumer<ListChangeListener.Change<? extends FancyList<@NonNull String, PickTablePane>.Cell>>() {
                @Override
                public void consume(ListChangeListener.Change<? extends FancyList<@NonNull String, PickTablePane>.Cell> c) {
                    while (c.next()) {
                        for (FancyList<@NonNull String, PickTablePane>.Cell cell : c.getAddedSubList()) {
                            focusTracker.addNode(cell.getContent());
                        }
                        for (FancyList<@NonNull String, PickTablePane>.Cell cell : c.getRemoved()) {
                            focusTracker.removeNode(cell.getContent());
                        }
                    }
                }
            });
        }

        @Override
        protected Pair<PickTablePane, FXPlatformSupplier<String>> makeCellContent(Optional<String> original, boolean editImmediately)
        {
            if (!original.isPresent())
                original = Optional.of("");
            SimpleObjectProperty<String> curValue = new SimpleObjectProperty<>(original.get());
            PickTablePane pickTablePane = new PickTablePane(parent.getManager(), ImmutableSet.of(existing), original.get(), t -> {
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
