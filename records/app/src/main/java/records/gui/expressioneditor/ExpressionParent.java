package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import utility.Pair;

import java.util.List;
import java.util.stream.Stream;

/**
 * An interface for the parents of ExpressionNode to provide information
 * to their children.
 */
public interface ExpressionParent
{
    // TODO not sure what this method was meant to be for...  ahem
    //@Nullable DataType getType(ExpressionNode child);

    /**
     * Get likely types and completions for given child.  For example,
     * if the expression is column Name = _ (where the RHS
     * is the child in question) we might offer Text and most frequent values
     * of the Name column.
     *
     * The completions are not meant to be all possible values of the given
     * type (e.g. literals, available columns, etc), as that can be figured out
     * from the type regardless of context.  This is only items which make
     * particular sense in this particular context, e.g. a commonly passed argument
     * to a function.
     */
    List<Pair<DataType, List<String>>> getSuggestedContext(ExpressionNode child) throws InternalException, UserException;

    /**
     * Gets all the declared variables in scope at the given child
     * (from parent/grandparent/aunt nodes, not from the node itself).
     */
    List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child);

    /**
     * Called to notify parent that the given child has changed its content.
     */
    void changed(@UnknownInitialization(ExpressionNode.class) ExpressionNode child);

    /**
     * Focus the child to the right of the given child.
     */
    void focusRightOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child);

    /**
     * Focus the child to the left of the given child.
     */
    void focusLeftOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child);

    /**
     * Gets the parent styles (for styling the top).  The first one is the outermost,
     * the last one is the innermost.
     */
    Stream<String> getParentStyles();

    /**
     * Gets the editor which contains this expression
     */
    ExpressionEditor getEditor();
}
