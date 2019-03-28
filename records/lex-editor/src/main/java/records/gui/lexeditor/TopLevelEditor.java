package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.gui.expressioneditor.ClipboardSaver;
import styled.StyledShowable;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Utility;
import utility.gui.ScrollPaneFill;

/**
 * An editor is a wrapper around an editable TextFlow.  The distinctive feature is that
 * the real content of the editor may not be precisely what is shown on screen, and that
 * the caret cannot necessarily occupy all visible positions in the editor.
 * 
 * The document is immediately lexed whenever it is changed to provide syntax highlighting, readjustment
 * of available caret positions, recalculation of context for autocomplete.
 * 
 * @param <EXPRESSION>
 * @param <LEXER>
 */
public abstract class TopLevelEditor<EXPRESSION extends StyledShowable, LEXER extends Lexer>
{
    protected final EditorContent content;
    private final EditorDisplay display;
    private final ScrollPaneFill scrollPane;

    // package-visible
    TopLevelEditor(String originalContent, LEXER lexer, FXPlatformConsumer<@NonNull EXPRESSION> onChange)
    {
        content = new EditorContent(originalContent, lexer);
        display = new EditorDisplay(content);
        scrollPane = new ScrollPaneFill(display);
        content.addChangeListener(() -> {
            onChange.consume(Utility.later(this).save());
        });
    }

    public static enum Focus { LEFT, RIGHT };

    public void focus(Focus side)
    {
        content.positionCaret(side);
        display.requestFocus();
    }

    public abstract @Recorded EXPRESSION save();

    public final Node getContainer()
    {
        return scrollPane;
    }
    
    public void cleanup()
    {
        
    }

    protected void parentFocusRightOfThis(Either<Focus, Integer> side, boolean becauseOfTab)
    {
        // By default, do nothing
    }
}
