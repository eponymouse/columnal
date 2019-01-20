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
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataType;
import records.gui.View;
import records.gui.expressioneditor.EEDisplayNode.Focus;
import records.gui.expressioneditor.ExpressionEditor;
import records.gui.expressioneditor.ExpressionEditor.ColumnAvailability;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LightDialog;

import java.util.Optional;
import java.util.function.Function;

// Edit column name and expression for that column
@OnThread(Tag.FXPlatform)
public class EditColumnExpressionDialog extends LightDialog<Pair<ColumnId, Expression>>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;

    public EditColumnExpressionDialog(View parent, @Nullable Table srcTable, ColumnId initialName, @Nullable Expression initialExpression, Function<ColumnId, ColumnAvailability> groupedColumns, @Nullable DataType expectedType)
    {
        super(parent.getWindow(), new DialogPaneWithSideButtons());
        setResizable(true);

        ColumnNameTextField field = new ColumnNameTextField(initialName);
        ReadOnlyObjectWrapper<@Nullable Table> srcTableWrapper = new ReadOnlyObjectWrapper<@Nullable Table>(srcTable);
        ReadOnlyObjectWrapper<@Nullable DataType> expectedTypeWrapper = new ReadOnlyObjectWrapper<@Nullable DataType>(expectedType);
        expressionEditor = new ExpressionEditor(initialExpression, srcTableWrapper, groupedColumns, expectedTypeWrapper, parent.getManager(), e -> {curValue = e;}) {
            @Override
            protected void parentFocusRightOfThis(Focus side, boolean becauseOfTab)
            {
                @Nullable Node button = getDialogPane().lookupButton(ButtonType.OK);
                if (button != null && becauseOfTab)
                    button.requestFocus();
            }
        };
        curValue = expressionEditor.save();
        // Tab doesn't seem to work right by itself:
        field.getNode().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB)
            {
                expressionEditor.focus(Focus.LEFT);
                e.consume();
            }
        });
        
        LabelledGrid content = new LabelledGrid();
        content.getStyleClass().add("edit-column-expression-content");

        content.addRow(GUI.labelledGridRow("edit.column.name", "edit-column/column-name", field.getNode()));
        
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
        setResultConverter(bt -> {
            @Nullable ColumnId columnId = field.valueProperty().getValue();
            if (bt == ButtonType.OK && columnId != null)
                return new Pair<>(columnId, curValue);
            else
                return null;
        });
        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(field::requestFocusWhenInScene);
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
}
