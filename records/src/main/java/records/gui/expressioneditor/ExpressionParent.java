package records.gui.expressioneditor;

import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;

import java.util.List;

/**
 * Created by neil on 17/12/2016.
 */
public interface ExpressionParent
{
    void replace(ExpressionNode oldNode, @Nullable ExpressionNode newNode);

    // If null, add to end
    void addToRight(@Nullable ExpressionNode rightOf, ExpressionNode... newNode);

    @Nullable DataType getType(ExpressionNode child);

    List<ColumnId> getAvailableColumns();

    void deleteOneLeftOf(ExpressionNode child);
    void deleteOneRightOf(ExpressionNode child);

    void focusBefore(ExpressionNode child);
}
