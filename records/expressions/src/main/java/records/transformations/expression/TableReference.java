package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import records.transformations.expression.Expression.ColumnLookup.FoundTable;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.Record;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

public class TableReference extends NonOperatorExpression
{
    public static final String ROWS = "rows";
    private final TableId tableName;
    private @MonotonicNonNull FoundTable resolvedTable;
    private boolean includeRows;

    public TableReference(TableId tableName)
    {
        this.tableName = tableName;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        FoundTable table = dataLookup.getTable(tableName);
        if (table == null)
        {
            onError.recordError(this, StyledString.s("Unknown table: " + tableName.getRaw()));
            return null;
        }
        HashMap<@ExpressionIdentifier String, TypeExp> fieldsAsSingle = new HashMap<>();
        HashMap<@ExpressionIdentifier String, TypeExp> fieldsAsList = new HashMap<>();

        for (Entry<ColumnId, DataTypeValue> entry : table.getColumnTypes().entrySet())
        {
            fieldsAsList.put(entry.getKey().getRaw(), TypeExp.list(this, TypeExp.fromDataType(this, entry.getValue().getType())));
            fieldsAsSingle.put(entry.getKey().getRaw(), TypeExp.fromDataType(this, entry.getValue().getType()));
        }
        
        if (!fieldsAsList.containsKey(ROWS))
        {
            includeRows = true;
            fieldsAsList.put(ROWS, TypeExp.list(this, TypeExp.record(this, fieldsAsSingle, true)));
        }
        resolvedTable = table;
        return new CheckedExp(onError.recordType(this, TypeExp.record(this, fieldsAsList, true)), typeState);
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        if (resolvedTable == null)
            throw new InternalException("Attempting to fetch value despite type check failure");
        final FoundTable resolvedTableNN = resolvedTable;
        final ImmutableMap<ColumnId, DataTypeValue> columnTypes = resolvedTableNN.getColumnTypes();
        @Value Record result = DataTypeUtility.value(new Record()
        {
            @Override
            public @Value Object getField(@ExpressionIdentifier String name) throws InternalException
            {
                if (includeRows && name.equals(ROWS))
                    return DataTypeUtility.value(new RowsAsList());
                return DataTypeUtility.value(new ColumnAsList(Utility.getOrThrow(columnTypes, new ColumnId(name), () -> new InternalException("Cannot find column " + name))));
            }
            
            class RowsAsList extends ListEx
            {
                @Override
                public int size() throws InternalException, UserException
                {
                    return resolvedTableNN.getRowCount();
                }

                @Override
                public @Value Object get(int index) throws InternalException, UserException
                {
                    ImmutableMap.Builder<@ExpressionIdentifier String, @Value Object> rowValuesBuilder = ImmutableMap.builder();
                    for (Entry<ColumnId, DataTypeValue> entry : columnTypes.entrySet())
                    {
                        rowValuesBuilder.put(entry.getKey().getRaw(), entry.getValue().getCollapsed(index));
                    }
                    ImmutableMap<@ExpressionIdentifier String, @Value Object> rowValues = rowValuesBuilder.build();
                    
                    return DataTypeUtility.value(new Record()
                    {
                        @Override
                        public @Value Object getField(@ExpressionIdentifier String name) throws InternalException
                        {
                            return Utility.getOrThrow(rowValues, name, () -> new InternalException("Cannot find column " + name));
                        }

                        @Override
                        public ImmutableMap<@ExpressionIdentifier String, @Value Object> getFullContent() throws InternalException
                        {
                            return rowValues;
                        }
                    });
                }
            }
            
            class ColumnAsList extends ListEx
            {
                private final DataTypeValue dataTypeValue;

                ColumnAsList(DataTypeValue dataTypeValue)
                {
                    this.dataTypeValue = dataTypeValue;
                }

                @Override
                public int size() throws InternalException, UserException
                {
                    return resolvedTableNN.getRowCount();
                }

                @Override
                public @Value Object get(int index) throws InternalException, UserException
                {
                    return dataTypeValue.getCollapsed(index);
                }
            }
            
            @Override
            public ImmutableMap<@ExpressionIdentifier String, @Value Object> getFullContent() throws InternalException
            {
                return columnTypes.entrySet().stream().collect(ImmutableMap.<Entry<ColumnId, DataTypeValue>, @ExpressionIdentifier String, @Value Object>toImmutableMap(e -> e.getKey().getRaw(), e -> DataTypeUtility.value(new ColumnAsList(e.getValue()))));
            }
        });
        
        return result(result, state, ImmutableList.of(), ImmutableList.of(/*new ExplanationLocation(resolvedTableName, columnName)*/), false);
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.table(this, tableName);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, @Nullable TypeManager typeManager, TableAndColumnRenames renames)
    {
        final TableId renamed = renames.tableId(tableName);
        
        // Sanity check to avoid saving something we can't load:
        if (saveDestination == SaveDestination.EDITOR || (IdentifierUtility.asExpressionIdentifier(renamed.getRaw()) != null))
            return "@table " + renamed.getRaw();
        else
            return "@unfinished " + OutputBuilder.quoted(renamed.getRaw());
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableReference that = (TableReference) o;
        return tableName.equals(that.tableName);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tableName);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(tableName.toStyledString(), this);
    }

    @SuppressWarnings("recorded") // Only used for items which will be reloaded anyway
    public static @Recorded Expression makeEntireColumnReference(TableId tableId, ColumnId columnId)
    {
        return new FieldAccessExpression(new TableReference(tableId), new IdentExpression(columnId.getRaw()));
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return this;
    }

    public TableId getTableId()
    {
        return tableName;
    }
}
