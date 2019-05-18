package records.gui;

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
import records.gui.AutoComplete.Completion;
import records.gui.AutoComplete.CompletionCalculator;
import records.gui.AutoComplete.CompletionListener;
import records.gui.AutoComplete.WhitespacePolicy;
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
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.UnitType;
import utility.Utility;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.DoubleOKLightDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.TimedFocusable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

// Edit column name and expression for that column
@OnThread(Tag.FXPlatform)
public class EditColumnExpressionDialog<T> extends DoubleOKLightDialog<EditColumnExpressionDialog<T>.Result>
{
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
    }
    
    private final ExpressionEditor expressionEditor;
    private Expression curValue;
    private final ColumnNameTextField nameField;
    private final SidePane<T> sidePane;

    public EditColumnExpressionDialog(View parent, @Nullable Table srcTable, @Nullable ColumnId initialName, @Nullable Expression initialExpression, Function<@Nullable ColumnId, ColumnLookup> makeColumnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType, SidePane<T> sidePane)
    {
        super(parent, new DialogPaneWithSideButtons());
        this.sidePane = sidePane;
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
        expressionEditor = new ExpressionEditor(initialExpression, srcTableWrapper, curColumnLookup, expectedType, columnPicker, parent.getManager().getTypeManager(), makeTypeState, FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), parent.getFixHelper(), e -> {
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

        Node sidePaneNode = sidePane.getSidePane();
        getDialogPane().setContent(new BorderPane(content, null, null, null, sidePaneNode));

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
        return new EditColumnExpressionDialog<>(parent, srcTable, initialName, initialExpression, makeColumnLookup, makeTypeState, expectedType, new SidePane<UnitType>()
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

            @Nullable
            @Override
            public UnitType calculateResult()
            {
                return UnitType.UNIT;
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
            return sidePane.checkValidity();
    }

    @Override
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
}
