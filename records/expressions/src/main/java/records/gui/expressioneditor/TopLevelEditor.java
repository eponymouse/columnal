package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.FixList;
import records.gui.FixList.FixInfo;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.gui.expressioneditor.ExpressionInfoDisplay.CaretSide;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.ScrollPaneFill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class TopLevelEditor<EXPRESSION extends StyledShowable, SAVER extends ClipboardSaver> extends ConsecutiveBase<EXPRESSION, SAVER>
{
    private final FlowPane container;
    private final ScrollPaneFill scrollPane;
    private final List<FXPlatformConsumer<Node>> focusListeners = new ArrayList<>();
    private final TypeManager typeManager;
    
    // Selections take place within one consecutive and go from one operand to another (inclusive):
    private @Nullable SelectionInfo<?, ?> selection;
    private @Nullable ConsecutiveChild<?, ?> curHoverDropTarget;
    private boolean selectionLocked;
    private final ErrorMessagePopup errorMessagePopup;
    private @Localized @Nullable String prompt = null;

    public TopLevelEditor(OperandOps<EXPRESSION, SAVER> operations, TypeManager typeManager, String... styleClasses)
    {
        super(operations, null, "");
        this.container = new TopLevelEditorFlowPane();
        this.errorMessagePopup = new ErrorMessagePopup();
        this.scrollPane = new ScrollPaneFill(container) {
            @Override
            @OnThread(Tag.FX)
            public void requestFocus()
            {
            }
        };
        scrollPane.getStyleClass().add("top-level-editor-scroll-pane");
        scrollPane.setAlwaysFitToWidth(true);
        this.typeManager = typeManager;

        container.getStyleClass().add("top-level-editor");
        container.getStyleClass().addAll(styleClasses);
        container.getStylesheets().add(FXUtility.getStylesheet("expression-editor.css"));
        // To allow popup to take on styles:
        FXUtility.onceNotNull(container.sceneProperty(), scene -> {
            scene.getStylesheets().add(FXUtility.getStylesheet("expression-editor.css"));
        });

        container.getChildren().setAll(nodes());
        FXUtility.listen(nodes(), c -> {
            container.getChildren().setAll(nodes());
        });

        FXUtility.onceNotNull(container.sceneProperty(), scene -> {
            FXUtility.addChangeListenerPlatform(scene.focusOwnerProperty(), owner -> {
                //Log.normalStackTrace("Focus now with: " + owner, 40);
                FXUtility.runAfter(() -> {
                    //Log.debug("Focus now with [2]: " + owner);
                    // We are in a run-after so check focus hasn't changed again:
                    if (scene.getFocusOwner() == owner)
                    {
                        Utility.later(this).focusChanged();
                    }
                });
            });
        });

        // If they click the background, focus the right-hand end of that row:
        // Note we use released here because we don't see press/click if a popup was showing when they clicked us:
        container.setOnMouseReleased(e -> {
            if (e.getClickCount() == 1 && e.getButton() == MouseButton.PRIMARY)
            {
                // We find the node that is rightmost in our row:
                
                // Maps absolute vertical difference from position
                // to a set of nodes
                class NodeInfo implements Comparable<NodeInfo>
                {
                    private final double verticalDist; // Zero if overlapping
                    private final double horizDist; // To nearest corner
                    private final ConsecutiveChild<?, ?> item;

                    NodeInfo(double verticalDist, double horizDist, ConsecutiveChild<?, ?> item)
                    {
                        this.verticalDist = verticalDist;
                        this.horizDist = horizDist;
                        this.item = item;
                    }

                    @Override
                    public int compareTo(NodeInfo o)
                    {
                        return Comparator.<NodeInfo, Double>comparing(n -> n.verticalDist).thenComparing(n -> n.horizDist).compare(this, o);
                    }
                }

                class NodeFinder implements LocatableVisitor
                {
                    private @MonotonicNonNull NodeInfo closest;
                    
                    @Override
                    public <C extends StyledShowable> void register(ConsecutiveChild<? extends C, ?> graphicalItem, Class<C> childType)
                    {
                        NodeInfo latest = graphicalItem.nodes().stream().map(node -> {
                            final double vertDist;
                            final double horizDist;
                            
                            Bounds boundsInScene = node.localToScene(node.getBoundsInLocal());
                            
                            if (e.getSceneY() < boundsInScene.getMinY())
                                vertDist = Math.abs(e.getSceneY() - boundsInScene.getMinY());
                            else if (e.getSceneY() > boundsInScene.getMaxY())
                                vertDist = Math.abs(e.getSceneY() - boundsInScene.getMaxY());
                            else
                                vertDist = 0;

                            if (e.getSceneX() < boundsInScene.getMinX())
                                horizDist = Math.abs(e.getSceneX() - boundsInScene.getMinX());
                            else if (e.getSceneX() > boundsInScene.getMaxX())
                                horizDist = Math.abs(e.getSceneX() - boundsInScene.getMaxX());
                            else
                                horizDist = 0;
                            
                            return new NodeInfo(vertDist, horizDist, graphicalItem);
                        }).min(Comparator.naturalOrder()).orElse(null);
                        if (latest != null)
                        {
                            if (closest == null)
                                closest = latest;
                            else if (latest.compareTo(closest) < 0)
                                closest = latest;
                        }
                    }
                };
                NodeFinder visitor = new NodeFinder();
                FXUtility.mouse(this).visitLocatable(visitor);
                
                if (visitor.closest != null)
                {
                    visitor.closest.item.getParent().focusRightOf(visitor.closest.item, Focus.RIGHT, false);
                }
                e.consume();
            }
        });
        container.addEventHandler(MouseEvent.ANY, e -> {
            e.consume();
        });

        //FXUtility.onceNotNull(container.sceneProperty(), s -> org.scenicview.ScenicView.show(s));

        /*
        FXUtility.enableDragTo(container, Collections.singletonMap(FXUtility.getTextDataFormat("Expression"), new DragHandler()
        {
            @Override
            @SuppressWarnings("initialization")
            public @OnThread(Tag.FXPlatform) void dragMoved(Point2D pointInScene)
            {
                Pair<ConsecutiveChild<? extends Expression, ?>, Double> nearest = findClosestDrop(pointInScene, Expression.class);
                if (curHoverDropTarget != null)
                    curHoverDropTarget.setHoverDropLeft(false);
                curHoverDropTarget = nearest.getFirst();
                curHoverDropTarget.setHoverDropLeft(true);
            }

            @Override
            @SuppressWarnings("initialization")
            public @OnThread(Tag.FXPlatform) boolean dragEnded(Dragboard dragboard, Point2D pointInScene)
            {
                @Nullable Object o = dragboard.getContent(FXUtility.getTextDataFormat("Expression"));
                if (o != null && o instanceof CopiedItems)
                {
                    // We need to find the closest drop point
                    Pair<ConsecutiveChild<? extends Expression, ?>, Double> nearest = findClosestDrop(pointInScene, Expression.class);
                    // Now we need to add the content:
                    //TODO work out if this is a null drag because everything would go to hell
                    // (Look if drag destination is inside selection?)
                    // Or can we stop it going to hell?
                    boolean dropped = nearest.getFirst().getParent().insertBefore(nearest.getFirst(), (CopiedItems) o);
                    // Tidy up any blanks:
                    if (dropped)
                        nearest.getFirst().getParent().focusChanged();
                    return dropped;
                }
                return false;
            }
        }));
        */
    }

    protected void loadContent(LoadableExpression<EXPRESSION, SAVER> startingValue, boolean unmaskErrors)
    {
        atomicEdit.set(true);
        children.setAll(startingValue.loadAsConsecutive(BracketedStatus.TOP_LEVEL).map(l -> l.load(this)).collect(Collectors.toList()));
        atomicEdit.set(false);
        if (unmaskErrors)
            unmaskErrors();
    }

    public TypeManager getTypeManager()
    {
        return typeManager;
    }

    @Override
    public boolean isFocused()
    {
        return childIsFocused();
    }

    @Override
    public void focusChanged()
    {
        super.focusChanged();
        getAllChildren().stream().flatMap(c -> c.nodes().stream()).filter(c -> c.isFocused()).findFirst().ifPresent(focused -> {
            focusListeners.forEach(l -> l.consume(focused));
        });
        FXUtility.setPseudoclass(container, "focus-within", childIsFocused());
    }

    @Override
    protected void selfChanged()
    {
        super.selfChanged();
        scrollPane.fillViewport();
    }

    public abstract @Recorded EXPRESSION save();

    public Node getContainer()
    {
        return scrollPane;
    }

    public @Nullable Window getWindow()
    {
        if (container.getScene() == null)
            return null;
        return container.getScene().getWindow();
    }
    @Override
    public TopLevelEditor<EXPRESSION, SAVER> getEditor()
    {
        return this;
    }

    public void addFocusListener(FXPlatformConsumer<Node> focusListener)
    {
        focusListeners.add(focusListener);
    }

    public Stream<ColumnReference> getAvailableColumnReferences()
    {
        return Stream.empty();
    }

    /**
     * @param before The child to add before, or null to add at the end
     * @param src The parseable structured source (i.e. full of @unfinished and so on,
     *            not just the raw text entered by the user)
     * Returns true if successful 
     */
    public final boolean addContent(@Nullable ConsecutiveChild<?, ?> before, String src)
    {
        try
        {
            @Nullable LoadableExpression<EXPRESSION, SAVER> parsed = parse(src);
            if (parsed == null)
                return false;
            else
            {
                int beforeIndex = before == null ? children.size() : Utility.indexOfRef(children, before);
                if (beforeIndex < 0)
                {
                    Log.error("Child " + before + " not found in " + children);
                    // Better to fix than crash:
                    beforeIndex = children.size();
                }
                atomicEdit.set(true);
                children.addAll(beforeIndex, parsed.loadAsConsecutive(BracketedStatus.TOP_LEVEL).map(l -> l.load(this)).collect(Collectors.toList()));
                atomicEdit.set(false);
                return true;
            }
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            return false;
        }
    }

    // Parses expression (with tokens, @unfinished etc) into an expression
    protected abstract @Nullable LoadableExpression<EXPRESSION, SAVER> parse(String src) throws InternalException, UserException;
    
    private static enum SelectionCaret
    {START, END}
    
    private class SelectionInfo<E extends StyledShowable, P extends ClipboardSaver>
    {
        private final ConsecutiveBase<E, P> parent;
        private final ConsecutiveChild<E, P> start;
        private final ConsecutiveChild<E, P> end;
        // The anchor is the opposite end to the caret:
        private final SelectionCaret caret;

        private SelectionInfo(ConsecutiveBase<E, P> parent, ConsecutiveChild<E, P> start, ConsecutiveChild<E, P> end, SelectionCaret caret)
        {
            this.parent = parent;
            this.start = start;
            this.end = end;
            this.caret = caret;
        }

        private SelectionInfo(ConsecutiveBase<E, P> parent, ConsecutiveChild<E, P> only)
        {
            this(parent, only, only, SelectionCaret.START);
        }

        public boolean contains(ConsecutiveChild<?, ?> item)
        {
            return parent.getChildrenFromTo(start, end).contains(item);
        }

        public @Nullable Map<DataFormat, Object> copyItems()
        {
            return parent.copyItems(start, end);
        }

        public void removeItems()
        {
            parent.removeItems(start, end);
        }

        public void markSelection(boolean selected, FXPlatformRunnable clearSelection)
        {
            // Must lock selection to avoid focus-loss from wiping it out:
            selectionLocked = true;
            parent.markSelection(start, end, selected, new Pair<>(getCaretNode(), clearSelection));
            selectionLocked = false;
        }

        public ConsecutiveChild<E, P> getCaretNode()
        {
            return caret == SelectionCaret.START ? start : end;
        }

        public ConsecutiveChild<E, P> getAnchorNode()
        {
            return caret == SelectionCaret.START ? end : start;
        }
    }


    public void setSelectionLocked(boolean selectionLocked)
    {
        this.selectionLocked = selectionLocked;
    }

    public boolean selectionContains(ConsecutiveChild<?, ?> item)
    {
        return selection != null && selection.contains(item);
    }
    
    public <E extends StyledShowable, P extends ClipboardSaver> void ensureSelectionIncludes(ConsecutiveChild<E, P> src)
    {
        if (selectionLocked)
            return;

        if (selection != null)
        {
            // Check that span includes src:
            if (selection.contains(src))
                return; // Fine, no need to reassign
            // else clear and drop through to reassignment:
            clearSelection();
        }

        selection = new SelectionInfo<E, P>(src.getParent(), src);
        selection.markSelection(true, this::clearSelection);
    }

    protected void clearSelection(@UnknownInitialization(ConsecutiveBase.class) TopLevelEditor<EXPRESSION, SAVER> this)
    {
        if (selectionLocked)
            return;

        if (selection != null)
            selection.markSelection(false, this::clearSelection);
        selection = null;
    }

    /**
     * Check selection is still valid and re-mark it.
     */
    protected void validateSelection()
    {
        if (selection != null)
        {
            ImmutableList<? extends ConsecutiveChild<?, ?>> children = selection.parent.getAllChildren();
            int startIndex = children.indexOf(selection.start);
            int endIndex = children.indexOf(selection.end);
            if (startIndex >= 0 && endIndex >= 0 && endIndex >= startIndex && selection.getCaretNode().isSelectionFocused())
            {
                selection.markSelection(true, this::clearSelection);
            }
            else
            {
                selection.markSelection(false, this::clearSelection);
                selection = null;
            }
        }
    }

    public <E extends StyledShowable, P extends ClipboardSaver> void selectOnly(ConsecutiveChild<E, P> src)
    {
        if (selectionLocked)
            return;

        clearSelection();
        ensureSelectionIncludes(src);
    }

    public <E extends StyledShowable, P extends ClipboardSaver> void selectAllSiblings(ConsecutiveChild<E, P> src)
    {
        if (selectionLocked)
            return;

        clearSelection();
        ImmutableList<ConsecutiveChild<@NonNull E, P>> siblings = src.getParent().getAllChildren();
        ConsecutiveChild<@NonNull E, P> end = siblings.get(siblings.size() - 1);
        selection = new SelectionInfo<>(src.getParent(), siblings.get(0), end, SelectionCaret.END);
        selection.markSelection(true, this::clearSelection);
    }
    
    // Exact item given, or its left or its right?
    public static enum SelectionTarget
    {
        LEFT, AS_IS, RIGHT;
        
        public <E extends StyledShowable, P extends ClipboardSaver> ConsecutiveChild<E, P> apply(ConsecutiveChild<E, P> original)
        {
            ImmutableList<ConsecutiveChild<E, P>> allChildren = original.getParent().getAllChildren();
            int ourIndex = Utility.indexOfRef(allChildren, original);
            if (ourIndex == -1)
            {
                Log.normalStackTrace("Could not find node in its parent's children", 100);
                return original;
            }
            switch (this)
            {
                case LEFT:
                    if (ourIndex > 0)
                        return allChildren.get(ourIndex - 1); 
                    break;
                case RIGHT:
                    if (ourIndex + 1 < allChildren.size())
                        return allChildren.get(ourIndex + 1);
                    break;
            }
            return original;
        }
    }
    
    public static enum SelectExtremityTarget
    {
        HOME, END;

        public <E extends StyledShowable, P extends ClipboardSaver> ConsecutiveChild<E, P> apply(ConsecutiveChild<E, P> original)
        {
            ImmutableList<ConsecutiveChild<E, P>> allChildren = original.getParent().getAllChildren();
            int ourIndex = Utility.indexOfRef(allChildren, original);
            if (allChildren.isEmpty())
            {
                Log.normalStackTrace("Could not find node in its parent's children", 100);
                return original;
            }
            switch (this)
            {
                case HOME:
                    return allChildren.get(0);
                case END:
                    return allChildren.get(allChildren.size() - 1);
            }
            return original;
        }
    }
    
    public <E extends StyledShowable, P extends ClipboardSaver> void extendSelectionToExtremity(ConsecutiveChild<E, P> node, SelectExtremityTarget selectExtremityTarget)
    {
        node = selectExtremityTarget.apply(node);
        extendSelectionTo(node, SelectionTarget.AS_IS);
    }

    public <E extends StyledShowable, P extends ClipboardSaver> void extendSelectionTo(ConsecutiveChild<E, P> node, SelectionTarget selectionTarget)
    {
        if (selectionLocked)
            return;

        node = selectionTarget.apply(node);
        
        if (selection == null)
        {
            selection = new SelectionInfo<E, P>(node.getParent(), node);
            selection.markSelection(true, this::clearSelection);
        }
        else if (selection != null && node.getParent() == selection.parent)
        {
            // Given they have same parent, selection must be of type E:
            @SuppressWarnings("unchecked")
            SelectionInfo<E, P> oldSel = (SelectionInfo<E, P>)selection;

            // The target might be ahead or behind or within the current selection.
            // We try with asking for ahead or behind.  If one is empty, choose the other
            // If both are non-empty, go from start to target:
            List<ConsecutiveChild<E, P>> startToTarget = oldSel.parent.getChildrenFromTo(oldSel.getAnchorNode(), node);
            
            // Thus the rule is use startToTarget unless it's empty:
            if (!startToTarget.isEmpty())
            {
                clearSelection();
                selection = new SelectionInfo<E, P>(node.getParent(), oldSel.getAnchorNode(), node, SelectionCaret.END);
                selection.markSelection(true, this::clearSelection);
            }
            else
            {
                clearSelection();
                selection = new SelectionInfo<E, P>(node.getParent(), node, oldSel.getAnchorNode(), SelectionCaret.START);
                selection.markSelection(true, this::clearSelection);
            }
        }
    }
    
    public void deleteSelection()
    {
        if (selection != null)
        {
            selection.parent.deleteRangeIncl(selection.start, selection.end);
        }
    }

    public @Nullable Map<DataFormat, Object> getSelection()
    {
        if (selection != null)
        {
            return selection.copyItems();
        }
        return null;
    }

    public void removeSelectedItems()
    {
        if (selectionLocked)
            return;

        if (selection != null)
        {
            selection.removeItems();
        }
    }

    @Override
    protected void updateDisplay(@UnknownInitialization(DeepNodeTree.class) TopLevelEditor<EXPRESSION, SAVER> this)
    {
        super.updateDisplay();
        if (children != null)
        {
            // Flush focus requests of children.
            // Must use list copy in case this causes blank to be defocused and removed:
            for (ConsecutiveChild<EXPRESSION, SAVER> child : new ArrayList<>(children))
            {
                if (children.contains(child))
                    child.flushFocusRequest();
            }
        }
    }

    public ExpressionInfoDisplay installErrorShower(ErrorTop vBox, Label topLabel, TextField textField, @UnknownInitialization ConsecutiveChild<?, ?> node)
    {
        FXPlatformFunction<CaretSide, ImmutableList<ErrorInfo>> getAdjErrors = side -> Utility.later(node).getErrorsForAdjacentSide(side);
        
        ExpressionInfoDisplay expressionInfoDisplay = new ExpressionInfoDisplay(vBox, topLabel, textField, getAdjErrors, errorMessagePopup);
        vBox.bindErrorMasking(expressionInfoDisplay.maskingErrors());
        return expressionInfoDisplay;
    }

    @Override
    public void cleanup()
    {
        super.cleanup();
        errorMessagePopup.hidePopup(true);
    }
    
    public void setPromptText(@Localized String prompt)
    {
        this.prompt = prompt;
        updatePrompts();
    }

    @Override
    protected void updatePrompts()
    {
        if (children.size() == 1 && prompt != null)
        {
            children.get(0).setPrompt(prompt);
        }
        else
        {
            for (ConsecutiveChild<EXPRESSION, SAVER> child : children)
            {
                child.setPrompt("");
            }
        }
    }
    
    public final void copySelection()
    {
        if (selection != null)
        {
            Map<DataFormat, Object> content = selection.copyItems();
            if (content != null)
                Clipboard.getSystemClipboard().setContent(content);
        }
    }
    
    public final void cutSelection()
    {
        copySelection();
        if (selection != null)
            selection.removeItems();
    }

    // Only really exists for testing purposes:
    public class TopLevelEditorFlowPane extends FlowPane
    {
        @OnThread(Tag.Any)
        public TopLevelEditor<?, ?> _test_getEditor()
        {
            return TopLevelEditor.this;
        }
    }

    // Interface to access a singleton-per-editor error-displayer.
    // Lets us hide the other functionality from people using the class
    @OnThread(Tag.FXPlatform)
    public static interface ErrorMessageDisplayer
    {
        void mouseHoverEnded(Node hoverEndedOn);

        void mouseHoverBegan(@Nullable ErrorInfo errorInfo, Node hoverBeganOn);

        void updateError(@Nullable ErrorInfo errorInfo, TextField keyboardFocus, Node... possibleHoverNodes);

        void hidePopup(boolean immediate);

        void keyboardFocusEntered(@Nullable ErrorInfo errorInfo, ImmutableList<ErrorInfo> adjacentErrors, TextField textField);

        void keyboardFocusExited(@Nullable ErrorInfo errorInfo, TextField textField);
    }

    public static class ErrorInfo
    {
        private final StyledString errorMessage;
        private final ImmutableList<FixInfo> fixes;

        public ErrorInfo(StyledString errorMessage, ImmutableList<FixInfo> fixes)
        {
            this.errorMessage = errorMessage;
            this.fixes = fixes;
        }
    }

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
    private class ErrorMessagePopup extends PopOver implements ErrorMessageDisplayer
    {        
        private @Nullable Pair<ErrorInfo, TextField> keyboardErrorInfo;
        private @Nullable Pair<ErrorInfo, Node> mouseErrorInfo;
        
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
            container.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                if (e.getButton() == MouseButton.MIDDLE)
                {
                    // Will come back if user hovers or moves keyboard:
                    keyboardErrorInfo = null;
                    mouseErrorInfo = null;
                    FXUtility.mouse(this).updateShowHide(true);
                    e.consume();
                }
            });
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
            if (!isShowing())
            {
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
            @Nullable ErrorInfo errorInfo = null;
            if (keyboardErrorInfo != null)
                errorInfo = keyboardErrorInfo.getFirst();
            else if (mouseErrorInfo != null)
                errorInfo = mouseErrorInfo.getFirst();
            
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

        private void show(ErrorInfo errorInfo)
        {
            errorLabel.getChildren().setAll(errorInfo.errorMessage.toGUI().toArray(new Node[0]));
            errorLabel.setVisible(!errorInfo.errorMessage.toPlain().isEmpty());
            fixList.setFixes(errorInfo.fixes);
        }

        @Override
        public void updateError(@Nullable ErrorInfo errorInfo, TextField keyboardFocus, Node... possibleHoverNodes)
        {
            if (this.keyboardErrorInfo != null && this.keyboardErrorInfo.getSecond() == keyboardFocus)
            {
                // Update keyboard error
                this.keyboardErrorInfo = errorInfo == null ? null : this.keyboardErrorInfo.replaceFirst(errorInfo);
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

        @Override
        public void keyboardFocusEntered(@Nullable ErrorInfo errorInfo, ImmutableList<ErrorInfo> adjacentErrors, TextField textField)
        {
            if (errorInfo == null && !adjacentErrors.isEmpty())
            {
                errorInfo = adjacentErrors.get(0);
            }
            
            keyboardErrorInfo = errorInfo == null ? null : new Pair<>(errorInfo, textField);
            
            updateShowHide(true);
        }

        @Override
        public void keyboardFocusExited(@Nullable ErrorInfo errorInfo, TextField textField)
        {
            keyboardErrorInfo = null;

            updateShowHide(true);
        }

        @Override
        public void mouseHoverBegan(@Nullable ErrorInfo errorInfo, Node hoverBeganOn)
        {
            mouseErrorInfo = errorInfo == null ? null : new Pair<>(errorInfo, hoverBeganOn);

            updateShowHide(true);
        }

        @Override
        public void mouseHoverEnded(Node hoverEndedOn)
        {
            mouseErrorInfo = null;

            updateShowHide(false);
        }
    }
}
