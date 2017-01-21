package records.gui.expressioneditor;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;

import java.util.List;

/**
 * An interface for the parents of ExpressionNode to provide information
 * to their children.
 */
public interface ExpressionParent
{
    // TODO not sure what this method was meant to be for...  ahem
    //@Nullable DataType getType(ExpressionNode child);

    /**
     * Gets all the columns that the user could possibly enter.
     */
    List<ColumnId> getAvailableColumns();

    /**
     * Gets all the declared variables in scope at the given child
     * (from parent/grandparent/aunt nodes, not from the node itself).
     */
    List<String> getAvailableVariables(ExpressionNode child);

    /**
     * Gets all the available tagged types (and thus their tags)
     */
    List<DataType> getAvailableTaggedTypes();

    /**
     * Is this expression at the topmost level?
     */
    boolean isTopLevel();

    /**
     * Called to notify parent that the given child has changed its content.
     */
    void changed(ExpressionNode child);

    /**
     * Focus the child to the right of the given child.
     */
    void focusRightOf(ExpressionNode child);

    /**
     * Focus the child to the left of the given child.
     */
    void focusLeftOf(ExpressionNode child);
}
