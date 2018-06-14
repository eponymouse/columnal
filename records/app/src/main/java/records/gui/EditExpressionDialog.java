package records.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.datatype.DataType;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.FXUtility;
import utility.gui.LightDialog;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class EditExpressionDialog extends LightDialog<Expression>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;

    public EditExpressionDialog(View parent, @Nullable Table srcTable, Expression initialExpression, boolean perRow, @Nullable DataType expectedType)
    {
        super(parent.getWindow(), new DialogPaneWithSideButtons());
        setResizable(true);
        curValue = initialExpression;

        expressionEditor = new ExpressionEditor(initialExpression, new ReadOnlyObjectWrapper<@Nullable Table>(srcTable), perRow, new ReadOnlyObjectWrapper<@Nullable DataType>(expectedType), parent.getManager(), e -> {curValue = e;});
        
        getDialogPane().setContent(new BorderPane(expressionEditor.getContainer()));
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        setResultConverter(bt -> bt == ButtonType.OK ? curValue : null);
        setOnHiding(e -> {
            expressionEditor.cleanup();
        });
        //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
    }

    public Optional<Expression> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, 400, 200);
    }
}
