package records.gui.expressioneditor;

import com.google.common.collect.ImmutableSet;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.layout.FlowPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Table;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil.CopiedItems;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.SingleLoader;
import records.transformations.expression.TypeState;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.FXUtility.DragHandler;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 17/12/2016.
 */
public class ExpressionEditor extends ConsecutiveBase<Expression, ExpressionNodeParent> implements ExpressionNodeParent
{
    private final ScrollPane scrollPane;
    private final FlowPane container;
    private final ObservableObjectValue<@Nullable DataType> expectedType;
    private final @Nullable Table srcTable;
    private final FXPlatformConsumer<@NonNull Expression> onChange;
    private final TableManager tableManager;

    // Selections take place within one consecutive and go from one operand to another (inclusive):

    private @Nullable SelectionInfo<?> selection;
    private @Nullable ConsecutiveChild<?> curHoverDropTarget;
    private boolean selectionLocked;

    public void registerFocusable(TextField textField)
    {
        // We now do this using scene's focus owner
        /*
        Utility.addChangeListenerPlatformNN(textField.focusedProperty(), focus -> {
            if (!focus)
            {
                focusChanged();
            }
        });
        */
    }

    @Override
    public boolean isFocused()
    {
        return childIsFocused();
    }

    private static class SelectionInfo<E extends @NonNull Object>
    {
        private final ConsecutiveBase<E, ?> parent;
        private final ConsecutiveChild<E> start;
        private final ConsecutiveChild<E> end;

        private SelectionInfo(ConsecutiveBase<E, ?> parent, ConsecutiveChild<E> start, ConsecutiveChild<E> end)
        {
            this.parent = parent;
            this.start = start;
            this.end = end;
        }

        public boolean contains(ConsecutiveChild<?> item)
        {
            return parent.getChildrenFromTo(start, end).contains(item);
        }

        public @Nullable CopiedItems copyItems()
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

