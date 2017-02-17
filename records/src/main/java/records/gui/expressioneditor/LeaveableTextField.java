package records.gui.expressioneditor;

import javafx.scene.control.TextField;
import org.checkerframework.checker.initialization.qual.NotOnlyInitialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A TextField which is a child of an ExpressionParent.
 * When the user tries to move left or right at the
 * beginning/end of the field respectively, it asks
 * the parent to move focus to the appropriate adjacent item.
 */
public class LeaveableTextField extends TextField
{
    private final ExpressionNode us;
    private final ExpressionParent parent;

    public LeaveableTextField(ExpressionNode us, ExpressionParent parent)
    {
        this.us = us;
        this.parent = parent;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void forward()
    {
        if (getCaretPosition() == getLength())
            parent.focusRightOf(us);
        else
            super.forward();
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void backward()
    {
        if (getCaretPosition() == 0)
            parent.focusLeftOf(us);
        else
            super.backward();
    }
}
