package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.ColumnId;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import records.transformations.expression.explanation.ExplanationLocation;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.IdentifierUtility;
import utility.Pair;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A reference to a column.  Is usually a single value, but in aggregate
 * it may be a group of values.
 */
public class ColumnReference extends NonOperatorExpression
{
    private final @Nullable TableId tableName;
    private final ColumnId columnName;
    
    private @MonotonicNonNull DataTypeValue column;
    private @MonotonicNonNull TableId resolvedTableName;

    public ColumnReference(@Nullable TableId tableName, ColumnId columnName)
    {
        this.tableName = tableName;
        this.columnName = columnName;
    }

    public ColumnReference(ColumnId columnName)
    {
        this(null, columnName);
    }

    public ColumnReference(ColumnReference toCopy)
    {
        this(toCopy.tableName, toCopy.columnName);
    }

    @Override
    public @Nullable CheckedExp check(@Recorded ColumnReference this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Expression.ColumnLookup.@Nullable FoundColumn col = dataLookup.getColumn(this);
        if (col == null)
        {
            onError.recordError(this, StyledString.s("Could not find source column " + (tableName == null ? "" : (tableName.getRaw() + "\\")) + columnName));
            return null;
        }
        if (col.information != null)
        {
            onError.recordInformation(this, col.information);
        }
        resolvedTableName = col.tableId;
        column = col.dataTypeValue;
        return onError.recordType(this, typeState, TypeExp.fromDataType(this, column.getType()));
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        if (column == null || resolvedTableName == null)
            throw new InternalException("Attempting to fetch value despite type check failure");
        return result(column.getCollapsed(state.getRowIndex()), state, ImmutableList.of(), ImmutableList.of(new ExplanationLocation(resolvedTableName, columnName, state.getRowIndex())), false);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, @Nullable TypeManager typeManager, TableAndColumnRenames renames)
    {
        final Pair<@Nullable TableId, ColumnId> renamed = renames.columnId(tableName, columnName, null);

        final @Nullable TableId renamedTableId = renamed.getFirst();
        String tableColonColumn = (renamedTableId != null ? (renamedTableId.getRaw() + "\\") : "") + renamed.getSecond().getRaw();
        
        // Sanity check to avoid saving something we can't load:
        if (saveDestination == SaveDestination.EDITOR || (IdentifierUtility.asExpressionIdentifier(renamed.getSecond().getRaw()) != null && (renamedTableId == null || IdentifierUtility.asExpressionIdentifier(renamedTableId.getRaw()) != null)))
            return (saveDestination == SaveDestination.EDITOR ? "" : "@column ") + tableColonColumn;
        else
            return "@unfinished " + OutputBuilder.quoted(tableColonColumn);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(columnName.toStyledString(), this);
    }
    
    @Override
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        // Don't want to print out entire column:
        return skipIfTrivial;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColumnReference that = (ColumnReference) o;
        return Objects.equals(tableName, that.tableName) &&
                columnName.equals(that.columnName);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tableName, columnName);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        // TODO could replace with an invalid column name
        return null;
    }

    @Pure
    public @Nullable TableId getTableId()
    {
        return tableName;
    }

    public ColumnId getColumnId()
    {
        return columnName;
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @OnThread(Tag.Simulation)
    public @Nullable ExplanationLocation getElementLocation(@TableDataRowIndex int index) throws InternalException
    {
        if (resolvedTableName != null)
            return new ExplanationLocation(resolvedTableName, columnName, index);
        throw new InternalException("Cannot explain unresolved table for column " + columnName);
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.column(this, tableName, columnName);
    }
}