    @SuppressWarnings("initialization")
    public ExpressionEditor(Expression startingValue, @Nullable Table srcTable, ObservableObjectValue<@Nullable DataType> expectedType, TableManager tableManager, FXPlatformConsumer<@NonNull Expression> onChangeHandler)
    {
        super(EXPRESSION_OPS,  null, null, "");
        this.container = new FlowPane();
        this.scrollPane = new ScrollPane(container);
        scrollPane.setPrefHeight(200.0);
        this.tableManager = tableManager;
        container.getStyleClass().add("expression-editor");
        container.getStylesheets().add(FXUtility.getStylesheet("expression-editor.css"));
        this.srcTable = srcTable;
        this.expectedType = expectedType;
        container.getChildren().setAll(nodes());
        FXUtility.listen(nodes(), c -> {
            container.getChildren().setAll(nodes());
        });
        this.onChange = onChangeHandler;
        FXUtility.addChangeListenerPlatform(container.sceneProperty(), scene -> {
            // We should only ever be added to one scene, but we will also get removed from it
            if (scene != null)
            {
                FXUtility.addChangeListenerPlatform(scene.focusOwnerProperty(), owner -> FXUtility.runAfter(() -> focusChanged()));
            }
        });

        loadContent(startingValue);

        FXUtility.enableDragTo(container, Collections.singletonMap(FXUtility.getTextDataFormat("Expression"), new DragHandler()
        {
            @Override
            @SuppressWarnings("initialization")
            public @OnThread(Tag.FXPlatform) void dragMoved(Point2D pointInScene)
            {
                Pair<ConsecutiveChild<? extends Expression>, Double> nearest = findClosestDrop(pointInScene, Expression.class);
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
                    Pair<ConsecutiveChild<? extends Expression>, Double> nearest = findClosestDrop(pointInScene, Expression.class);
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

        //Utility.onNonNull(container.sceneProperty(), s -> org.scenicview.ScenicView.show(s));
    }

    @SuppressWarnings("initialization") // Because we pass ourselves as this
    private void loadContent(@UnknownInitialization(ExpressionEditor.class) ExpressionEditor this, Expression startingValue)
    {
        Pair<List<SingleLoader<OperandNode<Expression>>>, List<SingleLoader<OperatorEntry<Expression, ExpressionNodeParent>>>> items = startingValue.loadAsConsecutive();
        atomicEdit.set(true);
        operators.addAll(Utility.mapList(items.getSecond(), f -> f.load(this, this)));
        operands.addAll(Utility.mapList(items.getFirst(), f -> f.load(this, this)));
        atomicEdit.set(false);
    }

    public Node getContainer()
    {
        return scrollPane;
    }

//    @Override
//    public @Nullable DataType getType(EEDisplayNode child)
//    {
//        return type;
//    }

    public List<Column> getAvailableColumns()
    {
        if (srcTable == null)
            return Collections.emptyList();
        try
        {
            return srcTable.getData().getColumns();
        }
        catch (UserException e)
        {
            Utility.log(e);
            return Collections.emptyList();
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(EEDisplayNode child)
    {
        // No variables from outside the expression:
        return Collections.emptyList();
    }

    @Override
    public boolean canDeclareVariable(@UnknownInitialization EEDisplayNode chid)
    {
        return false;
    }

    @Override
    protected void parentFocusRightOfThis(Focus side)
    {

    }

    @Override
    protected void parentFocusLeftOfThis()
    {

    }

    @Override
    protected boolean isMatchNode()
    {
        return false;
    }

    @Override
    public ImmutableSet<Character> terminatedByChars()
    {
        // Nothing terminates the overall editor:
        return ImmutableSet.of();
    }

    @Override
    protected ExpressionNodeParent getThisAsSemanticParent()
    {
        return this;
    }

    @Override
    protected void selfChanged()
    {
        clearSelection();
        // Can be null during initialisation
        if (!atomicEdit.get())
        {
            ErrorDisplayerRecord errorDisplayers = new ErrorDisplayerRecord();
            Expression expression = save(errorDisplayers, err -> {});
            if (onChange != null)
            {
                onChange.consume(expression);
            }
            try
            {
                if (srcTable != null && tableManager != null)
                {
                    expression.check(srcTable.getData(), new TypeState(tableManager.getUnitManager(), tableManager.getTypeManager()), (e, s, q) ->
                    {
                        if (!errorDisplayers.showError(e, s, q))
                        {
                            // Show it on us, then:
                            showError(s, q);
                        }
                    });
                }
            }
            catch (InternalException | UserException e)
            {
                Utility.log(e);
                String msg = e.getLocalizedMessage();
                if (msg != null)
                    showError(msg, Collections.emptyList());
            }
        }
    }

    public TypeManager getTypeManager()
    {
        return tableManager.getTypeManager();
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return this;
    }

    public void setSelectionLocked(boolean selectionLocked)
    {
        this.selectionLocked = selectionLocked;
    }

    @SuppressWarnings("initialization")
    public <E> void ensureSelectionIncludes(@UnknownInitialization ConsecutiveChild<E> src)
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

        selection = new SelectionInfo<E>(src.getParent(), src, src);
        selection.markSelection(true);
    }

    private void clearSelection(@UnknownInitialization(ConsecutiveBase.class) ExpressionEditor this)
    {
        if (selectionLocked)
            return;

        if (selection != null)
            selection.markSelection(false);
        selection = null;
    }

    public <E> void selectOnly(ConsecutiveChild<E> src)
    {
        if (selectionLocked)
            return;

        clearSelection();
        ensureSelectionIncludes(src);
    }

    public <E extends @NonNull Object> void extendSelectionTo(ConsecutiveChild<E> node)
    {
        if (selectionLocked)
            return;

        if (selection != null && node.getParent() == selection.parent)
        {
            // Given they have same parent, selection must be of type E:
            @SuppressWarnings("unchecked")
            SelectionInfo<E> oldSel = (SelectionInfo<E>)selection;

            // The target might be ahead or behind or within the current selection.
            // We try with asking for ahead or behind.  If one is empty, choose the other
            // If both are non-empty, go from start to target:
            ConsecutiveChild<E> oldSelStart = oldSel.start;
            List<ConsecutiveChild<E>> startToTarget = oldSel.parent.getChildrenFromTo(oldSelStart, node);
            ConsecutiveChild<E> oldSelEnd = oldSel.end;
            // Thus the rule is use startToTarget unless it's empty:
            if (!startToTarget.isEmpty())
            {
                clearSelection();
                selection = new SelectionInfo<E>(node.getParent(), oldSelStart, node);
                selection.markSelection(true);
            }
            else
            {
                clearSelection();
                selection = new SelectionInfo<E>(node.getParent(), node, oldSelEnd);
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

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        @Nullable DataType t = expectedType.get();
        if (t == null)
            return Collections.emptyList();
        else
            return Collections.singletonList(new Pair<>(t, Collections.emptyList()));
    }
}
