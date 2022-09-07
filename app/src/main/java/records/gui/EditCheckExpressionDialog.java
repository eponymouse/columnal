package records.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.stage.Modality;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table;
import xyz.columnal.data.datatype.DataType;
import records.gui.lexeditor.ExpressionEditor;
import records.gui.lexeditor.TopLevelEditor.Focus;
import xyz.columnal.transformations.Check;
import xyz.columnal.transformations.Check.CheckType;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.FXPlatformFunction;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.gui.DialogPaneWithSideButtons;
import xyz.columnal.utility.gui.DoubleOKLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.LabelledGrid;

// Edit column name and expression for that column
@OnThread(Tag.FXPlatform)
public class EditCheckExpressionDialog extends DoubleOKLightDialog<Pair<CheckType, Expression>>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;
    private final ComboBox<CheckType> combo;

    public EditCheckExpressionDialog(View parent, @Nullable Table srcTable, CheckType initialCheckType, @Nullable Expression initialExpression, boolean selectAll, FXPlatformFunction<CheckType, ColumnLookup> columnLookup)
    {
        super(parent, new DialogPaneWithSideButtons());
        setResizable(true);
        initModality(Modality.NONE);

        combo = new ComboBox<>();
        combo.getStyleClass().add("check-type-combo");
        combo.getItems().setAll(CheckType.values());
        combo.getSelectionModel().select(initialCheckType);
        ReadOnlyObjectWrapper<@Nullable Table> srcTableWrapper = new ReadOnlyObjectWrapper<@Nullable Table>(srcTable);
        SimpleObjectProperty<ColumnLookup> columnLookupProperty = new SimpleObjectProperty<>(columnLookup.apply(initialCheckType));
        FXUtility.addChangeListenerPlatform(combo.getSelectionModel().selectedItemProperty(), ct -> columnLookupProperty.set(columnLookup.apply(ct == null ? initialCheckType : ct)));
        expressionEditor = new ExpressionEditor(initialExpression, srcTableWrapper, columnLookupProperty, DataType.BOOLEAN, parent, parent.getManager().getTypeManager(), () -> Check.makeTypeState(parent.getManager().getTypeManager(), combo.getSelectionModel().getSelectedItem()), FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), e -> {curValue = e;}) {
            @Override
            protected void parentFocusRightOfThis(Either<Focus, Integer> side, boolean becauseOfTab)
            {
                @Nullable Node button = getDialogPane().lookupButton(ButtonType.OK);
                if (button != null && becauseOfTab)
                    button.requestFocus();
            }
        };
        curValue = expressionEditor.save(true);
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

        content.addRow(LabelledGrid.labelledGridRow("edit.check.type", "edit-check/check-type", combo));
        
        content.addRow(LabelledGrid.labelledGridRow("edit.check.expression",
                "edit-check/check-expression", expressionEditor.getContainer()));
        
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
            FXUtility.runAfter(() -> {
                expressionEditor.focus(Focus.LEFT);
                if (selectAll)
                    expressionEditor.selectAll();
            });
        });
        setOnHiding(e -> {
            expressionEditor.cleanup();
        });
        //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
    }

    @Override
    protected void showAllErrors()
    {
        expressionEditor.showAllErrors();
    }

    @Override
    protected Validity checkValidity()
    {
        expressionEditor.save(true);
        if (combo.getSelectionModel().getSelectedItem() == null)
            return Validity.IMPOSSIBLE_TO_SAVE;
        else if (expressionEditor.hasErrors())
            return Validity.ERROR_BUT_CAN_SAVE;
        else
            return Validity.NO_ERRORS;
    }

    @Override
    protected @Nullable Pair<CheckType, Expression> calculateResult()
    {
        @Nullable CheckType checkType = combo.getSelectionModel().getSelectedItem();
        if (checkType != null)
            return new Pair<>(checkType, curValue);
        else
            return null;
    }
}
