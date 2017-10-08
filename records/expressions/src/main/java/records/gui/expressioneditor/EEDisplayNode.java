package records.gui.expressioneditor;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;

/**
 * A navigatable, displayable item in the expression.  Used for requesting focus.
 */
public @Interned interface EEDisplayNode
{
    @Pure
    public ObservableList<Node> nodes();

    public static enum Focus { LEFT, RIGHT };

    public void focus(Focus side);

    /**
     * Is focus in this operand (or one of its children)?
     */
    public boolean isFocused();

    public void focusWhenShown();

    public boolean isOrContains(EEDisplayNode child);
}
