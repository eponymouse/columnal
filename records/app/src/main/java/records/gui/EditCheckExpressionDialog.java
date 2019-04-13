package records.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.stage.Modality;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.datatype.DataType;
import records.gui.lexeditor.ExpressionEditor;
import records.gui.lexeditor.TopLevelEditor.Focus;
import records.transformations.Check.CheckType;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LightDialog;

// Edit column name and expression for that column
@OnThread(Tag.FXPlatform)
public class EditCheckExpressionDialog extends LightDialog<Pair<CheckType, Expression>>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;

    public EditCheckExpressionDialog(View parent, @Nullable Table srcTable, CheckType initialCheckType, @Nullable Expression initialExpression, FXPlatformFunction<CheckType, ColumnLookup> columnLookup)
    {
        super(parent, new DialogPaneWithSideButtons());
        setResizable(true);
        initModality(Modality.NONE);

        ComboBox<CheckType> combo = new ComboBox<>();
        combo.getStyleClass().add("check-type-combo");
        combo.getItems().setAll(CheckType.values());
        combo.getSelectionModel().select(initialCheckType);
        ReadOnlyObjectWrapper<@Nullable Table> srcTableWrapper = new ReadOnlyObjectWrapper<@Nullable Table>(srcTable);
        ReadOnlyObjectWrapper<@Nullable DataType> expectedType = new ReadOnlyObjectWrapper<@Nullable DataType>(DataType.BOOLEAN);
        SimpleObjectProperty<ColumnLookup> columnLookupProperty = new SimpleObjectProperty<>(columnLookup.apply(initialCheckType));
        FXUtility.addChangeListenerPlatform(combo.getSelectionModel().selectedItemProperty(), ct -> columnLookupProperty.set(columnLookup.apply(ct == null ? initialCheckType : ct)));
        expressionEditor = new ExpressionEditor(initialExpression, srcTableWrapper, columnLookupProperty, expectedType, parent, parent.getManager().getTypeManager(), null, FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), e -> {curValue = e;}) {
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
        /*
        field.getNode().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB)
            {
                expressionEditor.focus(Focus.LEFT);
                e.consume();
            }
        });
        */
        
        LabelledGrid content = new LabelledGrid();
        content.getStyleClass().add("edit-check-expression-content");

        content.addRow(GUI.labelledGridRow("edit.check.type", "edit-check/check-type", combo));
        
        content.addRow(GUI.labelledGridRow("edit.check.expression",
                "edit-check/check-expression", expressionEditor.getContainer()));
        
        getDialogPane().setContent(content);

        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("cancel-button");
        // Prevent enter/escape activating buttons:
        ((Button)getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
        FXUtility.preventCloseOnEscape(getDialogPane());
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        setResultConverter(bt -> {
            @Nullable CheckType checkType = combo.getSelectionModel().getSelectedItem();
            if (bt == ButtonType.OK && checkType != null)
                return new Pair<>(checkType, curValue);
            else
                return null;
        });
        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(() -> expressionEditor.focus(Focus.LEFT));
        });
        setOnHiding(e -> {
            expressionEditor.cleanup();
        });
        //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
    }
}
