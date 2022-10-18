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
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Window;
import xyz.columnal.gui.dialog.AutoComplete;
import xyz.columnal.gui.dialog.ColumnNameTextField;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.dialog.AutoComplete.Completion;
import xyz.columnal.gui.dialog.AutoComplete.CompletionCalculator;
import xyz.columnal.gui.dialog.AutoComplete.CompletionListener;
import xyz.columnal.gui.dialog.AutoComplete.WhitespacePolicy;
import xyz.columnal.gui.lexeditor.ExpressionEditor;
import xyz.columnal.gui.lexeditor.ExpressionEditor.ColumnPicker;
import xyz.columnal.gui.lexeditor.TopLevelEditor.Focus;
import xyz.columnal.gui.recipe.ExpressionRecipe;
import xyz.columnal.transformations.expression.BracketedStatus;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformSupplierInt;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.UnitType;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.DialogPaneWithSideButtons;
import xyz.columnal.utility.gui.DoubleOKLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.LabelledGrid;
import xyz.columnal.utility.gui.TimedFocusable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

// Edit column name and expression for that column
@OnThread(Tag.FXPlatform)
public class EditColumnExpressionDialog<T> extends DoubleOKLightDialog<EditColumnExpressionDialog<T>.Result>
{

    private final SimpleObjectProperty<ColumnLookup> curColumnLookup;

    public class Result
    {
        public final ColumnId columnId;
        public final Expression expression;
        public final T extra;

        public Result(ColumnId columnId, Expression expression, T extra)
        {
            this.columnId = columnId;
            this.expression = expression;
            this.extra = extra;
        }
    }
    
    @OnThread(Tag.FXPlatform)
    public interface SidePane<T>
    {
        public @Nullable Node getSidePane();
        
        public Validity checkValidity();

        public void showAllErrors();

        // null if not currently valid.
        public @Nullable T calculateResult();

        ImmutableList<? extends TimedFocusable> getTimedFocusables();

        boolean includeColumn(TimedFocusable item, Pair<Table, ColumnId> column);

        void pickColumn(TimedFocusable item, Pair<Table, ColumnId> column);
    }

    private final BorderPane mainContent;
    private final ExpressionEditor expressionEditor;
    private Expression curValue;
    private final ColumnNameTextField nameField;
    private final @Nullable RecipeBar recipeBar;
    private final SidePane<T> sidePane;
    private @Nullable SubPicker subPicker = null;
    private final @Nullable Node sidePaneNode;
    private final TypeManager typeManager;
    
