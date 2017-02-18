package records.gui.expressioneditor;

import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import utility.FXPlatformFunction;

import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 20/12/2016.
 */
public class Bracketed extends Consecutive implements OperandNode
{
    public Bracketed(List<FXPlatformFunction<Consecutive, OperandNode>> initial, ExpressionParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode)
    {
        super(parent, prefixNode, suffixNode);
    }

    @Override
    protected void initializeContent(@UnknownInitialization(Consecutive.class) Bracketed this)
    {
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
