package records.gui.expressioneditor;

import javafx.beans.binding.BooleanExpression;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.stream.Stream;

/**
 * A helper class that shares code between various text-field based nodes.
 */
public abstract class EntryNode<EXPRESSION extends StyledShowable, SAVER extends ClipboardSaver> extends DeepNodeTree implements EEDisplayNode, ConsecutiveChild<EXPRESSION, SAVER>, ErrorDisplayer<EXPRESSION, SAVER>
{
    protected final ConsecutiveBase<EXPRESSION, SAVER> parent;
    private final Class<EXPRESSION> expressionClass;

    protected final LeaveableTextField textField;
    private boolean focusPending;
    
    public EntryNode(ConsecutiveBase<EXPRESSION, SAVER> parent, Class<EXPRESSION> expressionClass)
    {
        this.parent = parent;
        this.expressionClass = expressionClass;
        // Don't quite understand why I need to wrap this in a later call:
        textField = Utility.later(new LeaveableTextField(this, parent) {

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void paste()
            {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                if (clipboard.hasContent(parent.getClipboardType()))
                {
                    Object content = clipboard.getContent(parent.getClipboardType());
                    if (content != null)
                    {
                        parent.getEditor().addContent(FXUtility.mouse(EntryNode.this), content.toString());
                        return;
                    }
                }
                String s = clipboard.getString();
                // We may get removed so it's important to remember the scene:
                Scene sc = getScene();
                if (sc != null)
                {
                    Scene scene = sc;
                    // So that all the autocompletes etc fire,
                    // we paste one character at a time:
                    s.codePoints().forEach(c -> {
                        // The focus owner may change during paste,
                        // so we must requery it with each character:
                        Node focused = scene.getFocusOwner();
                        if (focused instanceof TextField)
                        {
                            TextField textField = (TextField) focused;
                            textField.replaceSelection(Utility.codePointToString(c));
                            textField.positionCaret(textField.getCaretPosition() + 1);
                        }
                    });
                }
            }
        });
        textField.getStyleClass().add("entry-field");
    }
    
    // Don't understand why the checker doesn't see that this method is OK:
    @SuppressWarnings("nullness")
    // Although we don't extend OperandNode, this deliberately implements a method from OperandNode:
    public final ConsecutiveBase<EXPRESSION, SAVER> getParent(@UnknownInitialization(EntryNode.class) EntryNode<EXPRESSION, SAVER> this)
    {
        return parent;
    }

    @Override
    public void setPrompt(@Localized String prompt)
    {
        textField.setPromptText(prompt);
    }

    @Override
    public boolean isFocused()
    {
        return textField.isFocused();
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
    }

    @Override
    public void focusChanged()
    {
        // Nothing to do
    }

    @Override
    public void focusWhenShown()
    {
        //Log.debug("### Focusing when shown: " + textField);
        focusPending = true;
        FXUtility.onceNotNull(textField.sceneProperty(), s -> FXUtility.runAfter(() -> {
            flushFocusRequest();
        }));
    }

    /**
     * If waiting to request focus, do that now.
     */
    @Override
    public void flushFocusRequest()
    {
        if (focusPending)
        {
            focus(Focus.RIGHT);
            focusPending = false;
        }
    }

    @Override
    public boolean isFocusPending()
    {
        return focusPending;
    }

    @Override
    public void focus(Focus side)
    {
        //Log.debug("### Focusing now: " + textField + " Editable: " + textField.isEditable() + " Scene: " + textField.getScene());
        textField.requestFocus();
        if (!textField.isFocused())
        {
            Log.error("Text field not focused, editable: " + textField.isEditable() + " disabled: " + textField.isDisabled() + " in scene: " + (textField.getScene() != null));
        }
        if (side == Focus.LEFT)
        {
            textField.positionCaret(0);
        }
        else
        {
            textField.positionCaret(textField.getLength());
        }
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        visitor.register(this, expressionClass);
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren(@UnknownInitialization(DeepNodeTree.class) EntryNode<EXPRESSION, SAVER> this)
    {
        return Stream.empty();
    }

    // Should also trigger completions if applicable
    public abstract void setText(String initialContent);

    @Override
    public void bindDisable(BooleanExpression disabledProperty)
    {
        textField.disableProperty().bind(disabledProperty);
    }
}
