package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sun.javafx.scene.text.HitInfo;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.DisplaySpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.completion.LexAutoComplete;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.TopLevelEditor.Focus;
import records.gui.lexeditor.completion.LexCompletionGroup;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TextEditorBase;
import utility.gui.TimedFocusable;

import java.util.BitSet;
import java.util.OptionalInt;

@OnThread(Tag.FXPlatform)
public final class EditorDisplay extends TextEditorBase implements TimedFocusable
{
    private boolean hasBeenFocused = false;
    private long lastFocusLeft;

    @OnThread(Tag.FXPlatform)
    public static class TokenBackground extends Style<TokenBackground>
    {
        private final ImmutableList<String> styleClasses;

        @OnThread(Tag.Any)
        public TokenBackground(ImmutableList<String> styleClasses)
        {
            super(TokenBackground.class);
            this.styleClasses = styleClasses;
        }


        @Override
        protected @OnThread(Tag.FXPlatform) void style(Text t)
        {
            // We don't style the text directly
        }

        @Override
        @OnThread(Tag.Any)
        protected TokenBackground combine(TokenBackground with)
        {
            return new TokenBackground(Utility.concatI(styleClasses, with.styleClasses));
        }

        @Override
        @OnThread(Tag.Any)
        protected boolean equalsStyle(TokenBackground item)
        {
            return styleClasses.equals(item.styleClasses);
        }
    }
    
    private final EditorContent<?, ?> content;
    private final LexAutoComplete autoComplete;
    private final TopLevelEditor<?, ?, ?> editor;

