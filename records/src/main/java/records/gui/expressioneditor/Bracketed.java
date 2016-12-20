package records.gui.expressioneditor;

import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 20/12/2016.
 */
public class Bracketed extends Consecutive implements OperandNode
{
    public Bracketed(List<Function<Consecutive, OperandNode>> initial, ExpressionParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode)
    {
        super(parent, prefixNode, suffixNode);
    }

    @Override
    public Bracketed focusWhenShown()
    {
        super.focusWhenShown();
        return this;
    }
}
