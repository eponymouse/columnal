package records.gui.expressioneditor;

import javafx.scene.control.TextField;
import records.gui.expressioneditor.EEDisplayNode.Focus;
import records.gui.expressioneditor.TopLevelEditor.SelectExtremityTarget;
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
    private final ConsecutiveChild<?, ?> us;
    private final EEDisplayNodeParent parent;
    private boolean leavingByCursor = false;

    public LeaveableTextField(ConsecutiveChild<?, ?> us, EEDisplayNodeParent parent)
    {
        this.us = us;
        this.parent = parent;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void forward()
    {
        if (getCaretPosition() == getLength())
        {
            leavingByCursor = true;
            parent.focusRightOf(us, Focus.LEFT, false);
            leavingByCursor = false;
        }
        else
            super.forward();
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void backward()
    {
        if (getCaretPosition() == 0)
        {
            leavingByCursor = true;
            parent.focusLeftOf(us);
            leavingByCursor = false;
            
        }
        else
            super.backward();
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void selectHome()
    {
        parent.getEditor().extendSelectionToExtremity(us, SelectExtremityTarget.HOME);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void selectEnd()
    {
        parent.getEditor().extendSelectionToExtremity(us, SelectExtremityTarget.END);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public boolean deletePreviousChar()
    {
        if (getCaretPosition() == 0)
        {
            leavingByCursor = true;
            parent.deleteLeftOf(us);
            leavingByCursor = false;
            // No need to do anything following deletion:
            return false;
        }
        else
            return super.deletePreviousChar();
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public boolean deleteNextChar()
    {
        if (getCaretPosition() == getLength())
        {
            leavingByCursor = true;
            parent.deleteRightOf(us);
            leavingByCursor = false;
            // No need to do anything following deletion:
            return false;
        }
        else
            return super.deleteNextChar();
    }

    @OnThread(Tag.FXPlatform)
    public boolean leavingByCursor()
    {
        return leavingByCursor;
    }

    @Override
    @OnThread(Tag.FX)
    public void requestFocus()
    {
        if (isEditable())
            super.requestFocus();
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public String toString()
    {
        // Useful for debugging to see content:
        return super.toString() + " {{{" + getText() + "}}}";
    }
}
