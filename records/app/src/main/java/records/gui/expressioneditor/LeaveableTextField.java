package records.gui.expressioneditor;

import javafx.scene.control.TextField;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A TextField which is a child of an EEDisplayNodeParent.
 * When the user tries to move left or right at the
 * beginning/end of the field respectively, it asks
 * the parent to move focus to the appropriate adjacent item.
 */
public class LeaveableTextField extends TextField
{
    private final EEDisplayNode us;
    private final EEDisplayNodeParent parent;

    public LeaveableTextField(EEDisplayNode us, EEDisplayNodeParent parent)
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
