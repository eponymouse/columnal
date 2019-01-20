package records.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataType;
import records.gui.expressioneditor.ExpressionEditor;
import records.gui.expressioneditor.ExpressionEditor.ColumnAvailability;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.FXUtility;
import utility.gui.LightDialog;

import java.util.Optional;
import java.util.function.Function;

@OnThread(Tag.FXPlatform)
public class EditExpressionDialog extends LightDialog<Expression>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;

    public EditExpressionDialog(View parent, @Nullable Table srcTable, @Nullable Expression initialExpression, Function<ColumnId, ColumnAvailability> groupedColumns, @Nullable DataType expectedType)
    {
        super(parent.getWindow(), new DialogPaneWithSideButtons());
        setResizable(true);

        expressionEditor = new ExpressionEditor(initialExpression, new ReadOnlyObjectWrapper<@Nullable Table>(srcTable), groupedColumns, new ReadOnlyObjectWrapper<@Nullable DataType>(expectedType), parent.getManager(), e -> {curValue = e;});
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
            FXUtility.runAfter(expressionEditor::focusWhenShown);
        });
        //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
    }

    public Optional<Expression> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, 400, 200);
    }
}
