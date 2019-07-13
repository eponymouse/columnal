package records.transformations;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;
import utility.SimulationSupplier;
import utility.Utility;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class Join extends Transformation
{
    private final TableId primarySource;
    private final TableId secondarySource;
    private final ImmutableList<Pair<ColumnId, ColumnId>> columnsToMatch; 
    /**
     * Should we put records from the primary source into the output even
     * if we found no match in the other part of the join?  If true, and we are keeping them,
     * the types of all records in the other table become wrapped in Optional.
     */
    private final boolean keepPrimaryWithNoMatch;

    // Not actually a column by itself, but holds a list of integers so reasonable to re-use:
    // Each item is a source index in the corresponding original table.
    // So if it holds [2, 5, 5] that means we have three items so far,
    // the first is from index 2 in primary table, second is from index 5, third is also from index 5.  These two items will always
    // be of the same size, but if secondary has -1 in, it means
    // there is no row (only happens when keepPrimaryWithNoMatch is true)
    private final NumericColumnStorage primaryIndexMap;
    private final NumericColumnStorage secondaryIndexMap;
    private boolean examinedAllSourceRows = false;
    
    @OnThread(Tag.Any)
    private final @Nullable String error;
    private final @Nullable RecordSet recordSet;

    @OnThread(Tag.Simulation)
    public Join(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId primarySource, TableId secondarySource, boolean keepPrimaryWithNoMatch, ImmutableList<Pair<ColumnId, ColumnId>> columnsToMatch)
    {
        super(mgr, initialLoadDetails);
        this.primarySource = primarySource;
        this.secondarySource = secondarySource;
        this.keepPrimaryWithNoMatch = keepPrimaryWithNoMatch;
        this.columnsToMatch = columnsToMatch;
        
        this.primaryIndexMap = new NumericColumnStorage(false);
        this.secondaryIndexMap = new NumericColumnStorage(false);
        
        RecordSet theRecordSet = null;
        String theError = null;
        try
        {
            RecordSet primary = mgr.getSingleTableOrThrow(primarySource).getData();
            RecordSet secondary = mgr.getSingleTableOrThrow(secondarySource).getData();
            
            
            Pair<RecordSet, RecordSet> primSec = new Pair<>(primary, secondary);
            
            // There's three kinds of column from tables P(rimary) and S(econdary):
            // Join columns of P."a" = S."b".  These remain in, named "c" if it does not also occur in a non-joined S column, or "P a" if there is a name clash. 
            // Left columns of P."c".  These remain in, named "c" if it does not also occur in a non-joined S column, or "P c" if there is a name clash.
            // Right columns of S."d".  These remain in, named "d" if it does not also occur in a non-joined P column, or "S c" if there is a name clash.
            // After joining index maps are sorted, the first and second category is the same
            theRecordSet = new <Column>RecordSet(Utility.<SimulationFunction<RecordSet, Column>>concatI(
                Utility.<Column, SimulationFunction<RecordSet, Column>>mapListI(primary.getColumns(), c -> copyColumn(c, avoidNameClash(primarySource, c.getName(), secondary.getColumnIds()), primaryIndexMap, primSec)),
                Utility.<Column, SimulationFunction<RecordSet, Column>>mapListI(secondary.getColumns(), c -> copyColumn(c, avoidNameClash(secondarySource, c.getName(), primary.getColumnIds()), secondaryIndexMap, primSec))
            ))
            {
                @Override
                public boolean indexValid(int index) throws UserException, InternalException
                {
                    if (index < primaryIndexMap.filled())
                        return true;

                    Utility.later(Join.this).fillJoinMapTo(index,primSec);
                    return index < primaryIndexMap.filled();
                }
            };
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            theError = e.getLocalizedMessage();
        }
        recordSet = theRecordSet;
        error = theError;
    }

    @SuppressWarnings("identifier")
    private static ColumnId avoidNameClash(TableId tableName, ColumnId columnName, List<ColumnId> namesToNotClashWith)
    {
        while (namesToNotClashWith.contains(columnName))
        {
            // Keep prepending table name until it's unique:
            String proposed = tableName.getRaw() + " " + columnName.getRaw();
            columnName = new ColumnId(IdentifierUtility.fixExpressionIdentifier(proposed, proposed));
        }
        return columnName;
    }

    private SimulationFunction<RecordSet, Column> copyColumn(@UnknownInitialization(Transformation.class) Join this, Column c, ColumnId name, NumericColumnStorage indexMap, Pair<RecordSet, RecordSet> recordSets)
    {
        return rs -> new Column(rs, name)
        {
            @Override
            @SuppressWarnings({"nullness", "initialization"})
            public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
            {
                return addManualEditSet(getName(), c.getType().copyReorder(i ->
                {
                    fillJoinMapTo(i, recordSets);
                    return DataTypeUtility.value(indexMap.getInt(i));
                }));
            }

            @Override
            public @OnThread(Tag.Any) AlteredState getAlteredState()
            {
                return AlteredState.FILTERED_OR_REORDERED;
            }
        };
    }

    @OnThread(Tag.Simulation)
    private void fillJoinMapTo(int destIndex, Pair<RecordSet, RecordSet> recordSets) throws InternalException, UserException
    {
        if (examinedAllSourceRows)
            return;
        // We go down the rows of the primary table, looking for secondary matches
        // If (none && keepPrimaryWithNoMatch) || some, we add a result row 
        int nextPrimaryToExamine = primaryIndexMap.filled() == 0 ? 0 : (primaryIndexMap.getInt(primaryIndexMap.filled() - 1) + 1);
        while (recordSets.getFirst().indexValid(nextPrimaryToExamine))
        {
            boolean foundSecondary = false;
            for (Pair<ColumnId, ColumnId> toMatch : columnsToMatch)
            {
                @Value Object primaryVal = recordSets.getFirst().getColumn(toMatch.getFirst()).getType().getCollapsed(nextPrimaryToExamine);
                for (int secondaryIndex = 0; recordSets.getSecond().indexValid(secondaryIndex); secondaryIndex++)
                {
                    @Value Object secondaryVal = recordSets.getSecond().getColumn(toMatch.getSecond()).getType().getCollapsed(secondaryIndex);
                    if (Utility.compareValues(primaryVal, secondaryVal) == 0)
                    {
                        foundSecondary = true;
                        primaryIndexMap.add(nextPrimaryToExamine);
                        secondaryIndexMap.add(secondaryIndex);
                    }
                }
            }
            
            if (columnsToMatch.isEmpty())
            {
                boolean added = false;
                for (int secondaryIndex = 0; recordSets.getSecond().indexValid(secondaryIndex); secondaryIndex++)
                {
                    primaryIndexMap.add(nextPrimaryToExamine);
                    secondaryIndexMap.add(secondaryIndex);
                    added = true;
                }
                if (!added && keepPrimaryWithNoMatch)
                {
                    // Add row with blank secondary
                    primaryIndexMap.add(nextPrimaryToExamine);
                    secondaryIndexMap.add(-1);
                }
            }
            else if (!foundSecondary && keepPrimaryWithNoMatch)
            {
                // Add row with blank secondary
                primaryIndexMap.add(nextPrimaryToExamine);
                secondaryIndexMap.add(-1);
            }
            
            if (primaryIndexMap.filled() > destIndex)
                return;
            else
                nextPrimaryToExamine += 1;
        }
        examinedAllSourceRows = true;
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.of();
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getPrimarySources()
    {
        return Stream.of(primarySource, secondarySource);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "join";
    }

    @Override
    protected @OnThread(Tag.Simulation) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return ImmutableList.of("TODO");
    }

    @Override
    protected int transformationHashCode()
    {
        return Objects.hash(primarySource, secondarySource, keepPrimaryWithNoMatch, columnsToMatch);
    }

    @Override
    public boolean transformationEquals(Transformation transformation)
    {
        if (this == transformation) return true;
        if (transformation == null || getClass() != transformation.getClass()) return false;
        if (!super.equals(transformation)) return false;
        Join join = (Join) transformation;
        return keepPrimaryWithNoMatch == join.keepPrimaryWithNoMatch &&
                primarySource.equals(join.primarySource) &&
                columnsToMatch.equals(join.columnsToMatch) &&
                secondarySource.equals(join.secondarySource);
    }

    @Override
    public @NonNull @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (recordSet != null)
            return recordSet;
        else
            throw new UserException(error == null ? "Unknown error in join" : error);
    }

    /**
     * The primary table on the "left" of the join
     */
    public TableId getPrimarySource()
    {
        return primarySource;
    }

    /**
     * The secondary table on the "right" of the join.
     */
    public TableId getSecondarySource()
    {
        return secondarySource;
    }

    /**
     * A list of the (primary, secondary) columns where each pair
     * must have equal values in the join.  Empty list means result table will be
     * full cross join with all possible pairings.
     */
    public ImmutableList<Pair<ColumnId, ColumnId>> getColumnsToMatch()
    {
        return columnsToMatch;
    }

    /**
     * If true, it does a left join; all rows from primary table
     * are included at least once, even if they have no match,
     * and all secondary items become Optional wrapped.  If false,
     * does an inner join.
     */
    public boolean isKeepPrimaryWithNoMatch()
    {
        return keepPrimaryWithNoMatch;
    }

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super("join", "transform.join", "preview-join.png", "join.explanation.short", Arrays.asList("merge"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail) throws InternalException, UserException
        {
            if (source.size() != 2)
                throw new UserException("Expected two tables as join sources but found " + source.size());
            // TODO load the detail
            return new Join(mgr, initialLoadDetails, source.get(0), source.get(1), false, ImmutableList.of());
        }

        @SuppressWarnings("identifier")
        @Override
        public @OnThread(Tag.FXPlatform) @Nullable SimulationSupplier<Transformation> make(TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable)
        {
            return () -> new Join(mgr, new InitialLoadDetails(null, destination, null), new TableId(""), new TableId(""), false, ImmutableList.of());
        }
    }
}
