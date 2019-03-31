package records.gui.lexeditor;

import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;
import records.gui.lexeditor.TopLevelEditor.Focus;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.HelpfulTextFlow;

import java.util.OptionalInt;

@OnThread(Tag.FXPlatform)
public final class EditorDisplay extends HelpfulTextFlow
{
    private final EditorContent<?, ?> content;
    private final LexAutoComplete autoComplete;
    private final TopLevelEditor<?, ?, ?> editor;

    public EditorDisplay(EditorContent<?, ?> theContent, FXPlatformConsumer<Integer> triggerFix, @UnknownInitialization TopLevelEditor<?, ?, ?> editor)
    {
        this.autoComplete = Utility.later(new LexAutoComplete(this));
        this.content = theContent;
        this.editor = Utility.later(editor);
        getStyleClass().add("editor-display");
        setFocusTraversable(true);
        
        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            if (!focused)
                showCompletions(null);
        });
        
        addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            FXUtility.mouse(this).requestFocus();
            event.consume();
        });
        
        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            OptionalInt fKey = FXUtility.FKeyNumber(keyEvent.getCode());
            if (keyEvent.isShiftDown() && fKey.isPresent())
            {
                // 1 is F1, but should trigger fix zero:
                triggerFix.consume(fKey.getAsInt() - 1);
            }
            
            @SourceLocation int[] caretPositions = content.getValidCaretPositions();
            int caretPosIndex = content.getCaretPosAsValidIndex();
            int caretPosition = content.getCaretPosition();
            switch (keyEvent.getCode())
            {
                case LEFT:
                    if (caretPosIndex > 0)
                        content.positionCaret(caretPositions[caretPosIndex - 1]);
                    break;
                case RIGHT:
                    if (caretPosIndex + 1 < caretPositions.length)
                        content.positionCaret(caretPositions[caretPosIndex + 1]);
                    break;
                case DOWN:
                    if (autoComplete.isShowing())
                        autoComplete.down();
                    break;
                case BACK_SPACE:
                    if (caretPosition > 0)
                        content.replaceText(caretPosition - 1, caretPosition, "");
                    break;
                case DELETE:
                    if (caretPosition < content.getText().length())
                        content.replaceText(caretPosition, caretPosition + 1, "");
                    break;
                case V:
                    if (keyEvent.isShortcutDown())
                    {
                        content.replaceText(caretPosition, caretPosition, Clipboard.getSystemClipboard().getString());
                    }
                    break;
                case ESCAPE:
                    showCompletions(null);
                    break;
                case TAB:
                    if (autoComplete.isShowing())
                        break; // TODO select completion
                    else
                        this.editor.parentFocusRightOfThis(Either.left(Focus.LEFT), true);
                    break;
            }
            keyEvent.consume();
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
        
        content.addChangeListener(() -> render());
        content.addCaretPositionListener(c -> render());
        render();
    }

    private void render()
    {
        getChildren().setAll(new Text(content.getText()));
    }
    
    void showCompletions(@Nullable ImmutableList<LexCompletion> completions)
    {
        if (completions != null)
            autoComplete.show(completions);
        else
            autoComplete.hide();
    }

    // How many right presses (positive) or left (negative) to
    // reach nearest end of given content?
    @OnThread(Tag.FXPlatform)
    public int _test_getCaretMoveDistance(String targetContent)
    {
        return content._test_getCaretMoveDistance(targetContent);
    }

    public Point2D getCaretBottomOnScreen()
    {
        return localToScreen(getClickPosFor(content.getCaretPosition(), VPos.BOTTOM, new Dimension2D(0, 0)).getFirst());
    }

    public int _test_getCaretPosition()
    {
        return content.getCaretPosition();
    }

    @SuppressWarnings("units")
    public void _test_positionCaret(int caretPos)
    {
        content.positionCaret(caretPos);
    }
    
    public TopLevelEditor<?, ?, ?> _test_getEditor()
    {
        return editor;
    }
}
