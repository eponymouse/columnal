package records.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import org.checkerframework.checker.nullness.qual.Nullable;
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
import utility.FXPlatformSupplierInt;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.FXUtility;
import utility.gui.LightDialog;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class EditExpressionDialog extends LightDialog<Expression>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;

    public EditExpressionDialog(View parent, @Nullable Table srcTable, @Nullable Expression initialExpression, ColumnLookup columnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType)
    {
        super(parent, new DialogPaneWithSideButtons());
        setResizable(true);
        initModality(Modality.NONE);

        expressionEditor = new ExpressionEditor(initialExpression, new ReadOnlyObjectWrapper<@Nullable Table>(srcTable), new ReadOnlyObjectWrapper<>(columnLookup), expectedType, parent, parent.getManager().getTypeManager(), makeTypeState, FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), parent.getFixHelper(), e -> {curValue = e;});
        curValue = expressionEditor.save();
        
        getDialogPane().setContent(new BorderPane(expressionEditor.getContainer()));
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("cancel-button");
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        setResultConverter(bt -> bt == ButtonType.OK ? curValue : null);
        setOnHiding(e -> {
            expressionEditor.cleanup();
        });
        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(() -> expressionEditor.focus(Focus.LEFT));
        });
        //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
    }

    public Optional<Expression> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, 400, 200);
    }
}
