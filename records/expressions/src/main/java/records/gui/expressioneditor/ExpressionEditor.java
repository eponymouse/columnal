package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableSet;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
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
import records.gui.expressioneditor.GeneralExpressionEntry.ColumnRef;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.Expression.SingleTableLookup;
import records.transformations.expression.Expression.TableLookup;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.TypeState;
import records.types.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.FXUtility.DragHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 17/12/2016.
 */
public class ExpressionEditor extends ConsecutiveBase<Expression, ExpressionNodeParent> implements ExpressionNodeParent
{
    private final FlowPane container;
    private final ObservableObjectValue<@Nullable DataType> expectedType;
    private final @Nullable Table srcTable;
    private final FXPlatformConsumer<@NonNull Expression> onChange;
    private final TableManager tableManager;
    private final List<FXPlatformConsumer<Node>> focusListeners = new ArrayList<>();
    // Does it allow use of same-row column references?  Thinks like Transform, Sort, do -- but Aggregate does not.
    private final boolean allowsSameRow;

    // Selections take place within one consecutive and go from one operand to another (inclusive):

    private @Nullable SelectionInfo<?, ?> selection;
    private @Nullable ConsecutiveChild<?, ?> curHoverDropTarget;
    private boolean selectionLocked;
    private ObjectProperty<@Nullable DataType> latestType = new SimpleObjectProperty<>(null);

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

    public ObjectExpression<@Nullable DataType> typeProperty()
    {
        return latestType;
    }

    public @Nullable Window getWindow()
    {
        if (container.getScene() == null)
            return null;
        return container.getScene().getWindow();
    }

    public TableManager getTableManager()
    {
        return tableManager;
    }

    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayerRecord, ErrorAndTypeRecorderStorer errorAndTypeRecorderStorer)
    {
        return errorDisplayerRecord.record(this, saveUnrecorded(errorDisplayerRecord, errorAndTypeRecorderStorer));
    }

    // Gets content, and error (true = error) state of all header labels.
    @OnThread(Tag.FXPlatform)
    public Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return getAllChildren().stream().flatMap(c -> c._test_getHeaders());
    }

    public boolean allowsSameRow()
    {
        return allowsSameRow;
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
    public ExpressionEditor(Expression startingValue, ObjectExpression<@Nullable Table> srcTable, boolean allowsSameRow, ObservableObjectValue<@Nullable DataType> expectedType, TableManager tableManager, FXPlatformConsumer<@NonNull Expression> onChangeHandler)
    {
        super(EXPRESSION_OPS,  null, null, "");
        this.allowsSameRow = allowsSameRow;
        this.container = new ExpressionEditorFlowPane();
        this.tableManager = tableManager;
        container.getStyleClass().add("expression-editor");
        container.getStylesheets().add(FXUtility.getStylesheet("expression-editor.css"));
        // TODO respond to dynamic adjustment of table to revalidate column references:
        this.srcTable = srcTable.getValue();
        this.expectedType = expectedType;
        container.getChildren().setAll(nodes());
        FXUtility.listen(nodes(), c -> {
            container.getChildren().setAll(nodes());
        });
        this.onChange = onChangeHandler;
        FXUtility.onceNotNull(container.sceneProperty(), scene -> {
            FXUtility.addChangeListenerPlatform(scene.focusOwnerProperty(), owner -> {
                //Utility.logStackTrace("Focus now with: " + owner);
                FXUtility.runAfter(() -> {
                    //Log.debug("Focus now with [2]: " + owner);
                    // We are in a run-after so check focus hasn't changed again:
                    if (scene.getFocusOwner() == owner)
                    {
                        focusChanged();
                    }
                });
            });
        });
        // If they click the background, focus the end:
        container.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1 && e.getButton() == MouseButton.PRIMARY)
            {
                focus(Focus.RIGHT);
            }
        });
        
        //#error TODO add drag to container to allow selection of nodes
        container.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                // TODO pass an enum indicating we want contains, not before
                ConsecutiveChild<? extends StyledShowable, ?> target = findSmallestContainer(new Point2D(e.getSceneX(), e.getSceneY()));
                if (target != null)
                {
                    if (this.selection == null)
                    {
                        selectOnly(target);
                    }
                    else
                    {
                        extendSelectionTo(target);
                    }
                }
            }
        });

        loadContent(startingValue);

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

        //FXUtility.onceNotNull(container.sceneProperty(), s -> org.scenicview.ScenicView.show(s));
    }

    private @Nullable ConsecutiveChild<? extends StyledShowable, ?> findSmallestContainer(Point2D pointInScene)
    {
        class Locator implements LocatableVisitor
        {
            @MonotonicNonNull ConsecutiveChild<? extends StyledShowable, ?> smallest = null;
            int nodesSizeOfSmallest = Integer.MAX_VALUE;

            @Override
            public <C extends StyledShowable> void register(ConsecutiveChild<? extends C, ?> graphicalItem, Class<C> childType)
            {
                for (Node node : graphicalItem.nodes())
                {
                    Bounds boundsInScene = node.localToScene(node.getBoundsInLocal());
                    if (boundsInScene.contains(pointInScene) && graphicalItem.nodes().size() < nodesSizeOfSmallest)
                    {
                        smallest = graphicalItem;
                        nodesSizeOfSmallest = graphicalItem.nodes().size();
                    }
                }
            }
        }
        Locator locator = new Locator();
        visitLocatable(locator);
        return locator.smallest;
    }

    private <C extends StyledShowable> @Nullable Pair<ConsecutiveChild<? extends C, ?>, Double> findClosestDrop(Point2D pointInScene, Class<C> targetClass)
    {
        class Locator implements LocatableVisitor
        {
            double minDist = Double.MAX_VALUE;
            @MonotonicNonNull ConsecutiveChild<? extends C, ?> nearest = null;

            @Override
            public <D extends StyledShowable> void register(ConsecutiveChild<? extends D, ?> item, Class<D> childType)
            {
                if (targetClass.isAssignableFrom(childType) && !item.nodes().isEmpty())
                {
                    double dist = FXUtility.distanceToLeft(item.nodes().get(0), pointInScene);
                    if (dist < minDist)
                    {
                        // Safe because of the check above:
                        @SuppressWarnings("unchecked")
                        ConsecutiveChild<? extends C, ?> casted = (ConsecutiveChild)item;
                        nearest = casted;
                    }
                }
            }
        }
        Locator locator = new Locator();
        visitLocatable(locator);
        return locator.nearest == null ? null : new Pair<>(locator.nearest, locator.minDist);
    }

    @SuppressWarnings("initialization") // Because we pass ourselves as this
    private void loadContent(@UnknownInitialization(ExpressionEditor.class) ExpressionEditor this, Expression startingValue)
    {
        Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> items = startingValue.loadAsConsecutive(false);
        atomicEdit.set(true);
        operators.addAll(Utility.mapList(items.getSecond(), f -> f.load(this, this)));
        operands.addAll(Utility.mapList(items.getFirst(), f -> f.load(this, this)));
        atomicEdit.set(false);
    }

    public Node getContainer()
    {
        return container;
    }

