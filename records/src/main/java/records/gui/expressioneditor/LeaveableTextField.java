package records.gui.expressioneditor;

import javafx.scene.control.TextField;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 20/12/2016.
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
