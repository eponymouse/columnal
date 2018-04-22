package records.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataType;
import records.gui.View;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.LightDialog;

import java.util.Optional;

// Edit column name and expression for that column
@OnThread(Tag.FXPlatform)
public class EditColumnExpressionDialog extends LightDialog<Pair<ColumnId, Expression>>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;

    public EditColumnExpressionDialog(View parent, @Nullable Table srcTable, ColumnId initialName, Expression initialExpression, boolean perRow, @Nullable DataType expectedType)
    {
        super(parent.getWindow());
        setResizable(true);
        curValue = initialExpression;

        ColumnNameTextField field = new ColumnNameTextField(initialName);
        expressionEditor = new ExpressionEditor(initialExpression, new ReadOnlyObjectWrapper<@Nullable Table>(srcTable), perRow, new ReadOnlyObjectWrapper<@Nullable DataType>(expectedType), parent.getManager(), e -> {curValue = e;});

        getDialogPane().setContent(new BorderPane(expressionEditor.getContainer(), field.getNode(), null, null, null));
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        setResultConverter(bt -> {
            @Nullable ColumnId columnId = field.valueProperty().getValue();
            if (bt == ButtonType.OK && columnId != null)
                return new Pair<>(columnId, curValue);
            else
                return null;
        });

        //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
    }

    public Optional<Pair<ColumnId, Expression>> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, 400, 200);
    }
}
