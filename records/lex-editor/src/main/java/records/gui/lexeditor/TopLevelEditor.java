package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import records.error.InternalException;
import records.gui.FixList;
import records.gui.FixList.FixInfo;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.ScrollPaneFill;
import utility.gui.TimedFocusable;

import java.util.ArrayList;
import java.util.function.Function;

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
public class TopLevelEditor<EXPRESSION extends StyledShowable, LEXER extends Lexer<EXPRESSION, CODE_COMPLETION_CONTEXT>, CODE_COMPLETION_CONTEXT extends CodeCompletionContext> implements TimedFocusable
{
    protected final EditorContent<EXPRESSION, CODE_COMPLETION_CONTEXT> content;
    protected final EditorDisplay display;
    private final ScrollPaneFill scrollPane;
    private final ErrorMessagePopup errorMessagePopup;
    private boolean hiding;

    // package-visible
    TopLevelEditor(@Nullable String originalContent, LEXER lexer, FXPlatformConsumer<@NonNull @Recorded EXPRESSION> onChange, String... styleClasses)
    {
        errorMessagePopup = new ErrorMessagePopup();
        content = new EditorContent<>(originalContent == null ? "" : originalContent, lexer);
        display = Utility.later(new EditorDisplay(content, n -> FXUtility.keyboard(this).errorMessagePopup.triggerFix(n), this));
        if (originalContent != null)
            display.markAsPreviouslyFocused();
        scrollPane = new ScrollPaneFill(new StackPane(display))  {
            @Override
            @OnThread(Tag.FX)
            public void requestFocus()
            {
            }
        };
        StackPane.setMargin(display, new Insets(2));
        // This also allows popup to take on styles:
        FXUtility.onceNotNull(scrollPane.sceneProperty(), scene -> {
            scene.getStylesheets().add(FXUtility.getStylesheet("expression-editor.css"));
        });
        scrollPane.setMinHeight(65.0);
        scrollPane.setPrefWidth(600.0);
        scrollPane.setAlwaysFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("top-level-editor");
        scrollPane.getStyleClass().addAll(styleClasses);
        FXUtility.addChangeListenerPlatformNN(display.focusedProperty(), focused -> {
            FXUtility.setPseudoclass(scrollPane, "focus-within", focused);
        });
        content.addChangeListener(() -> {
            onChange.consume(Utility.later(this).save());
        });
        content.addCaretPositionListener(errorMessagePopup::caretMoved);
        content.addCaretPositionListener((@CanonicalLocation Integer n) -> {
            display.showCompletions(content.getLexerResult().getCompletionsFor(n));
        });
        onChange.consume(save());
        FXUtility.onceNotNull(display.sceneProperty(), s -> showAllErrors());
    }

    public String _test_getRawText()
    {
        return content.getText();
    }

    public void setDisable(boolean disabled)
    {
        display.setDisable(disabled);
    }

    void mouseHover(@Nullable ImmutableList<ErrorDetails> hoveringOn)
    {
        errorMessagePopup.mouseHover(hoveringOn);
    }

    public static enum Focus { LEFT, RIGHT };

    public void focus(Focus side)
    {
        content.positionCaret(side);
        display.requestFocus();
    }
    
    public boolean isFocused()
    {
        return display.isFocused();
    }

    public @Recorded @NonNull EXPRESSION save(@UnknownInitialization(TopLevelEditor.class) TopLevelEditor<EXPRESSION, LEXER, CODE_COMPLETION_CONTEXT> this)
    {
        return content.getLexerResult().result;
    }

    public final Node getContainer()
    {
        return scrollPane;
    }
    
    public void cleanup()
    {
        hiding = true;
        errorMessagePopup.hidePopup(true);
    }

    protected void parentFocusRightOfThis(Either<Focus, Integer> side, boolean becauseOfTab)
    {
        // By default, do nothing
    }
    
    // Do we have any errors (including those currently suppressed)
    public boolean hasErrors()
    {
        return display.hasErrors();
    }
    
