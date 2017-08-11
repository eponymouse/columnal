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
 * An interface for the parents of EEDisplayNode to provide information
 * to their children.
 */
public interface EEDisplayNodeParent
{
    /**
     * Called to notify parent that the given child has changed its content.
     */
    void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child);

    /**
     * Focus the child to the right of the given child.
     */
    void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child);

    /**
     * Focus the child to the left of the given child.
     */
    void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child);

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
