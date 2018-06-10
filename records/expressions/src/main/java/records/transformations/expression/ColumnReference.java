package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
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
    private @MonotonicNonNull Column column;
    private final ColumnReferenceType referenceType;

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

    @Override
    public @Nullable CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable RecordSet recordSet = dataLookup.getTable(tableName);
        if (recordSet == null)
        {
            onError.recordError(this, StyledString.s("Could not find source table " + (tableName == null ? "" : tableName.getRaw())));
            return null;
        }
        column = recordSet.getColumn(columnName);
        switch (referenceType)
        {
            case CORRESPONDING_ROW:
                return onError.recordType(this, ExpressionKind.EXPRESSION, typeState, TypeExp.fromDataType(this, column.getType()));
            case WHOLE_COLUMN:
                return onError.recordType(this, ExpressionKind.EXPRESSION, typeState, TypeExp.fromDataType(this, DataType.array(column.getType())));
        }
        throw new InternalException("Unknown reference type: " + referenceType);
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
                return new Pair<>(column.getType().getCollapsed(state.getRowIndex()), state);
            case WHOLE_COLUMN:
                @NonNull Column columnFinal = column;
                return new Pair<>(DataTypeUtility.value(new ListEx() {

                    @Override
                    @OnThread(Tag.Simulation)
                    public int size() throws InternalException, UserException
                    {
                        return columnFinal.getLength();
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public @Value Object get(int index) throws UserException, InternalException
                    {
                        return columnFinal.getType().getCollapsed(index);
                    }
                }), state);
        }
        throw new InternalException("Unknown reference type: " + referenceType);
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return (referenceType == ColumnReferenceType.WHOLE_COLUMN ? "@entirecolumn " : "@column ")
            + OutputBuilder.quotedIfNecessary(renames.columnId(tableName, columnName).getOutput());
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.concat(
            StyledString.s(referenceType == ColumnReferenceType.WHOLE_COLUMN ? GeneralExpressionEntry.ARROW_WHOLE : GeneralExpressionEntry.ARROW_SAME_ROW),
            StyledString.s(OutputBuilder.quotedIfNecessary(columnName.getOutput()))
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
}


