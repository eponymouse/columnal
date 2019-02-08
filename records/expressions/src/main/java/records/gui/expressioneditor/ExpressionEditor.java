package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.DataFormat;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.CheckedExp;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.Expression.ExpressionKind;
import records.transformations.expression.Expression.LocationInfo;
import records.transformations.expression.InvalidIdentExpression;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.TypeState;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Collections;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * Created by neil on 17/12/2016.
 */
public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionSaver>
{
    public static enum ColumnAvailability { ONLY_ENTIRE, GROUPED, SINGLE }
    
    public static final DataFormat EXPRESSION_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-expression");
    private final ObservableObjectValue<@Nullable DataType> expectedType;
    private final @Nullable Table srcTable;
    private final FXPlatformConsumer<@NonNull Expression> onChange;
    private final ColumnLookup columnLookup;


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
    public Stream<Pair<Label, Boolean>> _test_getHeaders()
    {
        return getAllChildren().stream().flatMap(c -> c._test_getHeaders());
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public @Recorded Expression save()
    {
        ExpressionSaver saver = new ExpressionSaver(this, true);
        super.save(saver);
        @Recorded Expression expression = saver.finish(children.get(children.size() - 1));
        mostRecentSave = expression;

        try
        {
            @Nullable CheckedExp dataType = expression.check(columnLookup, new TypeState(getTypeManager().getUnitManager(), getTypeManager()), LocationInfo.UNIT_DEFAULT, saver);
            if (dataType != null && dataType.expressionKind == ExpressionKind.PATTERN)
            {
                saver.recordError(expression, StyledString.s("Expression cannot be a pattern"));
            }
            latestType.set(dataType == null ? null : saver.recordLeftError(getTypeManager(), expression, dataType.typeExp.toConcreteType(getTypeManager())));
            //Log.debug("Latest type: " + dataType);
            //errorDisplayers.showAllTypes(tableManager.getTypeManager());
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

    public ExpressionEditor(@Nullable Expression startingValue, ObjectExpression<@Nullable Table> srcTable, ColumnLookup columnLookup, ObservableObjectValue<@Nullable DataType> expectedType, TypeManager typeManager, FXPlatformConsumer<@NonNull Expression> onChangeHandler)
    {
        super(EXPRESSION_OPS, typeManager, "expression-editor");
        this.columnLookup = columnLookup;
        
        
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

        // Safe because at end of constructor:
        Utility.later(this).loadContent(startingValue != null ? startingValue : new InvalidIdentExpression(""), startingValue != null);
    }

    private @Nullable ConsecutiveChild<? extends StyledShowable, ?> findSmallestContainer(Point2D pointInScene)
    {
        class Locator implements LocatableVisitor
        {
            @MonotonicNonNull ConsecutiveChild<? extends StyledShowable, ?> smallest = null;
            int nodesSizeOfSmallest = Integer.MAX_VALUE;

            @Override
            public <C extends StyledShowable, S extends ClipboardSaver> void register(ConsecutiveChild<C, S> graphicalItem, Class<C> childType)
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

    //    @Override
//    public @Nullable DataType getType(EEDisplayNode child)
//    {
//        return type;
//    }

    @Override
    public Stream<ColumnReference> getAvailableColumnReferences()
    {
        /*
        return tableManager.streamAllTablesAvailableTo(srcTable == null ? null : srcTable.getId()).flatMap(t -> {
            try
            {
                List<Column> columns = t.getData().getColumns();
                return columns.stream().flatMap(c -> {
                    ArrayList<ColumnReference> refs = new ArrayList<>();
                    refs.add(new ColumnReference(t.getId(), c.getName(), ColumnReferenceType.WHOLE_COLUMN));
                    // Use reference equality, as tables may share names if we compare them:
                    if (t == srcTable && groupedColumns.apply(c.getName()) != ColumnAvailability.ONLY_ENTIRE)
                    {
                        refs.add(new ColumnReference(t.getId(), c.getName(), ColumnReferenceType.CORRESPONDING_ROW));
                    }
                    return refs.stream();
                });
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                return Stream.empty();
            }
        });
        */
        return columnLookup.getAvailableColumnReferences();
    }

    @Override
    protected void parentFocusRightOfThis(Either<Focus, Integer> position, boolean becauseOfTab)
    {
    }

    @Override
    protected void parentFocusLeftOfThis()
    {

    }

    @Override
    protected void selfChanged()
    {
        //Log.logStackTrace("selfChanged: " + atomicEdit.get());
        super.selfChanged();
        //Log.debug("selfChanged: " + atomicEdit.get());
        validateSelection();
        // Can be null during initialisation
        if (!atomicEdit.get())
        {
            ErrorDisplayerRecord errorDisplayers = new ErrorDisplayerRecord(true);
            ErrorAndTypeRecorder recorder = errorDisplayers.getRecorder();
            clearAllErrors();
            Expression expression = save();
            saved();
            Log.debug("Saved as: " + expression);
            //Log.debug("  From:\n    " + children.stream().map(c -> (c instanceof EntryNode) ? ((EntryNode)c).textField.getText() : "£" + c.getClass()).collect(Collectors.joining("\n    ")));
            if (onChange != null)
            {
                onChange.consume(expression);
            }
            /*
            try
            {
                if (tableManager != null)
                {
                    ColumnLookup tableLookup = new MultipleTableLookup(tableManager, srcTable);
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

    @Override
    public boolean showCompletionImmediately(@UnknownInitialization ConsecutiveChild<Expression, ExpressionSaver> child)
    {
        // If we're non-blank and no error, no need to show until they type:
        if (!child.isBlank() && !child.isShowingError())
            return false;
        
        // We show immediately if we are preceded by an operator:
        int index = Utility.indexOfRef(this.children, child);
        if (index == 0)
            return this.children.size() == 1; // Show if otherwise empty
        ConsecutiveChild<Expression, ExpressionSaver> before = this.children.get(index - 1);
        return (before instanceof GeneralExpressionEntry && ((GeneralExpressionEntry)before).isOperator());
    }

    @Override
    public DataFormat getClipboardType()
    {
        return EXPRESSION_CLIPBOARD_TYPE;
    }

    @Override
    protected @Nullable LoadableExpression<Expression, ExpressionSaver> parse(String src) throws InternalException, UserException
    {
        return Expression.parse(null, src, getTypeManager());
    }
}
