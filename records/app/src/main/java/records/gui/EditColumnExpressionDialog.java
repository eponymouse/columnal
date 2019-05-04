package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.lexeditor.ExpressionEditor;
import records.gui.lexeditor.ExpressionEditor.ColumnPicker;
import records.gui.lexeditor.TopLevelEditor.Focus;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.DoubleOKLightDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LightDialog;
import utility.gui.TimedFocusable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

// Edit column name and expression for that column
@OnThread(Tag.FXPlatform)
public class EditColumnExpressionDialog extends DoubleOKLightDialog<Pair<ColumnId, Expression>>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;
    private final ColumnNameTextField nameField;

    public EditColumnExpressionDialog(View parent, @Nullable Table srcTable, @Nullable ColumnId initialName, @Nullable Expression initialExpression, Function<@Nullable ColumnId, ColumnLookup> makeColumnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType)
    {
        super(parent, new DialogPaneWithSideButtons());
        setResizable(true);
        initModality(Modality.NONE);
        
        SimpleObjectProperty<ColumnLookup> curColumnLookup = new SimpleObjectProperty<>(makeColumnLookup.apply(initialName));

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
                AutoComplete<ColumnCompletion> autoComplete = new AutoComplete<ColumnCompletion>(nameField.getFieldForComplete(), s -> columnIds.stream().map(ColumnCompletion::new), new CompletionListener<ColumnCompletion>()
                {
                    @Override
                    public @Nullable String doubleClick(String currentText, ColumnCompletion selectedItem)
                    {
                        return selectedItem.columnId.getRaw();
                    }

                    @Override
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
            public void enableColumnPickingMode(@Nullable Point2D screenPos, Predicate<Pair<Table, ColumnId>> expEdIncludeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> expEdOnPick)
            {
                parent.enableColumnPickingMode(screenPos, tc -> {
                    @Nullable TimedFocusable item = TimedFocusable.getRecentlyFocused(FXUtility.mouse(EditColumnExpressionDialog.this).expressionEditor, nameField);
                    if (expressionEditor == item)
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
                    else
                    {
                        return false;
                    }
                }, tc -> {
                    @Nullable TimedFocusable item = TimedFocusable.getRecentlyFocused(FXUtility.mouse(EditColumnExpressionDialog.this).expressionEditor, nameField);
                    if (item == expressionEditor)
                    {
                        expEdOnPick.consume(tc);
                    }
                    else if (item == nameField)
                    {
                        nameField.setText(tc.getSecond().getRaw());
                    }
                });
            }

            @Override
            public void disablePickingMode()
            {
                parent.disablePickingMode();
            }
        };
        expressionEditor = new ExpressionEditor(initialExpression, srcTableWrapper, curColumnLookup, expectedType, columnPicker, parent.getManager().getTypeManager(), makeTypeState, FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), e -> {
            curValue = e;
            notifyModified();
        }) {
            @Override
            protected void parentFocusRightOfThis(Either<Focus, Integer> side, boolean becauseOfTab)
            {
                @Nullable Node button = getDialogPane().lookupButton(ButtonType.OK);
                if (button != null && becauseOfTab)
                    button.requestFocus();
            }
        };
        curValue = expressionEditor.save();
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

        content.addRow(GUI.labelledGridRow("edit.column.name", "edit-column/column-name", nameField.getNode()));
        
        content.addRow(GUI.labelledGridRow("edit.column.expression",
                "edit-column/column-expression", expressionEditor.getContainer()));
        
        getDialogPane().setContent(content);

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

    public Optional<Pair<ColumnId, Expression>> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, 400, 200);
    }
    
    public void addTopMessage(@LocalizableKey String topMessage)
    {
        TextFlow display = GUI.textFlowKey(topMessage, "edit-column-top-message");
        display.setMaxWidth(9999.0);
        Node oldContent = getDialogPane().getContent();
        getDialogPane().setContent(GUI.borderTopCenter(
            display,
                oldContent
        ));
        BorderPane.setMargin(oldContent, new Insets(10, 0, 0, 0));
    }

    @Override
    protected Validity checkValidity()
    {
        if (nameField.valueProperty().getValue() == null)
            return Validity.IMPOSSIBLE_TO_SAVE;
        else if (expressionEditor.hasErrors())
            return Validity.ERROR_BUT_CAN_SAVE;
        else
            return Validity.NO_ERRORS;
    }

    @Override
    protected @Nullable Pair<ColumnId, Expression> calculateResult()
    {
        @Nullable ColumnId name = nameField.valueProperty().getValue();
        if (name == null)
            return null;
        else
            return new Pair<>(name, curValue);
    }

    @Override
    protected void showAllErrors()
    {
        expressionEditor.showAllErrors();
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
}
