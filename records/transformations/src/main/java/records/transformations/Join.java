package records.transformations;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.NumericColumnStorage;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationSupplier;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class Join extends Transformation
{
    private final TableId primarySource;
    private final TableId joinWith;
    private final ImmutableList<Pair<ColumnId, ColumnId>> columnsToMatch; 
    /**
     * Should we put records from the primary source into the output even
     * if we found no match in the other part of the join?  If true, and we are keeping them,
     * the types of all records in the other table become wrapped in Optional.
     */
    private final boolean keepPrimaryWithNoMatch;

    // Not actually a column by itself, but holds a list of integers so reasonable to re-use:
    // Each item is a source index in the corresponding original table
    private final NumericColumnStorage primaryIndexMap;
    private final NumericColumnStorage joinWithIndexMap;
    
    @OnThread(Tag.Any)
    private final @Nullable String error;
    private final @Nullable RecordSet recordSet;

    @OnThread(Tag.Simulation)
    public Join(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId primarySource, TableId joinWith, boolean keepPrimaryWithNoMatch, ImmutableList<Pair<ColumnId, ColumnId>> columnsToMatch)
    {
        super(mgr, initialLoadDetails);
        this.primarySource = primarySource;
        this.joinWith = joinWith;
        this.keepPrimaryWithNoMatch = keepPrimaryWithNoMatch;
        this.columnsToMatch = columnsToMatch;
        
        this.primaryIndexMap = new NumericColumnStorage(false);
        this.joinWithIndexMap = new NumericColumnStorage(false);
        
        recordSet = new RecordSet()
        {
            @Override
            public boolean indexValid(int index) throws UserException, InternalException
            {
                return false;
            }
        };
        error = null;
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.of();
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getPrimarySources()
    {
        return Stream.of(primarySource, joinWith);
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
        return Objects.hash(primarySource, joinWith, keepPrimaryWithNoMatch, columnsToMatch);
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
                joinWith.equals(join.joinWith);
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
    public TableId getJoinWith()
    {
        return joinWith;
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
