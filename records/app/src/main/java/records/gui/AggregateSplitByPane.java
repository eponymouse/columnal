package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.AutoComplete.CompletionListener;
import records.gui.AutoComplete.SimpleCompletion;
import records.gui.AutoComplete.WhitespacePolicy;
import records.gui.SelectColumnDialog.SelectInfo;
import records.gui.lexeditor.ExpressionEditor.ColumnPicker;
import records.gui.recipe.ExpressionRecipe;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.TypeState;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.function.FunctionList;
import records.transformations.function.comparison.Max;
import records.transformations.function.comparison.MaxIndex;
import records.transformations.function.comparison.Min;
import records.transformations.function.Sum;
import records.transformations.function.comparison.MinIndex;
import records.transformations.function.list.GetElement;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.TranslationUtility;
import utility.Utility;
import utility.gui.DoubleOKLightDialog.Validity;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.GUI;
import utility.gui.Instruction;
import utility.gui.LabelledGrid;
import utility.gui.TimedFocusable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
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
        LabelledGrid.Row wholeTableRow = GUI.radioGridRow("agg.split.whole.table", "split-by/whole-table", toggleGroup);
        LabelledGrid.Row splitByRow = GUI.radioGridRow("agg.split.columns", "split-by/by-columns", toggleGroup);
        this.splitList = new SplitList(originalSplitBy);
        splitList.getNode().setMinHeight(130.0);
        LabelledGrid grid = new LabelledGrid(
            wholeTableRow,
            splitByRow,
            new LabelledGrid.Row(splitList.getNode())
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
        protected Pair<ColumnPane, FXPlatformSupplier<Optional<ColumnId>>> makeCellContent(@Nullable Optional<ColumnId> initialContent, boolean editImmediately)
        {
            ColumnPane columnPane = new ColumnPane(initialContent == null ? null : initialContent.orElse(null), editImmediately);
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
                if (addButton != null)
                    addButton.requestFocus();
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
                return new IdentExpression(TypeState.GROUP_COUNT);
            }
        };
        ExpressionRecipe sum = numberColumnRecipe("expression.recipe.sum", srcTable, c -> new CallExpression(FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), Sum.NAME, c));
        ExpressionRecipe min = dualColumnRecipe("expression.recipe.min", srcTable, directOrByIndex(parent, Min.NAME, MinIndex.NAME));
        ExpressionRecipe max = dualColumnRecipe("expression.recipe.max", srcTable, directOrByIndex(parent, Max.NAME, MaxIndex.NAME));
        
        EditColumnExpressionDialog<ImmutableList<ColumnId>> dialog = new EditColumnExpressionDialog<>(parent, srcTable, initialName, initialExpression, makeColumnLookup, makeTypeState, ImmutableList.of(count, sum, min, max), expectedType, new EditColumnSidePane(srcTable, initialSplitBy, null));
        dialog.addTopMessage("aggregate.header");
        return dialog;
    }

    private static BiFunction<ColumnReference, ColumnReference, Expression> directOrByIndex(View parent, String directFunctionName, String getIndexFunctionName)
    {
        return (main, show) -> {
            FunctionLookup functionLookup = FunctionList.getFunctionLookup(parent.getManager().getUnitManager());
            if (main.equals(show))
            {
                return new CallExpression(functionLookup, directFunctionName, main);
            }
            else
            {
                return new CallExpression(functionLookup, GetElement.NAME, show, new CallExpression(functionLookup, getIndexFunctionName, main));
            }
        };
    }

    private static ExpressionRecipe numberColumnRecipe(@LocalizableKey String nameKey, @Nullable Table srcTable, Function<ColumnReference, Expression> makeExpression)
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
                    .map(c -> makeExpression.apply(new ColumnReference(null, c.get(0), ColumnReferenceType.CORRESPONDING_ROW)))
                    .orElse(null);
            }
        };
    }

    private static ExpressionRecipe dualColumnRecipe(@LocalizableKey String nameKey, @Nullable Table srcTable, BiFunction<ColumnReference, ColumnReference, Expression> makeExpression)
    {
        return new ExpressionRecipe(nameKey)
        {
            @Override
            public @Nullable Expression makeExpression(Window parentWindow, ColumnPicker columnPicker)
            {
                return new SelectColumnDialog(parentWindow, srcTable, columnPicker, ImmutableList.of(
                        new SelectInfo("agg.recipe.pick.calc", "agg-recipe/calc-column", t -> true, false),
                        new SelectInfo("agg.recipe.pick.result", "agg-recipe/result-column", t -> true, true)
                    )
                ).showAndWait()
                    .map(c -> makeExpression.apply(new ColumnReference(null, c.get(0), ColumnReferenceType.CORRESPONDING_ROW), new ColumnReference(null, c.get(1), ColumnReferenceType.CORRESPONDING_ROW)))
                    .orElse(null);
            }
        };
    }
}
