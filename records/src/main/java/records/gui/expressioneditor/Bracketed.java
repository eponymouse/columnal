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
    @SuppressWarnings("nullness") // TODO sort this out
    public Bracketed(List<Function<Consecutive, OperandNode>> initial, ExpressionParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode)
    {
        super(parent, prefixNode == null ? null : c -> prefixNode, suffixNode);
    }

    @Override
    public Bracketed focusWhenShown()
    {
        super.focusWhenShown();
        return this;
    }

    @Override
    public Bracketed prompt(String prompt)
    {
        super.prompt(prompt);
        return this;
    }
}