    public EditorDisplay(EditorContent<?, ?> theContent, FXPlatformConsumer<Integer> triggerFix, @UnknownInitialization TopLevelEditor<?, ?, ?> editor)
    {
        super(ImmutableList.of());
        this.autoComplete = Utility.later(new LexAutoComplete(this, c -> Utility.later(this).triggerSelection(c)));
        this.content = theContent;
        this.editor = Utility.later(editor);
        getStyleClass().add("editor-display");
        setFocusTraversable(true);
        
        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            if (!focused)
            {
                showCompletions(null);
                lastFocusLeft = System.currentTimeMillis();
            }
            focusChanged(focused);
        });
        
        addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            FXUtility.mouse(this).requestFocus();
            HitInfo hitInfo = hitTest(event.getX(), event.getY());
            if (hitInfo != null)
            {
                @SuppressWarnings("units")
                @DisplayLocation int insertionIndex = hitInfo.getInsertionIndex();
                content.positionCaret(content.mapDisplayToContent(insertionIndex, !hitInfo.isLeading()), true);
            }
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            HitInfo hitInfo = hitTest(event.getX(), event.getY());
            if (hitInfo != null)
            {
                @SuppressWarnings("units")
                @DisplayLocation int insertionIndex = hitInfo.getInsertionIndex();
                content.positionCaret(content.mapDisplayToContent(insertionIndex, !hitInfo.isLeading()), false);
            }
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            @Nullable HitInfo hit = hitTest(event.getX(), event.getY());
            if (hit != null)
            {
                FXUtility.mouse(editor).mouseHover(hasBeenFocused ? content.getErrors().stream().filter(e -> {
                    @SuppressWarnings("units")
                    @DisplayLocation int displayIndex = hit.getCharIndex();
                    return e.displayLocation != null && e.displayLocation.touches(displayIndex) && e.caretHasLeftSinceEdit;
                }).collect(ImmutableList.<ErrorDetails>toImmutableList()) : null);
            }
            else
            {
                FXUtility.mouse(editor).mouseHover(null);
            }
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            FXUtility.mouse(editor).mouseHover(null);
        });
        
        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            OptionalInt fKey = FXUtility.FKeyNumber(keyEvent.getCode());
            if (keyEvent.isShiftDown() && fKey.isPresent())
            {
                // 1 is F1, but should trigger fix zero:
                triggerFix.consume(fKey.getAsInt() - 1);
            }
            
            @CanonicalLocation int[] caretPositions = content.getValidCaretPositions();
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
                case UP:
                    if (autoComplete.isShowing())
                        autoComplete.up();
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
                case X:
                case C:
                    if (keyEvent.isShortcutDown())
                    {
                        if (content.getAnchorPosition() != content.getCaretPosition())
                        {
                            Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, content.getText().substring(Math.min(content.getCaretPosition(), content.getAnchorPosition()), Math.max(content.getCaretPosition(), content.getAnchorPosition()))));
                            if (keyEvent.getCode() == KeyCode.X)
                                content.replaceSelection("");
                        }
                    }
                    break;
                case V:
                    if (keyEvent.isShortcutDown())
                    {
                        String clip = Clipboard.getSystemClipboard().getString();
                        if (clip != null && !clip.isEmpty())
                            content.replaceSelection(clip);
                    }
                    break;
                case ESCAPE:
                    showCompletions(null);
                    break;
                case ENTER:
                    if (autoComplete.isShowing())
                        autoComplete.getSelectedCompletion().ifPresent(this::triggerSelection);
                    else
                        return;
                    break;
                case TAB:
                    if (autoComplete.isShowing())
                        autoComplete.getSelectedCompletion().ifPresent(this::triggerSelection);
                    else
                        this.editor.parentFocusRightOfThis(Either.left(Focus.LEFT), true);
                    break;
            }
            keyEvent.consume();
        });
        
        addEventHandler(KeyEvent.KEY_TYPED, keyEvent -> {
            if (FXUtility.checkKeyTyped(keyEvent))
            {
                String character = keyEvent.getCharacter();
                if ("({[".contains(character) && !content.suppressBracketMatch(content.getCaretPosition()) && content.getCaretPosition() == content.getAnchorPosition())
                {
                    if (character.equals("("))
                        this.content.replaceSelection("()");
                    else if (character.equals("["))
                        this.content.replaceSelection("[]");
                    else
                        this.content.replaceSelection("{}");
                    @SuppressWarnings("units")
                    @CanonicalLocation int one = 1;
                    content.positionCaret(this.getCaretPosition() - one, true);
                }
                else if (")}]".contains(character) && content.getCaretPosition() < content.getText().length() && content.getText().charAt(content.getCaretPosition()) == character.charAt(0) && content.areBracketsBalanced())
                {
                    // Overtype instead
                    @SuppressWarnings("units")
                    @CanonicalLocation int one = 1;
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

    @Override
    @OnThread(Tag.FXPlatform)
    public long lastFocusedTime()
    {
        return isFocused() ? System.currentTimeMillis() : lastFocusLeft;
    }

    public void markAsPreviouslyFocused()
    {
        hasBeenFocused = true;
        render(false);
    }

    @Override
    public @OnThread(value = Tag.FXPlatform) void focusChanged(boolean focused)
    {
        super.focusChanged(focused);
        if (focused)
        {
            hasBeenFocused = true;
            content.notifyCaretPositionListeners();
        }
        else
            showAllErrors();
    }

    @SuppressWarnings("units")
    private void selectAll()
    {
        content.positionCaret(0, true);
        content.positionCaret(content.getText().length(), false);
    }

    @SuppressWarnings("units")
    private void triggerSelection(LexCompletion p)
    {
        content.replaceText(p.startPos, content.getCaretPosition(), p.content);
        content.positionCaret(p.startPos + p.relativeCaretPos, true);
    }

    private void render(boolean contentChanged)
    {
        if (contentChanged)
            textFlow.getChildren().setAll(content.getDisplayText());
        if (caretAndSelectionNodes != null)
            caretAndSelectionNodes.queueUpdateCaretShape();
    }
    
    void showCompletions(@Nullable ImmutableList<LexCompletionGroup> completions)
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
    
    public void _test_queueUpdateCaret()
    {
        if (caretAndSelectionNodes != null)
            caretAndSelectionNodes.queueUpdateCaretShape();
    }

    public @Nullable Point2D getCaretBottomOnScreen()
    {
        return getCaretBottomOnScreen(content.getCaretPosition());
    }

    public @Nullable Point2D getCaretBottomOnScreen(@CanonicalLocation int caretPos)
    {
        // localToScreen can return null if not in window, hence the @Nullable return
        return localToScreen(textFlow.getClickPosFor(content.mapContentToDisplay(caretPos), VPos.BOTTOM, new Dimension2D(0, 0)).getFirst());
    }
    
    @OnThread(Tag.FXPlatform)
    public @CanonicalLocation int getCaretPosition()
    {
        return content.getCaretPosition();
    }
    
    public @OnThread(Tag.FXPlatform) int getAnchorPosition()
    {
        return content.getAnchorPosition();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public @DisplayLocation int getDisplayCaretPosition()
    {
        return content.getDisplayCaretPosition();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public @DisplayLocation int getDisplayAnchorPosition()
    {
        return content.getDisplayAnchorPosition();
    }

    @Override
    @SuppressWarnings("units")
    public @OnThread(Tag.FXPlatform) BitSet getErrorCharacters()
    {
        BitSet errorChars = new BitSet();
        for (ErrorDetails error : content.getErrors())
        {
            if (!hasBeenFocused)
            {
                error.caretHasLeftSinceEdit = false;
            }
            else if (error.caretHasLeftSinceEdit || !isFocused() || !error.location.touches(getCaretPosition()))
            {
                error.caretHasLeftSinceEdit = true;
                if (error.displayLocation == null)
                {
                    error.displayLocation = new DisplaySpan(content.mapContentToDisplay(error.location.start), content.mapContentToDisplay(error.location.end));
                }
                try
                {
                    if (error.displayLocation.start > error.displayLocation.end)
                        throw new InternalException("Invalid display location: " + error.displayLocation + " with error: " + error.error + " and content: " + content.getText());
                    errorChars.set(error.displayLocation.start, error.displayLocation.end);
                }
                catch (InternalException e)
                {
                    Log.log(e);
                }
            }
        }
        return errorChars;
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

    public Bounds _test_getCaretBounds(@CanonicalLocation int pos)
    {
        try
        {
            return textFlow.localToScreen(new Path(textFlow.getInternalTextLayout().getCaretShape(content.mapContentToDisplay(pos), true, 0, 0)).getBoundsInParent());
        }
        catch (Exception e)
        {
            return new BoundingBox(0, 0, 0, 0);
        }
    }
    
    public boolean hasErrors()
    {
        return !content.getErrors().isEmpty();
    }
    
    public void showAllErrors()
    {
        for (ErrorDetails error : content.getErrors())
        {
            error.caretHasLeftSinceEdit = true;
        }
        render(false);
    }

    @Override
    public @OnThread(Tag.FXPlatform) ImmutableList<BackgroundInfo> getBackgrounds()
    {
        ImmutableList.Builder<BackgroundInfo> r = ImmutableList.builder();
        int curPos = 0;
        for (Pair<ImmutableList<Style<?>>, String> member : content.getDisplay().getMembers())
        {
            for (Style<?> style : member.getFirst())
            {
                if (style instanceof TokenBackground)
                {
                    r.add(new BackgroundInfo(curPos, curPos + member.getSecond().length(), ((TokenBackground)style).styleClasses));
                }
            }
            
            curPos += member.getSecond().length();
        }
        return r.build();
    }

    public ImmutableList<ErrorDetails> _test_getErrors()
    {
        return content.getErrors();
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Point2D translateHit(double x, double y)
    {
        return new Point2D(x, y);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        double wholeTextHeight = textFlow.prefHeight(getWidth());

        CaretAndSelectionNodes cs = this.caretAndSelectionNodes;

        textFlow.resizeRelocate(0, 0, getWidth(), wholeTextHeight);
   
        if (cs != null)
        {
            cs.fadeOverlay.resize(getWidth(), getHeight());
            cs.caretShape.setLayoutX(0);
            cs.caretShape.setLayoutY(0);
            cs.selectionShape.setLayoutX(0);
            cs.selectionShape.setLayoutY(0);
            cs.inverter.setLayoutX(0);
            cs.inverter.setLayoutY(0);
            cs.inverterPane.resizeRelocate(0, 0, getWidth(), getHeight());
            cs.selectionPane.resizeRelocate(0, 0, getWidth(), getHeight());
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMinHeight(double width)
    {
        return textFlow.prefHeight(width);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        return textFlow.prefHeight(width);
    }
}