    public void showAllErrors(@UnknownInitialization(TopLevelEditor.class) TopLevelEditor<EXPRESSION, LEXER, CODE_COMPLETION_CONTEXT> this)
    {
        display.showAllErrors();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public long lastFocusedTime()
    {
        return display.lastFocusedTime();
    }

    private Function<@NonNull TextQuickFix, @NonNull FixInfo> makeFixInfo()
    {
        return f -> new FixInfo(f.getTitle(), f.getCssClasses(), () -> {
            try
            {
                Pair<CanonicalSpan, String> replacement = f.getReplacement();
                content.replaceText(replacement.getFirst().start, replacement.getFirst().end, replacement.getSecond());
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
        });
    }

    // Interface to access a singleton-per-editor error-displayer.
    // Lets us hide the other functionality from people using the class
    /*
    @OnThread(Tag.FXPlatform)
    public static interface ErrorMessageDisplayer
    {
        void mouseHoverEnded(Node hoverEndedOn);

        void mouseHoverBegan(@Nullable ErrorInfo errorInfo, Node hoverBeganOn);

        //void updateError(@Nullable ErrorInfo errorInfo, @SourceLocation int newCaretPos, Node... possibleHoverNodes);

        void hidePopup(boolean immediate);

        void keyboardFocusEntered(@Nullable ErrorInfo errorInfo, ImmutableList<ErrorInfo> adjacentErrors);

        void keyboardFocusExited(@Nullable ErrorInfo errorInfo, @SourceLocation int newCaretPos);
    }
    */


    /**
     * A popup to show error messages.  This is shared between all the items in the same top-level editor.
     *
     * The rules for anchoring and showing are as follows:
     *   - If there is no mouse hover, the error for the current keyboard position is shown, if any.
     *   - If keyboard moves, the mouse hover is cancelled until mouse moves.
     *   - If mouse moves to hover over an error, this is shown instead of keyboard error.
     *   - Quick fixes are only shown for the keyboard position.  If mouse hover has fixes, says "click to show fixes".
     *     (Because moving mouse away may cancel that message, frustrating to users if they go to try to click.
     *     Clicking causes keyboard focus which is more persistent.)
     */
    @OnThread(Tag.FXPlatform)
    private class ErrorMessagePopup extends PopOver //implements ErrorMessageDisplayer
    {
        private @Nullable Pair<StyledString, ImmutableList<TextQuickFix>> keyboardErrorInfo;
        private @Nullable Pair<StyledString, ImmutableList<TextQuickFix>> mouseErrorInfo;

        private final TextFlow errorLabel;
        private final FixList fixList;

        // null when definitely stopped.
        private @Nullable Animation hidingAnimation;

        public ErrorMessagePopup()
        {
            setDetachable(false);
            getStyleClass().add("expression-info-popup");
            setArrowLocation(ArrowLocation.BOTTOM_CENTER);
            // If we let the position vary to fit on screen, we end up with the popup bouncing in and out
            // as the mouse hovers on item then on popup then hides.  Better to let the item be off-screen
            // and let the user realise they need to move things about a bit:
            setAutoFix(false);
            // It's the skin that binds the height, so we must unbind after the skin is set:
            FXUtility.onceNotNull(skinProperty(), skin -> {
                // By default, the min width and height are the same, to allow for arrow + corners.
                // But we know arrow is on bottom, so we don't need such a large min height:
                getRoot().minHeightProperty().unbind();
                getRoot().setMinHeight(20.0);
            });

            //Log.debug("Showing error [initial]: \"" + errorMessage.get().toPlain() + "\"");
            errorLabel = new TextFlow();
            errorLabel.getStyleClass().add("expression-info-error");
            errorLabel.managedProperty().bind(errorLabel.visibleProperty());

            fixList = new FixList(ImmutableList.of());

            BorderPane container = new BorderPane(errorLabel, null, null, fixList, null);
            container.getStyleClass().add("expression-info-content");

            setContentNode(container);
            //FXUtility.addChangeListenerPlatformNN(detachedProperty(), b -> updateShowHide(false));
            // Have to put this on the scene because of all the window decorations and shadows:
            if (getScene() != null)
            {
                getScene().addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.MIDDLE)
                    {
                        // Will come back if user hovers or moves keyboard:
                        keyboardErrorInfo = null;
                        mouseErrorInfo = null;
                        FXUtility.mouse(this).updateShowHide(true);
                        e.consume();
                    }
                });
            }
            container.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
                FXUtility.mouse(this).cancelHideAnimation();
            });
            container.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
                if (keyboardErrorInfo == null)
                    FXUtility.mouse(this).hidePopup(false);
            });

        }

        // Can't have an ensuresnull check
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void hidePopup(boolean immediately)
        {
            // Whether we hide immediately or not, stop any current animation:
            cancelHideAnimation();

            if (immediately)
            {
                super.hide(Duration.ZERO);
            }
            else
            {

                hidingAnimation = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(opacityProperty(), 1.0)),
                        new KeyFrame(Duration.millis(2000), new KeyValue(opacityProperty(), 0.0))
                );
                hidingAnimation.setOnFinished(e -> {
                    if (isShowing())
                    {
                        super.hide(Duration.ZERO);
                    }
                });
                hidingAnimation.playFromStart();
            }
        }


        private void showPopup()
        {
            // Shouldn't be non-null already, but just in case:
            if (!isShowing() && !hiding)
            {
                Log.debug("Showing ErrorMessagePopup");
                show(scrollPane);
                //org.scenicview.ScenicView.show(getScene());
            }
        }

        private void cancelHideAnimation()
        {
            if (hidingAnimation != null)
            {
                hidingAnimation.stop();
                hidingAnimation = null;
            }
            setOpacity(1.0);
        }

        // Updates its showing status based on all the relevant variables
        private void updateShowHide(boolean hideImmediately)
        {
            //Log.logStackTrace("updateShowHide focus " + focused + " mask " + maskingErrors.get());
            @Nullable Pair<StyledString, ImmutableList<TextQuickFix>> errorInfo = null;
            if (keyboardErrorInfo != null)
                errorInfo = keyboardErrorInfo;
            else if (mouseErrorInfo != null)
                errorInfo = mouseErrorInfo;

            if (errorInfo != null)
            {
                // Make sure to cancel any hide animation:
                cancelHideAnimation();
                show(errorInfo);
                showPopup();
            }
            else
            {
                hidePopup(hideImmediately);
            }
        }

        private void show(Pair<StyledString, ImmutableList<TextQuickFix>> errorInfo)
        {
            errorLabel.getChildren().setAll(errorInfo.getFirst().toGUI().toArray(new Node[0]));
            errorLabel.setVisible(!errorInfo.getFirst().toPlain().isEmpty());
            fixList.setFixes(Utility.mapListI(errorInfo.getSecond(), makeFixInfo()));
        }

        /*
        @Override
        public void updateError(@Nullable ErrorInfo errorInfo, @SourceLocation int newCaretPos, Node... possibleHoverNodes)
        {
            if (this.keyboardErrorInfo != null && this.keyboardErrorInfo.getSecond() == keyboardFocus)
            {
                // Update keyboard error
                this.keyboardErrorInfo = errorInfo == null ? null : this.keyboardErrorInfo.replaceFirst(errorInfo);
            }

            // Can happen after a save; error was wiped but
            // keyboard focus remains:
            if (this.keyboardErrorInfo == null && errorInfo != null &&  keyboardFocus.isFocused())
            {
                this.keyboardErrorInfo = new Pair<>(errorInfo, keyboardFocus);
            }

            // Not else; both may happen:
            List<Node> hoverNodes = Arrays.asList(possibleHoverNodes);
            if (this.mouseErrorInfo != null && Utility.containsRef(hoverNodes, this.mouseErrorInfo.getSecond()))
            {
                // Update mouse error
                this.mouseErrorInfo = errorInfo == null ? null : this.mouseErrorInfo.replaceFirst(errorInfo);
            }

            updateShowHide(true);
        }
        */
        
        public void caretMoved(@CanonicalLocation int newCaretPos)
        {
            ImmutableList<ErrorDetails> allErrors = content.getErrors();
            ArrayList<StyledString> errors = new ArrayList<>();
            ImmutableList.Builder<TextQuickFix> fixes = ImmutableList.builder();
            Log.debug("Caret: " + newCaretPos + " errors: " + allErrors.size());
            for (ErrorDetails error : allErrors)
            {
                Log.debug("  " + error.location + " " + error.error.toPlain());
                if (error.location.touches(newCaretPos))
                {
                    if (error.caretHasLeftSinceEdit)
                    {
                        errors.add(error.error);
                        fixes.addAll(error.quickFixes);
                    }
                }
                else
                {
                    error.caretHasLeftSinceEdit = true;
                }
            }
            
            if (errors.isEmpty())
                keyboardErrorInfo = null;
            else
                keyboardErrorInfo = new Pair<>(errors.stream().collect(StyledString.joining("\n")), fixes.build());
            
            updateShowHide(true);
        }

        public void mouseHover(@Nullable ImmutableList<ErrorDetails> errorInfo)
        {
            if (errorInfo == null || errorInfo.isEmpty())
                mouseErrorInfo = null;
            else
            {
                mouseErrorInfo = new Pair<>(errorInfo.stream().map(e -> e.error).collect(StyledString.joining("\n")), errorInfo.stream().flatMap(e -> e.quickFixes.stream()).collect(ImmutableList.<TextQuickFix>toImmutableList()));
            }
            updateShowHide(errorInfo != null);
        }

        public void triggerFix(int fixIndex)
        {
            if (fixIndex >= 0 && fixIndex < fixList.getFixes().size())
            {
                fixList.getFixes().get(fixIndex).executeFix.run();
                hidePopup(true);
            }
        }
    }

}
