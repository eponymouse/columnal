package records.gui.lexeditor;

import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import com.sun.javafx.scene.text.HitInfo;
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
import utility.gui.TextEditorBase;

import java.util.OptionalInt;

@OnThread(Tag.FXPlatform)
public final class EditorDisplay extends TextEditorBase
{
    private final EditorContent<?, ?> content;
    private final LexAutoComplete autoComplete;
    private final TopLevelEditor<?, ?, ?> editor;

    public EditorDisplay(EditorContent<?, ?> theContent, FXPlatformConsumer<Integer> triggerFix, @UnknownInitialization TopLevelEditor<?, ?, ?> editor)
    {
        super(ImmutableList.of());
        this.autoComplete = Utility.later(new LexAutoComplete(this));
        this.content = theContent;
        this.editor = Utility.later(editor);
        getStyleClass().add("editor-display");
        setFocusTraversable(true);
        
        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            if (!focused)
                showCompletions(null);
            focusChanged(focused);
        });
        
        addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            FXUtility.mouse(this).requestFocus();
            HitInfo hitInfo = hitTest(event.getX(), event.getY());
            if (hitInfo != null)
            {
                int insertionIndex = hitInfo.getInsertionIndex();
                content.positionCaret(content.mapDisplayToContent(insertionIndex), true);
            }
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        
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
                        content.positionCaret(caretPositions[caretPosIndex - 1], !keyEvent.isShiftDown());
                    break;
                case RIGHT:
                    if (caretPosIndex + 1 < caretPositions.length)
                        content.positionCaret(caretPositions[caretPosIndex + 1], !keyEvent.isShiftDown());
                    break;
                case HOME:
                    if (caretPositions.length > 0)
                        content.positionCaret(caretPositions[0], !keyEvent.isShiftDown());
                    break;
                case END:
                    if (caretPositions.length > 0)
                        content.positionCaret(caretPositions[caretPositions.length - 1], !keyEvent.isShiftDown());
                    break;
                case DOWN:
                    if (autoComplete.isShowing())
                        autoComplete.down();
                    break;
                case BACK_SPACE:
                    if (caretPosition != content.getAnchorPosition())
                        content.replaceSelection("");
                    else if (caretPosition > 0)
                        content.replaceText(caretPosition - 1, caretPosition, "");
                    break;
                case DELETE:
                    if (caretPosition != content.getAnchorPosition())
                        content.replaceSelection("");
                    else if (caretPosition < content.getText().length())
                        content.replaceText(caretPosition, caretPosition + 1, "");
                    break;
                case A:
                    if (keyEvent.isShortcutDown())
                    {
                        selectAll();
                    }
                    break;
                case V:
                    if (keyEvent.isShortcutDown())
                    {
                        content.replaceSelection(Clipboard.getSystemClipboard().getString());
                    }
                    break;
                case ESCAPE:
                    showCompletions(null);
                    break;
                case ENTER:
                    if (autoComplete.isShowing())
                        triggerSelection();
                    else
                        return;
                    break;
                case TAB:
                    if (autoComplete.isShowing())
                        triggerSelection();
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
                if ("({[".contains(character) && !content.suppressBracketMatch(content.getCaretPosition()) && content.getCaretPosition() == content.getAnchorPosition())
                {
                    if (character.equals("("))
                        this.content.replaceSelection("()");
                    else if (character.equals("["))
                        this.content.replaceSelection("[]");
                    else
                        this.content.replaceSelection("{}");
                    @SuppressWarnings("units")
                    @SourceLocation int one = 1;
                    content.positionCaret(this.getCaretPosition() - one, true);
                }
                else if (")}]".contains(character) && content.getCaretPosition() < content.getText().length() && content.getText().charAt(content.getCaretPosition()) == character.charAt(0) && content.areBracketsBalanced())
                {
                    // Overtype instead
                    @SuppressWarnings("units")
                    @SourceLocation int one = 1;
                    this.content.positionCaret(content.getCaretPosition() + one, true);
                }
                else
                {
                    this.content.replaceSelection(character);
                }
            }
        });
        
        content.addChangeListener(() -> render(true));
        content.addCaretPositionListener(c -> render(false));
        render(true);
    }

    @SuppressWarnings("units")
    private void selectAll()
    {
        content.positionCaret(0, true);
        content.positionCaret(content.getText().length(), false);
    }

    @SuppressWarnings("units")
    private void triggerSelection()
    {
        autoComplete.selectCompletion().ifPresent(p -> {
            content.replaceText(p.startPos, content.getCaretPosition(), p.content);
            content.positionCaret(p.startPos + p.relativeCaretPos, true);
        });
    }

    private void render(boolean contentChanged)
    {
        if (contentChanged)
            textFlow.getChildren().setAll(content.getDisplayText());
        if (caretAndSelectionNodes != null)
            caretAndSelectionNodes.queueUpdateCaretShape();
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
        return localToScreen(textFlow.getClickPosFor(content.getCaretPosition(), VPos.BOTTOM, new Dimension2D(0, 0)).getFirst());
    }

    public Point2D getCaretBottomOnScreen(int caretPos)
    {
        return localToScreen(textFlow.getClickPosFor(caretPos, VPos.BOTTOM, new Dimension2D(0, 0)).getFirst());
    }
    
    @OnThread(Tag.FXPlatform)
    public @SourceLocation int getCaretPosition()
    {
        return content.getCaretPosition();
    }
    
    public @OnThread(Tag.FXPlatform) int getAnchorPosition()
    {
        return content.getAnchorPosition();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public int getDisplayCaretPosition()
    {
        return content.getDisplayCaretPosition();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public int getDisplayAnchorPosition()
    {
        return content.getDisplayAnchorPosition();
    }

    @SuppressWarnings("units")
    public void _test_positionCaret(int caretPos)
    {
        content.positionCaret(caretPos, true);
    }
    
    public TopLevelEditor<?, ?, ?> _test_getEditor()
    {
        return editor;
    }
}
