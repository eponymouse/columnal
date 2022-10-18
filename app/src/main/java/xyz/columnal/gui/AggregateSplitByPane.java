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

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import xyz.columnal.gui.dialog.AutoComplete;
import xyz.columnal.gui.dialog.ColumnNameTextField;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Column;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.Table;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.dialog.AutoComplete.CompletionListener;
import xyz.columnal.gui.dialog.AutoComplete.SimpleCompletion;
import xyz.columnal.gui.dialog.AutoComplete.WhitespacePolicy;
import xyz.columnal.gui.SelectColumnDialog.SelectInfo;
import xyz.columnal.gui.lexeditor.ExpressionEditor.ColumnPicker;
import xyz.columnal.gui.recipe.ExpressionRecipe;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.transformations.function.comparison.Max;
import xyz.columnal.transformations.function.comparison.MaxIndex;
import xyz.columnal.transformations.function.comparison.Min;
import xyz.columnal.transformations.function.Sum;
import xyz.columnal.transformations.function.comparison.MinIndex;
import xyz.columnal.transformations.function.list.GetElement;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.function.fx.FXPlatformSupplierInt;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.DoubleOKLightDialog.Validity;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.FancyList;
import xyz.columnal.utility.gui.Instruction;
import xyz.columnal.utility.gui.LabelledGrid;
import xyz.columnal.utility.gui.TimedFocusable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class AggregateSplitByPane extends BorderPane
{
    private final @Nullable Table srcTable;
    private final SplitList splitList;
    
    public AggregateSplitByPane(@Nullable Table srcTable, ImmutableList<ColumnId> originalSplitBy, @Nullable Pair<ColumnId, ImmutableList<String>> example)
    {
        this.srcTable = srcTable;
        getStyleClass().add("split-by-pane");
        String header = "Calculate each expression\nonce per:";
        Label label = new Label(header);
        setTop(label);
        ToggleGroup toggleGroup = new ToggleGroup();
        LabelledGrid.Row wholeTableRow = LabelledGrid.radioGridRow("agg.split.whole.table", "split-by/whole-table", toggleGroup);
        LabelledGrid.Row splitByRow = LabelledGrid.radioGridRow("agg.split.columns", "split-by/by-columns", toggleGroup);
        this.splitList = new SplitList(originalSplitBy);
        splitList.getNode().setMinHeight(130.0);
        LabelledGrid grid = new LabelledGrid(
            wholeTableRow,
            splitByRow,
            LabelledGrid.labelOnlyRow(splitList.getNode())
        );
        setCenter(grid);
        /*
        String splitFooter = (example == null || example.getSecond().size() < 2 ? "" : "\n\nFor example, if column " + example.getFirst().getRaw() + " is selected , there will be one result for rows with value " + example.getSecond().stream().map(AggregateSplitByPane::truncate).collect(Collectors.joining(", one for rows with value ")) + ", etc");
        if (!splitFooter.isEmpty())
            setBottom(new TextFlow(new Text(splitFooter)));
        */
        FXUtility.addChangeListenerPlatformNN(toggleGroup.selectedToggleProperty(), toggle -> {
            if (Utility.indexOfRef(toggleGroup.getToggles(), toggle) == 0)
            {
                splitList.getNode().setVisible(false);
            }
            else
            {
                splitList.getNode().setVisible(true);
                if (splitList.getFields().isEmpty())
                {
                    splitList.addToEnd(null, true);
                }
            }
        });
        toggleGroup.selectToggle(toggleGroup.getToggles().get(originalSplitBy.isEmpty() ? 0 : 1));
    }

    private static String truncate(String orig)
    {
        if (orig.length() > 20)
            return orig.substring(0, 20) + "\u2026";
        else
            return orig;
    }

    public void pickColumnIfEditing(Pair<Table, ColumnId> t)
    {
        splitList.pickColumnIfEditing(t);
    }

    public @Nullable ImmutableList<ColumnId> getItems()
    {
        ImmutableList.Builder<ColumnId> r = ImmutableList.builder();
        for (Optional<ColumnId> item : splitList.getItems())
        {
            if (!item.isPresent())
                return null;
            r.add(item.get());
        }
        return r.build();
    }


    @OnThread(Tag.FXPlatform)
    private class SplitList extends FancyList<Optional<ColumnId>, ColumnPane>
    {
        public SplitList(ImmutableList<ColumnId> initialItems)
        {
            super(Utility.mapListI(initialItems, x -> Optional.of(x)), true, true, true);
            getStyleClass().add("split-list");
            setAddButtonText(TranslationUtility.getString("aggregate.add.column"));

            // We don't want to do this actually; whole table
            // calculation is a common wish:
            //if (initialItems.isEmpty())
            //    addToEnd(new ColumnId(""), true);
        }

        @Override
        protected Pair<ColumnPane, FXPlatformSupplier<Optional<ColumnId>>> makeCellContent(Optional<Optional<ColumnId>> initialContent, boolean editImmediately)
        {
            ColumnPane columnPane = new ColumnPane(initialContent.flatMap(Function.identity()).orElse(null), editImmediately);
            return new Pair<>(columnPane, () -> Optional.ofNullable(columnPane.currentValue().getValue()));
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
                focusAddButton();
            }
            else
            {
                // Add to end:
                addToEnd(Optional.of(t.getSecond()), false);
            }
        }

        public ImmutableList<ColumnNameTextField> getFields()
        {
            return streamCells().map(c -> c.getContent().columnField).collect(ImmutableList.<ColumnNameTextField>toImmutableList());
        }
    }

    @OnThread(Tag.FXPlatform)
    private class ColumnPane extends BorderPane
    {
        private final ColumnNameTextField columnField;
        private final AutoComplete autoComplete;
        private long lastEditTimeMillis = -1;

        public ColumnPane(@Nullable ColumnId initialContent, boolean editImmediately)
        {
            columnField = new ColumnNameTextField(initialContent);
            if (editImmediately)
                columnField.requestFocusWhenInScene();
            BorderPane.setMargin(columnField.getNode(), new Insets(0, 2, 2, 5));
            autoComplete = new AutoComplete<ColumnCompletion>(columnField.getFieldForComplete(),
                s -> {
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
                getListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM);
            FXUtility.addChangeListenerPlatformNN(columnField.focusedProperty(), focus -> {
                // Update whether focus is arriving or leaving:
                lastEditTimeMillis = System.currentTimeMillis();
            });
            Instruction instruction = new Instruction("pick.column.instruction");
            instruction.showAboveWhenFocused(columnField.getFieldForComplete());
            setCenter(columnField.getNode());
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
                public @Nullable String keyboardSelect(String textBefore, String textAfter, @Nullable ColumnCompletion selectedItem, boolean tabPressed)
                {
                    if (selectedItem != null)
                        return selectedItem.c.getName().getOutput();
                    else
                        return null;
                }
            };
        }

        public void setContent(ColumnId columnId)
        {
            autoComplete.setContentDirect(columnId.getRaw(), true);
        }

        public ObjectExpression<@Nullable ColumnId> currentValue()
        {
            return columnField.valueProperty();
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
    
    @OnThread(Tag.FXPlatform)
    private static class EditColumnSidePane implements EditColumnExpressionDialog.SidePane<ImmutableList<ColumnId>>
    {
        private final AggregateSplitByPane pane;
        private final @Nullable Table srcTable;

        public EditColumnSidePane(@Nullable Table srcTable, ImmutableList<ColumnId> initialSplitBy, @Nullable Pair<ColumnId, ImmutableList<String>> example)
        {
            this.srcTable = srcTable;
            this.pane = new AggregateSplitByPane(srcTable, initialSplitBy, example);
        }

        @Override
        public @Nullable Node getSidePane()
        {
            return pane;
        }

        @Override
        public @Nullable ImmutableList<ColumnId> calculateResult()
        {
            return pane.getItems();
        }

        @Override
        public Validity checkValidity()
        {
            return pane.getItems() == null ? Validity.IMPOSSIBLE_TO_SAVE : Validity.NO_ERRORS;
        }

        @Override
        public void showAllErrors()
        {
            // Nothing extra needed
        }

        @Override
        public ImmutableList<? extends TimedFocusable> getTimedFocusables()
        {
            return pane.splitList.getFields();
        }

        @Override
        public boolean includeColumn(TimedFocusable item, Pair<Table, ColumnId> column)
        {
            return Objects.equals(srcTable, column.getFirst());
        }

        @Override
        public void pickColumn(TimedFocusable item, Pair<Table, ColumnId> column)
        {
            pane.pickColumnIfEditing(column);
        }
    }


    public static EditColumnExpressionDialog<ImmutableList<ColumnId>> editColumn(View parent, @Nullable Table srcTable, @Nullable ColumnId initialName, @Nullable Expression initialExpression, Function<@Nullable ColumnId, ColumnLookup> makeColumnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType, ImmutableList<ColumnId> initialSplitBy)
    {
        ExpressionRecipe count = new ExpressionRecipe("expression.recipe.count") {
            @Override
            public @Nullable Expression makeExpression(Window parentWindow, ColumnPicker columnPicker)
            {
                return IdentExpression.load(TypeState.GROUP_COUNT);
            }
        };
        ExpressionRecipe sum = numberColumnRecipe("expression.recipe.sum", srcTable, c -> new CallExpression(FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), Sum.NAME, IdentExpression.column(c)));
        ExpressionRecipe min = dualColumnRecipe("expression.recipe.min", srcTable, directOrByIndex(parent, Min.NAME, MinIndex.NAME));
        ExpressionRecipe max = dualColumnRecipe("expression.recipe.max", srcTable, directOrByIndex(parent, Max.NAME, MaxIndex.NAME));
        
        EditColumnExpressionDialog<ImmutableList<ColumnId>> dialog = new EditColumnExpressionDialog<>(parent, srcTable, initialName, initialExpression, makeColumnLookup, makeTypeState, ImmutableList.of(count, sum, min, max), expectedType, new EditColumnSidePane(srcTable, initialSplitBy, null));
        dialog.addTopMessage("aggregate.header");
        return dialog;
    }

    private static BiFunction<ColumnId, ColumnId, Expression> directOrByIndex(View parent, String directFunctionName, String getIndexFunctionName)
    {
        return (main, show) -> {
            FunctionLookup functionLookup = FunctionList.getFunctionLookup(parent.getManager().getUnitManager());
            if (main.equals(show))
            {
                return new CallExpression(functionLookup, directFunctionName, IdentExpression.column(main));
            }
            else
            {
                return new CallExpression(functionLookup, GetElement.NAME, IdentExpression.column(show), new CallExpression(functionLookup, getIndexFunctionName, IdentExpression.column(main)));
            }
        };
    }

    private static ExpressionRecipe numberColumnRecipe(@LocalizableKey String nameKey, @Nullable Table srcTable, Function<ColumnId, Expression> makeExpression)
    {
        return new ExpressionRecipe(nameKey)
        {
            @Override
            public @Nullable Expression makeExpression(Window parentWindow, ColumnPicker columnPicker)
            {
                return new SelectColumnDialog(parentWindow, srcTable, columnPicker, ImmutableList.of(new SelectInfo("agg.recipe.pick.calc", (Column c) -> {
                    try
                    {
                        return DataTypeUtility.isNumber(c.getType().getType());
                    }
                    catch (InternalException | UserException e)
                    {
                        if (e instanceof InternalException)
                            Log.log(e);
                    }
                    return true;
                }))).showAndWait()
                    .map(c -> makeExpression.apply(c.get(0)))
                    .orElse(null);
            }
        };
    }

    private static ExpressionRecipe dualColumnRecipe(@LocalizableKey String nameKey, @Nullable Table srcTable, BiFunction<ColumnId, ColumnId, Expression> makeExpression)
    {
        return new ExpressionRecipe(nameKey)
        {
            @Override
            public @Nullable Expression makeExpression(Window parentWindow, ColumnPicker columnPicker)
            {
                return new SelectColumnDialog(parentWindow, srcTable, columnPicker, ImmutableList.of(
                        new SelectInfo("agg.recipe.pick.compare", "agg-recipe/calc-column", t -> true, false),
                        new SelectInfo("agg.recipe.pick.result", "agg-recipe/result-column", t -> true, true)
                    )
                ).showAndWait()
                    .map(c -> makeExpression.apply(c.get(0), c.get(1)))
                    .orElse(null);
            }
        };
    }
}