    public EditColumnExpressionDialog(View parent, @Nullable Table srcTable, @Nullable ColumnId initialName, @Nullable Expression initialExpression, Function<@Nullable ColumnId, ColumnLookup> makeColumnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, ImmutableList<ExpressionRecipe> recipes, @Nullable DataType expectedType, SidePane<T> sidePane)
    {
        super(parent, new DialogPaneWithSideButtons());
        this.typeManager = parent.getManager().getTypeManager();
        this.sidePane = sidePane;
        setResizable(true);
        initModality(Modality.NONE);

        curColumnLookup = new SimpleObjectProperty<>(makeColumnLookup.apply(initialName));

        nameField = new ColumnNameTextField(initialName);
        FXUtility.addChangeListenerPlatform(nameField.valueProperty(), v -> {
            notifyModified();
            curColumnLookup.set(makeColumnLookup.apply(v));
        });
        if (srcTable != null)
        {
            try
            {
                List<ColumnId> columnIds = srcTable.getData().getColumnIds();
                AutoComplete<ColumnCompletion> autoComplete = new AutoComplete<ColumnCompletion>(nameField.getFieldForComplete(), new CompletionCalculator<ColumnCompletion>()
                {
                    boolean contentHasChanged = false;
                    @Override
                    public Stream<ColumnCompletion> calculateCompletions(String textFieldContent) throws InternalException, UserException
                    {
                        if (!contentHasChanged && !textFieldContent.equals(initialName == null ? "" : initialName.getRaw()))
                            contentHasChanged = true;
                        // Show nothing until content has changed, otherwise it's really annoying to have to click the autocomplete away
                        // before being able to focus the expression.
                        if (contentHasChanged)
                            return columnIds.stream().filter(c -> Utility.startsWithIgnoreCase(c.getRaw(), textFieldContent)).map(ColumnCompletion::new);
                        else
                            return Stream.of();
                    }
                }, new CompletionListener<ColumnCompletion>()
                {
                    @Override
                    @OnThread(Tag.FXPlatform)
                    public @Nullable String doubleClick(String currentText, ColumnCompletion selectedItem)
                    {
                        return selectedItem.columnId.getRaw();
                    }

                    @Override
                    @OnThread(Tag.FXPlatform)
                    public @Nullable String keyboardSelect(String textBeforeCaret, String textAfterCaret, @Nullable ColumnCompletion selectedItem, boolean wasTab)
                    {
                        FXUtility.keyboard(EditColumnExpressionDialog.this).expressionEditor.focus(Focus.LEFT);
                        return selectedItem != null ? selectedItem.columnId.getRaw() : textBeforeCaret + textAfterCaret;
                    }
                }, WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM);
            }
            catch (InternalException | UserException e)
            {
                if (e instanceof InternalException)
                    Log.log(e);
            }
        }
        
        ReadOnlyObjectWrapper<@Nullable Table> srcTableWrapper = new ReadOnlyObjectWrapper<@Nullable Table>(srcTable);
        // We let ExpressionEditor call these methods, and piggy-back on them:
        ColumnPicker columnPicker = new ColumnPicker()
        {
            @Override
            public void enableColumnPickingMode(@Nullable Point2D screenPos, ObjectExpression<@PolyNull Scene> sceneProperty, Predicate<Pair<Table, ColumnId>> expEdIncludeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> expEdOnPick)
            {
                parent.enableColumnPickingMode(screenPos, getDialogPane().sceneProperty(), tc -> {
                    @Nullable TimedFocusable item = getRecentlyFocused();
                    if (subPicker != null)
                    {
                        return subPicker.includeColumn.test(tc);
                    }
                    else if (expressionEditor == item)
                    {
                        return expEdIncludeColumn.test(tc);
                    }
                    else if (nameField == item)
                    {
                        try
                        {
                            return srcTable != null && srcTable.getData().getColumnIds().contains(tc.getSecond());
                        }
                        catch (UserException | InternalException e)
                        {
                            return false;
                        }
                    }
                    else if (item != null)
                    {
                        return sidePane.includeColumn(item, tc);
                    }
                    else
                    {
                        return false;
                    }
                }, tc -> {
                    // If they recently clicked a completion, ignore this click:
                    if (expressionEditor != null && expressionEditor.isMouseClickImmune())
                        return;
                    
                    @Nullable TimedFocusable item = getRecentlyFocused();
                    
                    if (subPicker != null)
                    {
                        subPicker.onPick.consume(tc);
                    }
                    else if (item == expressionEditor)
                    {
                        expEdOnPick.consume(tc);
                    }
                    else if (item == nameField)
                    {
                        nameField.setText(tc.getSecond().getRaw());
                    }
                    else if (item != null)
                    {
                        sidePane.pickColumn(item, tc);
                    }
                });
            }

            @OnThread(Tag.FXPlatform)
            @Nullable
            private TimedFocusable getRecentlyFocused()
            {
                return TimedFocusable.getRecentlyFocused(Stream.<TimedFocusable>concat(Stream.<TimedFocusable>of(FXUtility.mouse(EditColumnExpressionDialog.this).expressionEditor, nameField), sidePane.getTimedFocusables().stream()).toArray(TimedFocusable[]::new));
            }

            @Override
            public void disablePickingMode()
            {
                parent.disablePickingMode();
            }
        };
        expressionEditor = new ExpressionEditor(initialExpression, srcTableWrapper, curColumnLookup, expectedType, columnPicker, parent.getManager().getTypeManager(), makeTypeState, FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), e -> {
            curValue = e;
            @Nullable RecipeBar recipeBar = Utility.later(this).recipeBar;
            if (recipeBar != null)
                recipeBar.update(e);
            notifyModified();
        }) {
            @Override
            protected void parentFocusRightOfThis(Either<Focus, Integer> side, boolean becauseOfTab)
            {
                @Nullable Node button = getDialogPane().lookupButton(ButtonType.OK);
                if (button != null && becauseOfTab)
                    button.requestFocus();
            }

            @Override
            @SuppressWarnings("override.receiver") // Mainly because awkward to refer to our own type
            protected Dimension2D getEditorDimension()
            {
                if (sidePaneNode == null)
                    return super.getEditorDimension();
                else
                    return new Dimension2D(300.0, 130.0);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            protected @Nullable Pair<@Nullable ColumnId, Expression> forceCloseDialog()
            {
                Pair<@Nullable ColumnId, Expression> result = new Pair<>(nameField.valueProperty().get(), Utility.later(EditColumnExpressionDialog.this).expressionEditor.save(true));
                setResult(null);
                close();
                return result;
            }
        };
        curValue = expressionEditor.save(true);
        // Tab doesn't seem to work right by itself:
        nameField.getNode().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB)
            {
                expressionEditor.focus(Focus.LEFT);
                e.consume();
            }
        });
        
        LabelledGrid content = new LabelledGrid();
        content.getStyleClass().add("edit-column-expression-content");

        content.addRow(LabelledGrid.labelledGridRow("edit.column.name", "edit-column/column-name", nameField.getNode()));
        
        if (!recipes.isEmpty())
        {
            recipeBar = new RecipeBar(recipes);
            content.addRow(LabelledGrid.contentOnlyRow(recipeBar));
        }
        else
            recipeBar = null;
        
        content.addRow(LabelledGrid.labelledGridRow("edit.column.expression",
                "edit-column/column-expression", expressionEditor.getContainer()));

        sidePaneNode = sidePane.getSidePane();
        if (sidePaneNode != null)
            BorderPane.setMargin(sidePaneNode, new Insets(0, 10, 0, 0));
        mainContent = new BorderPane(content);
        BorderPane borderPane = new BorderPane(mainContent, null, null, null, sidePaneNode);
        borderPane.getStyleClass().add("edit-column-expression-root");
        getDialogPane().setContent(borderPane);

        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("cancel-button");
        // Prevent enter/escape activating buttons:
        ((Button)getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
        FXUtility.preventCloseOnEscape(getDialogPane());
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(nameField::requestFocusWhenInScene);
        });
        setOnHiding(e -> {
            expressionEditor.cleanup();
        });
        //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
    }

    public static EditColumnExpressionDialog<UnitType> withoutSidePane(View parent, @Nullable Table srcTable, @Nullable ColumnId initialName, @Nullable Expression initialExpression, Function<@Nullable ColumnId, ColumnLookup> makeColumnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType)
    {
        return new EditColumnExpressionDialog<>(parent, srcTable, initialName, initialExpression, makeColumnLookup, makeTypeState, ImmutableList.of(), expectedType, new SidePane<UnitType>()
        {
            @Override
            public @Nullable Node getSidePane()
            {
                return null;
            }

            @Override
            public Validity checkValidity()
            {
                return Validity.NO_ERRORS;
            }

            @Override
            public void showAllErrors()
            {
            }

            @Override
            public @Nullable UnitType calculateResult()
            {
                return UnitType.UNIT;
            }

            @Override
            public ImmutableList<? extends TimedFocusable> getTimedFocusables()
            {
                return ImmutableList.of();
            }

            @Override
            public boolean includeColumn(TimedFocusable item, Pair<Table, ColumnId> column)
            {
                return false;
            }

            @Override
            public void pickColumn(TimedFocusable item, Pair<Table, ColumnId> column)
            {
            }
        });
    }

    public Optional<Result> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, 400, 200);
    }
    
    public void addTopMessage(@LocalizableKey String topMessage)
    {
        TextFlow display = GUI.textFlowKey(topMessage, "edit-column-top-message");
        display.setMaxWidth(9999.0);
        mainContent.setTop(display);
        BorderPane.setMargin(mainContent.getCenter(), new Insets(10, 0, 0, 0));
    }

    @Override
    protected Validity checkValidity()
    {
        expressionEditor.save(true);
        if (nameField.valueProperty().getValue() == null)
            return Validity.IMPOSSIBLE_TO_SAVE;
        else if (expressionEditor.hasErrors())
            return Validity.ERROR_BUT_CAN_SAVE;
        else
            return sidePane.checkValidity();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    protected @Nullable Result calculateResult()
    {
        @Nullable ColumnId name = nameField.valueProperty().getValue();
        @Nullable T t = sidePane.calculateResult();
        if (name == null || t == null)
            return null;
        else
            return new Result(name, curValue, t);
    }

    @Override
    protected void showAllErrors()
    {
        expressionEditor.showAllErrors();
        sidePane.showAllErrors();
    }

    private static class ColumnCompletion extends Completion
    {
        private final ColumnId columnId;

        public ColumnCompletion(ColumnId columnId)
        {
            this.columnId = columnId;
        }
        
        @Override
        public CompletionContent makeDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(columnId.getRaw(), null);
        }
    }

    @OnThread(Tag.FXPlatform)
    private final class RecipeBar extends FlowPane
    {
        public RecipeBar(ImmutableList<ExpressionRecipe> recipes)
        {
            setHgap(12);
            for (ExpressionRecipe recipe : recipes)
            {
                getChildren().add(GUI.buttonLocal(recipe.getTitle(), () -> {
                    @SuppressWarnings("nullness")
                    @NonNull Window window = getScene().getWindow();
                    Expression expression = recipe.makeExpression(window, makeColumnPicker());
                    if (expression != null)
                        expressionEditor.setContent(expression.save(SaveDestination.toExpressionEditor(typeManager, curColumnLookup.get(), FunctionList.getFunctionLookup(typeManager.getUnitManager())), BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY));
                }, "recipe-button"));
            }
        }
        
        public void update(Expression curContent)
        {
            boolean empty = curContent.save(SaveDestination.toExpressionEditor(typeManager, curColumnLookup.get(), FunctionList.getFunctionLookup(typeManager.getUnitManager())), BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY).trim().isEmpty();
            setVisible(empty);
        }
        
        private ColumnPicker makeColumnPicker()
        {
            return new ColumnPicker()
            {
                @Override
                public void enableColumnPickingMode(@Nullable Point2D screenPos, ObjectExpression<@PolyNull Scene> sceneProperty, Predicate<Pair<Table, ColumnId>> includeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> onPick)
                {
                    // Column picking already enabled, just need to register ourselves:
                    subPicker = new SubPicker(includeColumn, onPick);
                }

                @Override
                public void disablePickingMode()
                {
                    subPicker = null;
                }
            };
        }
    }
    
    private static class SubPicker
    {
        private final Predicate<Pair<Table, ColumnId>> includeColumn;   
        private final FXPlatformConsumer<Pair<Table, ColumnId>> onPick;

        public SubPicker(Predicate<Pair<Table, ColumnId>> includeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> onPick)
        {
            this.includeColumn = includeColumn;
            this.onPick = onPick;
        }
    }
}
