package records.gui.lexeditor;

import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.TextFlow;
import org.apache.commons.lang3.SystemUtils;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;


public class EditorDisplay extends TextFlow
{
    private final EditorContent<?> content;
    
    public EditorDisplay(EditorContent<?> theContent)
    {
        this.content = theContent;
        getStyleClass().add("editor-display");
        
        addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            FXUtility.mouse(this).requestFocus();
            event.consume();
        });
        
        addEventHandler(KeyEvent.KEY_TYPED, keyEvent -> {
            // Borrowed from TextInputControlBehavior:
            // Sometimes we get events with no key character, in which case
            // we need to bail.
            String character = keyEvent.getCharacter();
            if (character.length() == 0)
                return;

            // Filter out control keys except control+Alt on PC or Alt on Mac
            if (keyEvent.isControlDown() || keyEvent.isAltDown() || (SystemUtils.IS_OS_MAC && keyEvent.isMetaDown()))
            {
                if (!((keyEvent.isControlDown() || SystemUtils.IS_OS_MAC) && keyEvent.isAltDown()))
                    return;
            }

            // Ignore characters in the control range and the ASCII delete
            // character as well as meta key presses
            if (character.charAt(0) > 0x1F
                    && character.charAt(0) != 0x7F
                    && !keyEvent.isMetaDown()) // Not sure about this one (Note: this comment is in JavaFX source)
            {
                this.content.replaceText(this.content.getCaretPosition(), this.content.getCaretPosition(), character);
                // TODO move anchor to caret
            }
        });
    }

    // How many right presses (positive) or left (negative) to
    // reach nearest end of given content?
    @OnThread(Tag.FXPlatform)
    public int _test_getCaretMoveDistance(String targetContent)
    {
        return content._test_getCaretMoveDistance(targetContent);
    }
}
