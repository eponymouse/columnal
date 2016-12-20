package records.gui.expressioneditor;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;

import java.util.Collections;
import java.util.List;

/**
 * A navigatable, displayable item in the expression
 */
public @Interned abstract interface ExpressionNode
{
    @Pure
    public abstract ObservableList<Node> nodes();

    public static enum Focus { LEFT, RIGHT };

    public abstract void focus(Focus side);
}
