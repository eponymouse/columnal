package records.gui.expressioneditor;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;

import java.util.List;

/**
 * Created by neil on 17/12/2016.
 */
public interface ExpressionParent
{
    @Nullable DataType getType(ExpressionNode child);

    List<ColumnId> getAvailableColumns();

    List<String> getAvailableVariables(ExpressionNode child);

    boolean isTopLevel();

    // Focus the child to the right of the given child:
    void focusRightOf(ExpressionNode child);
    void focusLeftOf(ExpressionNode child);
}
