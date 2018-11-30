package records.gui.expressioneditor;

import javafx.scene.control.TextField;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import records.gui.expressioneditor.EEDisplayNode.Focus;
import records.gui.expressioneditor.TopLevelEditor.SelectExtremityTarget;
import records.gui.expressioneditor.TopLevelEditor.SelectionTarget;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

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

    public LeaveableTextField(@UnknownInitialization ConsecutiveChild<?, ?> us, EEDisplayNodeParent parent)
    {
        this.us = Utility.later(us);
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
    public void home()
    {
        parent.getEditor().focus(Focus.LEFT);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void end()
    {
        parent.getEditor().focus(Focus.RIGHT);
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
    public void selectBackward()
    {
        if (getCaretPosition() == 0)
        {
            if (getSelection().getLength() > 0)
                parent.getEditor().extendSelectionTo(us, SelectionTarget.AS_IS);
            parent.getEditor().extendSelectionTo(us, SelectionTarget.LEFT);
        }
        else
            super.selectBackward();
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void selectForward()
    {
        if (getCaretPosition() == getLength())
        {
            if (getSelection().getLength() > 0)
                parent.getEditor().extendSelectionTo(us, SelectionTarget.AS_IS);
            parent.getEditor().extendSelectionTo(us, SelectionTarget.RIGHT);
        }
        else
            super.selectForward();
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
