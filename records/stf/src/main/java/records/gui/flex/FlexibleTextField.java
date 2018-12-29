package records.gui.flex;

import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

@OnThread(Tag.FXPlatform)
public class FlexibleTextField extends StyleClassedTextArea
{
    private @MonotonicNonNull EditorKit<?> editorKit;
    
    public FlexibleTextField()
    {
        super(false);
        getStyleClass().add("flexible-text-field");
        setPrefHeight(FXUtility.measureNotoSansHeight());

        FXUtility.addChangeListenerPlatformNN(textProperty(), text -> {
            if (editorKit != null)
                editorKit.fieldChanged(text, isFocused());
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

    public <T> void resetContent(EditorKit<T> editorKit)
    {
        if (this.editorKit != null)
        {
            this.editorKit.setField(null);
        }

        this.editorKit = editorKit;
        this.editorKit.setField(this);
        replace(editorKit.getLatestDocument());

        setEditable(this.editorKit.isEditable());
    }

    public @Nullable EditorKit<?> getEditorKit()
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
}
