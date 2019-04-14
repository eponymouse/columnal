package records.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
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
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataType;
import records.gui.lexeditor.ExpressionEditor;
import records.gui.lexeditor.TopLevelEditor.Focus;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.DoubleOKLightDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LightDialog;

import java.util.Optional;

// Edit column name and expression for that column
@OnThread(Tag.FXPlatform)
public class EditColumnExpressionDialog extends DoubleOKLightDialog<Pair<ColumnId, Expression>>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;
    private final ColumnNameTextField nameField;

    public EditColumnExpressionDialog(View parent, @Nullable Table srcTable, ColumnId initialName, @Nullable Expression initialExpression, ColumnLookup columnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType)
    {
        super(parent, new DialogPaneWithSideButtons());
        setResizable(true);
        initModality(Modality.NONE);

        nameField = new ColumnNameTextField(initialName);
        FXUtility.addChangeListenerPlatform(nameField.valueProperty(), v -> notifyModified());
        ReadOnlyObjectWrapper<@Nullable Table> srcTableWrapper = new ReadOnlyObjectWrapper<@Nullable Table>(srcTable);
        ReadOnlyObjectWrapper<@Nullable DataType> expectedTypeWrapper = new ReadOnlyObjectWrapper<@Nullable DataType>(expectedType);
        expressionEditor = new ExpressionEditor(initialExpression, srcTableWrapper, new ReadOnlyObjectWrapper<>(columnLookup), expectedTypeWrapper, parent, parent.getManager().getTypeManager(), makeTypeState, FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), e -> {
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
}
