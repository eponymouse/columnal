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
public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionNodeParent> implements ExpressionNodeParent
{
    private final ObservableObjectValue<@Nullable DataType> expectedType;
    private final @Nullable Table srcTable;
    private final FXPlatformConsumer<@NonNull Expression> onChange;
    // Does it allow use of same-row column references?  Thinks like Transform, Sort, do -- but Aggregate does not.
    private final boolean allowsSameRow;

    
    private ObjectProperty<@Nullable DataType> latestType = new SimpleObjectProperty<>(null);

    public ObjectExpression<@Nullable DataType> typeProperty()
    {
        return latestType;
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

    

    @SuppressWarnings("initialization")
    public ExpressionEditor(Expression startingValue, ObjectExpression<@Nullable Table> srcTable, boolean allowsSameRow, ObservableObjectValue<@Nullable DataType> expectedType, TableManager tableManager, FXPlatformConsumer<@NonNull Expression> onChangeHandler)
    {
        super(EXPRESSION_OPS, tableManager, "expression-editor");
        this.allowsSameRow = allowsSameRow;
        
        
        // TODO respond to dynamic adjustment of table to revalidate column references:
        this.srcTable = srcTable.getValue();
        this.expectedType = expectedType;
        
        this.onChange = onChangeHandler;
        
        //#error TODO add drag to container to allow selection of nodes
        /*
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
        */

        loadContent(startingValue);

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

//    @Override
//    public @Nullable DataType getType(EEDisplayNode child)
//    {
//        return type;
//    }

    @Override
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
    public ExpressionNodeParent getThisAsSemanticParent()
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

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
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
