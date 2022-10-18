/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.gui.FixList;
import xyz.columnal.utility.gui.FixList.FixInfo;
import xyz.columnal.gui.lexeditor.EditorContent.CaretMoveReason;
import xyz.columnal.gui.lexeditor.EditorContent.CaretPositionListener;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.QuickFix.QuickFixAction;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumer;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.ScrollPaneFill;
import xyz.columnal.utility.gui.TimedFocusable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map.Entry;
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
    private final InformationPopup informationPopup;
    private final TypeManager typeManager;
    private boolean hiding;

    // package-visible
    TopLevelEditor(@Nullable String originalContent, LEXER lexer, TypeManager typeManager, FXPlatformConsumer<@NonNull @Recorded EXPRESSION> onChange, String... styleClasses)
    {
        this.typeManager = typeManager;
        informationPopup = new InformationPopup();
        content = new EditorContent<>(originalContent == null ? "" : originalContent, lexer);
        display = Utility.later(new EditorDisplay(content, n -> FXUtility.keyboard(this).informationPopup.triggerFix(n), this));
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
        Dimension2D dim = getEditorDimension();
        scrollPane.setMinHeight(dim.getHeight());
        scrollPane.setPrefWidth(dim.getWidth());
        scrollPane.setAlwaysFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("top-level-editor");
        scrollPane.getStyleClass().addAll(styleClasses);
        FXUtility.addChangeListenerPlatformNN(display.focusedProperty(), focused -> {
            FXUtility.setPseudoclass(scrollPane, "focus-within", focused);
            display.showCompletions(content.getLexerResult().getCompletionsFor(content.getCaretPosition()));
        });
        content.addChangeListener(() -> {
            onChange.consume(Utility.later(this).save(false));
        });
        content.addCaretPositionListener(informationPopup::caretMoved);
        content.addCaretPositionListener(new CaretPositionListener()
        {
            @Override
            public void caretMoved(@CanonicalLocation int caretPos, CaretMoveReason caretMoveReason)
            {
                if (caretMoveReason != CaretMoveReason.FORCED_SAVE)
                {
                    display.showCompletions(content.getLexerResult().getCompletionsFor(caretPos));
                }
            }
        });
        onChange.consume(save(true));
        FXUtility.onceNotNull(display.sceneProperty(), s -> FXUtility.runAfter(() -> showAllErrors()));
    }

    // The width is preferred, the height is minimum
    protected Dimension2D getEditorDimension(@UnknownInitialization(Object.class) TopLevelEditor<EXPRESSION, LEXER, CODE_COMPLETION_CONTEXT> this)
    {
        return new Dimension2D(250.0, 25.0);
    }

    public String _test_getRawText()
    {
        return content.getText();
    }

    public void setDisable(boolean disabled)
    {
        scrollPane.setDisable(disabled);
        display.setDisable(disabled);
    }

    public void selectAll()
    {
        display.selectAll();
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

    public @Recorded @NonNull EXPRESSION save(@UnknownInitialization(TopLevelEditor.class) TopLevelEditor<EXPRESSION, LEXER, CODE_COMPLETION_CONTEXT> this, boolean forceSaveAsIfUnfocused)
    {
        if (forceSaveAsIfUnfocused)
        {
            content.forceSaveAsIfUnfocused();
            showAllErrors();
        }
        Log.debug("Saved: " + content.getLexerResult().result + " //" + forceSaveAsIfUnfocused);
        return content.getLexerResult().result;
    }

    public final Node getContainer()
    {
        return scrollPane;
    }
    
    public void cleanup()
    {
        hiding = true;
        informationPopup.hidePopup(true);
        display.cleanup();
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
                Either<QuickFixAction, Pair<CanonicalSpan, String>> actionOrReplacement = f.getReplacement();
                actionOrReplacement.eitherInt_(a -> {
                    @Nullable SimulationConsumer<Pair<@Nullable ColumnId, Expression>> toRun = a.doAction(typeManager, scrollPane.sceneProperty());
                    if (toRun != null)
                    {
                        @Nullable Pair<@Nullable ColumnId, Expression> latest = forceCloseDialog();
                        if (latest != null)
                            Workers.onWorkerThread("Moving Calculate column", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.moving.calculate"), () -> toRun.consume(latest)));
                    }
                    else
                    {
                        // Reprocess:
                        content.replaceWholeText(content.getText());
                    }
                }, replacement -> {
                    content.replaceText(replacement.getFirst().start, replacement.getFirst().end, replacement.getSecond());
                });
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
        });
    }

    // Its return type is quite hacky as it is specific to EditColumnExpressionDialog, but it gets the job done.
    // returns the current values of columnId and expression.
    @OnThread(Tag.FXPlatform)
    protected @Nullable Pair<@Nullable ColumnId, Expression> forceCloseDialog()
    {
        // Currently only implemented by EditColumnExpressionDialog
        return null;
    }

    public void setContent(String text)
    {
        this.content.replaceWholeText(text);
    }

    public boolean isMouseClickImmune()
    {
        return display.isMouseClickImmune();
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
    
    // Note -- these names are used in lower-case for styleclasses so change the CSS
    // if you change the names here.
    public static enum DisplayType
    {
        ERROR,
        WARNING,
        // Things that are like warnings but information, e.g.
        // this could be refactored, or this column is overridden in this calculate
        INFORMATION,
        // Information for entry.
        PROMPT;
    }


    /**
     * A popup to show information messages at the top of the editor.  This is shared between all the items in the same top-level editor.
     *
     * The popup only tracks caret position, not mouse hover.  Tracking both leads
     * to lots of awkward states and would require different content (e.g. entry
     * prompt should only show for keyboard).  So we keep it simple and just use
     * the caret.
     */
    @OnThread(Tag.FXPlatform)
    private final class InformationPopup extends PopOver //implements ErrorMessageDisplayer
    {
        private final EnumMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>> displays = new EnumMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>>(DisplayType.class);

        private final TextFlow textFlow = new TextFlow();
        private final FixList fixList = new FixList(ImmutableList.of());

        // null when definitely stopped.
        private @Nullable Animation hidingAnimation;
        
        private @MonotonicNonNull DisplayType curDisplayType = null;
        private Pair<StyledString, ImmutableList<TextQuickFix>> curContent = new Pair<>(StyledString.s(""), ImmutableList.of());

        public InformationPopup()
        {
            setDetachable(false);
            getStyleClass().add("expression-info-popup");
            setArrowSize(0);
            setArrowIndent(0);
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
            textFlow.getStyleClass().add("expression-info-error");
            textFlow.managedProperty().bind(textFlow.visibleProperty());

            BorderPane container = new BorderPane(textFlow, null, null, fixList, null);
            container.getStyleClass().add("expression-info-content");

            setContentNode(container);
            //FXUtility.addChangeListenerPlatformNN(detachedProperty(), b -> updateShowHide(false));
            // Have to put this on the scene because of all the window decorations and shadows:
            if (getScene() != null)
            {
                getScene().addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.MIDDLE)
                    {
                        // Will come back if user moves keyboard:
                        displays.clear();
                        FXUtility.mouse(this).updateShowHide(true);
                        e.consume();
                    }
                });
            }
            container.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
                FXUtility.mouse(this).cancelHideAnimation();
            });
            container.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
                if (displays.isEmpty())
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


        private void showPopup(boolean contentHasChanged)
        {
            if (!hiding && (!isShowing() || contentHasChanged) && scrollPane.localToScreen(0, 0) != null)
            {
                // Need to call show even if already showing in order
                // to fix the position if our contents have changed:
                //setAnimated(!isShowing());
                show(scrollPane);
                //setAnimated(true);
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
            // EnumMap returns entries in order of enum keys,
            // so this will get errors ahead of warnings ahead of information ahead of prompt, as we want:
            @Nullable Entry<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>> curDisplay = displays.entrySet().stream().findFirst().orElse(null);

            if (curDisplay != null && TopLevelEditor.this.isFocused())
            {
                // Make sure to cancel any hide animation:
                cancelHideAnimation();
                boolean contentChanged = setContent(curDisplay.getKey(), curDisplay.getValue());
                showPopup(contentChanged);
            }
            else
            {
                hidePopup(hideImmediately);
            }
        }

        // Returns true if content has changed as a result of the call
        private boolean setContent(DisplayType displayType, Pair<StyledString, ImmutableList<TextQuickFix>> errorInfo)
        {
            if (displayType.equals(curDisplayType) && errorInfo.getFirst().equals(curContent.getFirst())
                && sameFixes(curContent.getSecond(), errorInfo.getSecond()))
                return false;
            
            // We can't use pseudoclasses here because they don't seem to work properly
            // with popups, so fall back to adding/removing style-classes
            for (DisplayType dt : DisplayType.values())
            {
                if (dt.equals(displayType))
                {
                    if (!getStyleClass().contains(dt.name().toLowerCase()))
                        getStyleClass().add(dt.name().toLowerCase());
                }
                else
                    getStyleClass().remove(dt.name().toLowerCase());
            }
            textFlow.getChildren().setAll(errorInfo.getFirst().toGUI().toArray(new Node[0]));
            textFlow.setVisible(!errorInfo.getFirst().toPlain().isEmpty());
            fixList.setFixes(Utility.mapListI(errorInfo.getSecond(), makeFixInfo()));
            this.curDisplayType = displayType;
            this.curContent = errorInfo;
            return true;
        }

        private boolean sameFixes(ImmutableList<TextQuickFix> a, ImmutableList<TextQuickFix> b)
        {
            if (a.size() != b.size())
                return false;

            for (int i = 0; i < a.size(); i++)
            {
                if (!a.get(i).getTitle().equals(b.get(i).getTitle()))
                    return false;
                if (!a.get(i).getReplacementTarget().equals(b.get(i).getReplacementTarget()))
                    return false;
            }
            return true;
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
        
        public void caretMoved(@CanonicalLocation int newCaretPos, CaretMoveReason reason)
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
                displays.remove(DisplayType.ERROR);
            else
                displays.put(DisplayType.ERROR, new Pair<>(errors.stream().collect(StyledString.joining("\n")), fixes.build()));

            Node node = display;
            if (node.getScene() != null)
                node = node.getScene().getRoot();
            
            ImmutableMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>> infoAndPrompt = content.getDisplayFor(newCaretPos, node);
            displays.remove(DisplayType.PROMPT);
            displays.remove(DisplayType.INFORMATION);
            for (Entry<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>> entry : infoAndPrompt.entrySet())
            {
                displays.put(entry.getKey(), entry.getValue());
            }
            
            updateShowHide(true);
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
