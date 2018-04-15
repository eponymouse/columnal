package records.gui.expressioneditor;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableManager;
import records.data.datatype.TypeManager;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import records.gui.expressioneditor.ExpressionEditorUtil.CopiedItems;
import utility.gui.FXUtility.DragHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class TopLevelEditor<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT>
{
    private final FlowPane container;
    private final List<FXPlatformConsumer<Node>> focusListeners = new ArrayList<>();
    protected final TableManager tableManager;
    
    // Selections take place within one consecutive and go from one operand to another (inclusive):
    private @Nullable SelectionInfo<?, ?> selection;
    private @Nullable ConsecutiveChild<?, ?> curHoverDropTarget;
    private boolean selectionLocked;

    public TopLevelEditor(OperandOps<EXPRESSION, SEMANTIC_PARENT> operations, TableManager tableManager, String... styleClasses)
    {
        super(operations, null, null, "");
        this.container = new TopLevelEditorFlowPane();
        this.tableManager = tableManager;

        container.getStyleClass().addAll(styleClasses);
        container.getStylesheets().add(FXUtility.getStylesheet("expression-editor.css"));

        container.getChildren().setAll(nodes());
        FXUtility.listen(nodes(), c -> {
            container.getChildren().setAll(nodes());
        });

        FXUtility.onceNotNull(container.sceneProperty(), scene -> {
            FXUtility.addChangeListenerPlatform(scene.focusOwnerProperty(), owner -> {
                //Utility.logStackTrace("Focus now with: " + owner);
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

        // If they click the background, focus the end:
        container.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1 && e.getButton() == MouseButton.PRIMARY)
            {
                FXUtility.mouse(this).focus(Focus.RIGHT);
            }
        });

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


    @SuppressWarnings("initialization") // Because we pass ourselves as this
    protected void loadContent(@UnknownInitialization(TopLevelEditor.class) TopLevelEditor<EXPRESSION, SEMANTIC_PARENT> this, LoadableExpression<EXPRESSION, SEMANTIC_PARENT> startingValue)
    {
        Pair<List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperandNode<EXPRESSION, SEMANTIC_PARENT>>>, List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>>> items = startingValue.loadAsConsecutive(false);
        atomicEdit.set(true);
        operators.addAll(Utility.mapList(items.getSecond(), f -> f.load(this, getThisAsSemanticParent())));
        operands.addAll(Utility.mapList(items.getFirst(), f -> f.load(this, getThisAsSemanticParent())));
        atomicEdit.set(false);
    }


    public TableManager getTableManager()
    {
        return tableManager;
    }

    public TypeManager getTypeManager()
    {
        return tableManager.getTypeManager();
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


    public Node getContainer()
    {
        return container;
    }

    public @Nullable Window getWindow()
    {
        if (container.getScene() == null)
            return null;
        return container.getScene().getWindow();
    }
    @Override
    public TopLevelEditor<EXPRESSION, SEMANTIC_PARENT> getEditor()
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

    private static class SelectionInfo<E extends StyledShowable, P>
    {
        private final ConsecutiveBase<E, P> parent;
        private final ConsecutiveChild<E, P> start;
        private final ConsecutiveChild<E, P> end;

        private SelectionInfo(ConsecutiveBase<E, P> parent, ConsecutiveChild<E, P> start, ConsecutiveChild<E, P> end)
        {
            this.parent = parent;
            this.start = start;
            this.end = end;
        }

        public boolean contains(ConsecutiveChild<?, ?> item)
        {
            return parent.getChildrenFromTo(start, end).contains(item);
        }

        public ExpressionEditorUtil.@Nullable CopiedItems copyItems()
        {
            return parent.copyItems(start, end);
        }

        public void removeItems()
        {
            parent.removeItems(start, end);
        }

        public void markSelection(boolean selected)
        {
            parent.markSelection(start, end, selected);
        }
    }


    public void setSelectionLocked(boolean selectionLocked)
    {
        this.selectionLocked = selectionLocked;
    }

    @SuppressWarnings("initialization")
    public <E extends StyledShowable, P> void ensureSelectionIncludes(@UnknownInitialization ConsecutiveChild<E, P> src)
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

        selection = new SelectionInfo<E, P>(src.getParent(), src, src);
        selection.markSelection(true);
    }

    protected void clearSelection(@UnknownInitialization(ConsecutiveBase.class) TopLevelEditor<EXPRESSION, SEMANTIC_PARENT> this)
    {
        if (selectionLocked)
            return;

        if (selection != null)
            selection.markSelection(false);
        selection = null;
    }

    public <E extends StyledShowable, P> void selectOnly(ConsecutiveChild<E, P> src)
    {
        if (selectionLocked)
            return;

        clearSelection();
        ensureSelectionIncludes(src);
    }

    public <E extends StyledShowable, P> void extendSelectionTo(ConsecutiveChild<E, P> node)
    {
        if (selectionLocked)
            return;

        if (selection != null && node.getParent() == selection.parent)
        {
            // Given they have same parent, selection must be of type E:
            @SuppressWarnings("unchecked")
            SelectionInfo<E, P> oldSel = (SelectionInfo<E, P>)selection;

            // The target might be ahead or behind or within the current selection.
            // We try with asking for ahead or behind.  If one is empty, choose the other
            // If both are non-empty, go from start to target:
            ConsecutiveChild<E, P> oldSelStart = oldSel.start;
            List<ConsecutiveChild<E, P>> startToTarget = oldSel.parent.getChildrenFromTo(oldSelStart, node);
            ConsecutiveChild<E, P> oldSelEnd = oldSel.end;
            // Thus the rule is use startToTarget unless it's empty:
            if (!startToTarget.isEmpty())
            {
                clearSelection();
                selection = new SelectionInfo<E, P>(node.getParent(), oldSelStart, node);
                selection.markSelection(true);
            }
            else
            {
                clearSelection();
                selection = new SelectionInfo<E, P>(node.getParent(), node, oldSelEnd);
                selection.markSelection(true);
            }
        }
    }

    public @Nullable CopiedItems getSelection()
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

    // Only really exists for testing purposes:
    public class TopLevelEditorFlowPane extends FlowPane
    {
        @OnThread(Tag.Any)
        public TopLevelEditor<?, ?> _test_getEditor()
        {
            return TopLevelEditor.this;
        }
    }
}
