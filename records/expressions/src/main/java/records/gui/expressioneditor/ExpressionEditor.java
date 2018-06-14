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
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Table;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.CheckedExp;
import records.transformations.expression.Expression.ExpressionKind;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.Expression.TableLookup;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.QuickFix;
import records.transformations.expression.TypeState;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 17/12/2016.
 */
public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionSaver>
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

    /*
    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayerRecord, ErrorAndTypeRecorderStorer errorAndTypeRecorderStorer)
    {
        return errorDisplayerRecord.record(this, saveUnrecorded(errorDisplayerRecord, errorAndTypeRecorderStorer));
    }
    */

    // Gets content, and error (true = error) state of all header labels.
    @OnThread(Tag.FXPlatform)
    public Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return getAllChildren().stream().flatMap(c -> c._test_getHeaders());
    }

    @Override
    public @Recorded Expression save()
    {
        ExpressionSaver saver = new ExpressionSaver(this);
        super.save(saver);
        @Recorded Expression expression = saver.finish(children.get(children.size() - 1));

        try
        {
            if (tableManager != null)
            {
                TableLookup tableLookup = new MultipleTableLookup(tableManager, srcTable);
                @Nullable CheckedExp dataType = expression.check(tableLookup, new TypeState(tableManager.getUnitManager(), tableManager.getTypeManager()), saver);
                if (dataType != null && dataType.expressionKind == ExpressionKind.PATTERN)
                {
                    saver.recordError(expression, StyledString.s("Expression cannot be a pattern"));
                }
                latestType.set(dataType == null ? null : saver.recordLeftError(tableManager.getTypeManager(), expression, dataType.typeExp.toConcreteType(tableManager.getTypeManager())));
                //Log.debug("Latest type: " + dataType);
                //errorDisplayers.showAllTypes(tableManager.getTypeManager());
            }
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
            String msg = e.getLocalizedMessage();
            if (msg != null)
                addErrorAndFixes(null, null, StyledString.s(msg), Collections.emptyList());
        }
        
        
        return expression;
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
        return tableManager.streamAllTablesAvailableTo(srcTable == null ? null : srcTable.getId()).flatMap(t -> {
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
    protected void parentFocusRightOfThis(Focus side)
    {
        if (!children.get(children.size() - 1).isBlank())
            addOperandToRight(children.get(children.size() - 1), "", true);
    }

    @Override
    protected void parentFocusLeftOfThis()
    {

    }

    @Override
    public ImmutableSet<Character> terminatedByChars()
    {
        // Nothing terminates the overall editor:
        return ImmutableSet.of();
    }

    @Override
    protected void selfChanged()
    {
        super.selfChanged();
        //Log.debug("selfChanged: " + atomicEdit.get());
        clearSelection();
        // Can be null during initialisation
        if (!atomicEdit.get())
        {
            ErrorDisplayerRecord errorDisplayers = new ErrorDisplayerRecord();
            ErrorAndTypeRecorder recorder = errorDisplayers.getRecorder();
            clearAllErrors();
            Expression expression = save();
            //Log.debug("Saved as: " + expression);
            if (onChange != null)
            {
                onChange.consume(expression);
            }
            /*
            try
            {
                if (tableManager != null)
                {
                    TableLookup tableLookup = new MultipleTableLookup(tableManager, srcTable);
                    @Nullable CheckedExp dataType = expression.check(tableLookup, new TypeState(tableManager.getUnitManager(), tableManager.getTypeManager()), recorder);
                    if (dataType != null && dataType.expressionKind == ExpressionKind.PATTERN)
                    {
                        recorder.recordError(expression, StyledString.s("Expression cannot be a pattern"));
                    }
                    latestType.set(dataType == null ? null : recorder.recordLeftError(tableManager.getTypeManager(), expression, dataType.typeExp.toConcreteType(tableManager.getTypeManager())));
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
            */
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
}
