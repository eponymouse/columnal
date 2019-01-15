package records.gui.flex;

import com.google.common.collect.ImmutableList;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledText;
import org.fxmisc.richtext.model.TextOps;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import java.util.Collection;

/**
 * A text field which can change its behaviour based on a dynamically-switchable
 * EditorKitInterface object.  Because these fields can be expensive to create,
 * we re-use them, and swap over the editor kit.  This also means the field is
 * not bound to a specific contained data type, as it is the EditorKitInterface
 * object (usually an EditorKit&lt;T&gt; object) which knows which type is held.
 */
@OnThread(Tag.FXPlatform)
public class FlexibleTextField extends StyleClassedTextArea
{
    private @MonotonicNonNull EditorKitInterface editorKit;
    
    public FlexibleTextField()
    {
        super(false);
        getStyleClass().add("flexible-text-field");
        setPrefHeight(FXUtility.measureNotoSansHeight());

        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            FlexibleTextField usFocused = FXUtility.focused(this);
            if (usFocused.editorKit == null)
                return;
            //usFocused.updateAutoComplete(getSelection());
            if (focused)
            {
                usFocused.focusGained();
            }
            else
            {
                usFocused.focusLost();
            }
        });

        Nodes.addInputMap(FXUtility.keyboard(this), InputMap.sequence(
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.TAB), (KeyEvent e) -> {
                FXUtility.keyboard(this).tabPressed();
                e.consume();
            }),
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ENTER), (KeyEvent e) -> {
                FXUtility.keyboard(this).enterPressed();
                e.consume();
            }),
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ESCAPE), (KeyEvent e) -> {
                FXUtility.keyboard(this).escapePressed();
                e.consume();
            })
        ));

        Nodes.addFallbackInputMap(FXUtility.mouse(this), InputMap.consume(MouseEvent.ANY));
    }

    public void resetContent(EditorKitInterface editorKit)
    {
        if (this.editorKit != null)
        {
            this.editorKit.setField(null);
        }

        this.editorKit = editorKit;
        this.editorKit.setField(this);
    }

    public @Nullable EditorKitInterface getEditorKit()
    {
        return editorKit;
    }

    void tabPressed()
    {
        /*
        if (autoComplete != null && autoComplete.isShowing())
        {
            autoComplete.fireSelected();
        }
        else*/ if (editorKit != null)
        {
            editorKit.relinquishFocus();
        }
    }

    void escapePressed()
    {
        /*if (autoComplete != null)
        {
            autoComplete.hide();
            autoComplete = null;
        }
        else*/ if (editorKit != null)
            editorKit.relinquishFocus(); // Should move focus away from us
    }

    void enterPressed()
    {
        /*if (autoComplete != null)
        {
            autoComplete.fireSelected();
        }
        */
        boolean atEnd = getCaretPosition() == getLength();
        if (atEnd && editorKit != null)
            editorKit.relinquishFocus(); // Should move focus away from us
    }

    @RequiresNonNull("editorKit")
    private void focusGained()
    {
        editorKit.focusChanged(getText(), true);
    }

    @RequiresNonNull("editorKit")
    private void focusLost()
    {
        // Deselect when focus is lost:
        deselect();
        editorKit.focusChanged(getText(), false);
    }
    
    public static ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc(ImmutableList<StyledText<Collection<String>>> segments)
    {
        ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> overall = ReadOnlyStyledDocument.fromString("", ImmutableList.<String>of(), ImmutableList.<String>of(), StyledText.<Collection<String>>textOps());
        for (StyledText<Collection<String>> segment : segments)
        {
            ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> latest = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromString(segment.getText(), ImmutableList.<String>of(), segment.getStyle(), StyledText.<Collection<String>>textOps());
            overall = overall.concat(latest);
        }
        return overall;
        
    }

    public void setHasError(boolean hasError)
    {
        FXUtility.setPseudoclass(this, "has-error", hasError);
    }

    public <T> @Nullable EditorKit<T> getEditableKit(Class<T> itemClass)
    {
        if (editorKit == null)
            return null;
        else
            return editorKit.asEditableKit(itemClass);
    }
}
