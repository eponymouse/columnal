package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.ColumnId;
import records.data.ExplanationLocation;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.loadsave.OutputBuilder;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility.ListEx;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 25/11/2016.
 */
public class ColumnReference extends NonOperatorExpression
{
    public static enum ColumnReferenceType
    {
        // Column is in same table as referring item, use the same row as that item
        // E.g. if doing a transform, score_percent = score / 100;
        CORRESPONDING_ROW,

        // Column may or may not be in same table, use whole column as item,
        // e.g. if normalising, cost = cost {CORRESPONDING_ROW}/sum(cost{WHOLE_COLUMN})
        WHOLE_COLUMN;
    }
    private final @Nullable TableId tableName;
    private final ColumnId columnName;
    private final ColumnReferenceType referenceType;
    
    private @MonotonicNonNull DataTypeValue column;
    private @MonotonicNonNull TableId resolvedTableName;

    public ColumnReference(@Nullable TableId tableName, ColumnId columnName, ColumnReferenceType referenceType)
    {
        this.tableName = tableName;
        this.columnName = columnName;
        this.referenceType = referenceType;
    }

    public ColumnReference(ColumnId columnName, ColumnReferenceType type)
    {
        this(null, columnName, type);
    }

    public ColumnReference(ColumnReference toCopy)
    {
        this(toCopy.tableName, toCopy.columnName, toCopy.referenceType);
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable Pair<TableId, DataTypeValue> col = dataLookup.getColumn(tableName, columnName, referenceType);
        if (col == null)
        {
            onError.recordError(this, StyledString.s("Could not find source column " + (tableName == null ? "" : (tableName.getRaw() + ":")) + columnName));
            return null;
        }
        resolvedTableName = col.getFirst();
        column = col.getSecond();
        return onError.recordType(this, ExpressionKind.EXPRESSION, typeState, TypeExp.fromDataType(this, column));
    }

    @Override
    @OnThread(Tag.Simulation)
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        if (column == null)
            throw new InternalException("Attempting to fetch value despite type check failure");
        switch (referenceType)
        {
            case CORRESPONDING_ROW:
                return new Pair<>(column.getCollapsed(state.getRowIndex()), state);
            case WHOLE_COLUMN:
                return new Pair<>(column.getCollapsed(0), state);
        }
        throw new InternalException("Unknown reference type: " + referenceType);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        final Pair<@Nullable TableId, ColumnId> renamed = renames.columnId(tableName, columnName);

        final @Nullable TableId renamedTableId = renamed.getFirst();
        String tableColonColumn = (renamedTableId != null ? (renamedTableId.getRaw() + ":") : "") + renamed.getSecond().getRaw();
        
        if (!structured)
        {
            return tableColonColumn;
        }
        
        // Sanity check to avoid saving something we can't load:
        if (IdentifierUtility.asExpressionIdentifier(renamed.getSecond().getRaw()) != null && (renamedTableId == null || IdentifierUtility.asExpressionIdentifier(renamedTableId.getRaw()) != null))
            return (referenceType == ColumnReferenceType.WHOLE_COLUMN ? "@entire " : "@column ") + tableColonColumn;
        else
            return "@unfinished " + OutputBuilder.quoted(tableColonColumn);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.concat(
            StyledString.s(referenceType == ColumnReferenceType.WHOLE_COLUMN ? GeneralExpressionEntry.ARROW_WHOLE : GeneralExpressionEntry.ARROW_SAME_ROW),
            columnName.toStyledString()
        );
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.of(this);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(GeneralExpressionEntry.load(this));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ColumnReference that = (ColumnReference) o;

        if (tableName != null ? !tableName.equals(that.tableName) : that.tableName != null) return false;
        return columnName.equals(that.columnName);
    }

    @Override
    public int hashCode()
    {
        int result = tableName != null ? tableName.hashCode() : 0;
        result = 31 * result + columnName.hashCode();
        return result;
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


    public ColumnReferenceType getReferenceType()
    {
        return referenceType;
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @OnThread(Tag.Simulation)
    public @Nullable ExplanationLocation getElementLocation(int index)
    {
        if (resolvedTableName != null)
            return new ExplanationLocation(resolvedTableName, columnName, index);
        return null;
    }
}


