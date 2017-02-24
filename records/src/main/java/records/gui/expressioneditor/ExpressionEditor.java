package records.gui.expressioneditor;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.layout.FlowPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.Column;
import records.data.Table;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil.CopiedItems;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
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
public class ExpressionEditor extends ConsecutiveBase
{
    private final FlowPane container;
    private final @Nullable DataType type;
    private final @Nullable Table srcTable;
    private final FXPlatformConsumer<@NonNull Expression> onChange;
    private final TypeManager typeManager;

    // Selections take place within one consecutive and go from one operand to another (inclusive):

    private @Nullable SelectionInfo selection;
    private @Nullable ConsecutiveChild curHoverDropTarget;
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

    private static class SelectionInfo
    {
        private final ConsecutiveBase parent;
        private final ConsecutiveChild start;
        private final ConsecutiveChild end;

        private SelectionInfo(ConsecutiveBase parent, ConsecutiveChild start, ConsecutiveChild end)
        {
            this.parent = parent;
            this.start = start;
            this.end = end;
        }
    }

    public ExpressionEditor(Expression startingValue, @Nullable Table srcTable, @Nullable DataType type, TypeManager typeManager, FXPlatformConsumer<@NonNull Expression> onChangeHandler)
    {
        super( null, null, "");
        this.container = new FlowPane();
        this.typeManager = typeManager;
        container.getStyleClass().add("expression-editor");
        Utility.ensureFontLoaded("NotoSans-Regular.ttf");
        container.getStylesheets().add(Utility.getStylesheet("expression-editor.css"));
        this.srcTable = srcTable;
        this.type = type;
        container.getChildren().setAll(nodes());
        Utility.listen(nodes(), c -> {
            container.getChildren().setAll(nodes());
        });
        this.onChange = onChangeHandler;
        Utility.addChangeListenerPlatform(container.sceneProperty(), scene -> {
            // We should only ever be added to one scene, but we will also get removed from it
            if (scene != null)
            {
                Utility.addChangeListenerPlatform(scene.focusOwnerProperty(), owner -> Utility.runAfter(() -> focusChanged()));
            }
        });

        loadContent(startingValue);

        FXUtility.enableDragTo(container, Collections.singletonMap(FXUtility.getTextDataFormat("Expression"), new DragHandler()
        {
            @Override
            @SuppressWarnings("initialization")
            public @OnThread(Tag.FXPlatform) void dragMoved(Point2D pointInScene)
            {
                Pair<ConsecutiveChild, Double> nearest = findClosestDrop(pointInScene);
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
                    Pair<ConsecutiveChild, Double> nearest = findClosestDrop(pointInScene);
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
        Pair<List<FXPlatformFunction<ConsecutiveBase, OperandNode>>, List<FXPlatformFunction<ConsecutiveBase, OperatorEntry>>> items = startingValue.loadAsConsecutive();
        atomicEdit.set(true);
        operators.addAll(Utility.mapList(items.getSecond(), f -> f.apply(this)));
        operands.addAll(Utility.mapList(items.getFirst(), f -> f.apply(this)));
        if (operators.size() == operands.size() - 1)
        {
            // Need a blank operator on the end:
            operators.add(new OperatorEntry(this));
        }
        atomicEdit.set(false);
    }

    @Override
    protected void initializeContent(@UnknownInitialization(ConsecutiveBase.class) ExpressionEditor this)
    {
        // Don't do default initialization
    }

    public Node getContainer()
    {
        return container;
    }

//    @Override
//    public @Nullable DataType getType(ExpressionNode child)
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
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        // No variables from outside the expression:
        return Collections.emptyList();
    }

    @Override
    public boolean isTopLevel(@UnknownInitialization(ConsecutiveBase.class) ExpressionEditor this)
    {
        return true;
    }

    @Override
    protected void parentFocusRightOfThis()
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
    protected void selfChanged(@UnknownInitialization(ConsecutiveBase.class) ExpressionEditor this)
    {
        clearSelection();
        // Can be null during initialisation
        if (onChange != null && !atomicEdit.get())
        {
            Expression expression = toExpression(err -> {});
            onChange.consume(expression);
        }
    }

    @Override
    protected List<Pair<DataType, List<String>>> getSuggestedParentContext() throws UserException, InternalException
    {
        return Collections.emptyList();
    }

    public TypeManager getTypeManager() throws InternalException
    {
        return typeManager;
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
    public void ensureSelectionIncludes(@UnknownInitialization ConsecutiveChild src)
    {
        if (selectionLocked)
            return;

        if (selection != null)
        {
            // Check that span includes src:
            if (selection.parent.getChildrenFromTo(selection.start, selection.end).contains(src))
                return; // Fine, no need to reassign
            // else clear and drop through to reassignment:
            clearSelection();
        }

        selection = new SelectionInfo(src.getParent(), src, src);
        selection.parent.markSelection(src, src, true);
    }

    private void clearSelection(@UnknownInitialization(ConsecutiveBase.class) ExpressionEditor this)
    {
        if (selectionLocked)
            return;

        if (selection != null)
            selection.parent.markSelection(selection.start, selection.end, false);
        selection = null;
    }

    public void selectOnly(ConsecutiveChild src)
    {
        if (selectionLocked)
            return;

        clearSelection();
        ensureSelectionIncludes(src);
    }

    public void extendSelectionTo(ConsecutiveChild node)
    {
        if (selectionLocked)
            return;

        if (selection != null && node.getParent() == selection.parent)
        {
            // The target might be ahead or behind or within the current selection.
            // We try with asking for ahead or behind.  If one is empty, choose the other
            // If both are non-empty, go from start to target:
            ConsecutiveChild oldSelStart = selection.start;
            List<ConsecutiveChild> startToTarget = selection.parent.getChildrenFromTo(oldSelStart, node);
            ConsecutiveChild oldSelEnd = selection.end;
            // Thus the rule is use startToTarget unless it's empty:
            if (!startToTarget.isEmpty())
            {
                clearSelection();
                selection = new SelectionInfo(node.getParent(), oldSelStart, node);
                selection.parent.markSelection(oldSelStart, node, true);
            }
            else
            {
                clearSelection();
                selection = new SelectionInfo(node.getParent(), node, oldSelEnd);
                selection.parent.markSelection(node, oldSelEnd, true);
            }
        }
    }

    public @Nullable CopiedItems getSelection()
    {
        if (selection != null)
        {
            return selection.parent.copyItems(selection.start, selection.end);
        }
        return null;
    }

    public void removeSelectedItems()
    {
        if (selectionLocked)
            return;

        if (selection != null)
        {
            selection.parent.removeItems(selection.start, selection.end);
        }
    }
}