//    @Override
//    public @Nullable DataType getType(EEDisplayNode child)
//    {
//        return type;
//    }

    public Stream<ColumnReference> getAvailableColumnReferences()
    {
        return tableManager.streamAllTables().flatMap(t -> {
            try
            {
                List<Column> columns = t.getData().getColumns();
                Stream<ColumnReference> wholeColumns = columns.stream().map(c -> new ColumnReference(t.getId(), c.getName(), ColumnReferenceType.WHOLE_COLUMN));
                // Use reference equality, as tables may share names if we compare them:
                if (t == srcTable)
                {
                    return Stream.concat(wholeColumns, columns.stream().map(c -> new ColumnReference(c.getName(), ColumnReferenceType.CORRESPONDING_ROW)));
                }
                else
                {
                    return wholeColumns;
                }
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                return Stream.empty();
            }
        });
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(@UnknownInitialization EEDisplayNode child)
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
        //Log.debug("selfChanged: " + atomicEdit.get());
        clearSelection();
        // Can be null during initialisation
        if (!atomicEdit.get())
        {
            ErrorDisplayerRecord errorDisplayers = new ErrorDisplayerRecord();
            ErrorAndTypeRecorder recorder = errorDisplayers.getRecorder();
            clearAllErrors();
            Expression expression = errorDisplayers.record(this, saveUnrecorded(errorDisplayers, recorder));
            //Log.debug("Saved as: " + expression);
            if (onChange != null)
            {
                onChange.consume(expression);
            }
            try
            {
                if (tableManager != null)
                {
                    TableLookup tableLookup = new MultipleTableLookup(tableManager, srcTable);
                    @Nullable TypeExp dataType = expression.check(tableLookup, new TypeState(tableManager.getUnitManager(), tableManager.getTypeManager()), recorder);
                    latestType.set(dataType == null ? null : recorder.recordLeftError(expression, dataType.toConcreteType(tableManager.getTypeManager())));
                    //Log.debug("Latest type: " + dataType);
                    errorDisplayers.showAllTypes(tableManager.getTypeManager());
                }
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                String msg = e.getLocalizedMessage();
                if (msg != null)
                    addErrorAndFixes(StyledString.s(msg), Collections.emptyList());
            }
        }
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
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

    private void clearSelection(@UnknownInitialization(ConsecutiveBase.class) ExpressionEditor this)
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

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        @Nullable DataType t = expectedType.get();
        if (t == null)
            return Collections.emptyList();
        else
            return Collections.singletonList(new Pair<>(t, Collections.emptyList()));
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
    
    public void addFocusListener(FXPlatformConsumer<Node> focusListener)
    {
        focusListeners.add(focusListener);
    }

    // Only really exists for testing purposes:
    public class ExpressionEditorFlowPane extends FlowPane
    {
        @OnThread(Tag.Any)
        public ExpressionEditor _test_getEditor()
        {
            return ExpressionEditor.this;
        }
    }

}
