package records.transformations;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.Table;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.SimulationSupplier;

import java.util.List;
import java.util.Optional;

/**
 * Helper class that simplifies implementing TransformationInfo for those transformations which have a single primary source.
 */
public abstract class SingleSourceTransformationInfo extends TransformationInfo
{
    public SingleSourceTransformationInfo(String canonicalName, @LocalizableKey String displayNameKey, String imageFileName, @LocalizableKey String explanationKey, List<String> keywords)
    {
        super(canonicalName, displayNameKey, imageFileName, explanationKey, keywords);
    }

    @Override
    @OnThread(Tag.Simulation)
    public final Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail) throws InternalException, UserException
    {
        if (source.size() > 1)
            throw new UserException("Transformation " + getCanonicalName() + " cannot have multiple sources. (If source name has a space, make sure to quote it.)");
        return loadSingle(mgr, initialLoadDetails, source.get(0), detail);
    }

    @OnThread(Tag.Simulation)
    protected abstract Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException;

    @Override
    @OnThread(Tag.FXPlatform)
    public final @Nullable SimulationSupplier<Transformation> make(TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable)
    {
        return askForSingleSrcTable.get().<@Nullable SimulationSupplier<Transformation>>map(srcTable -> {
            SimulationSupplier<Transformation> simulationSupplier = () -> makeWithSource(mgr, destination, srcTable);
            return simulationSupplier;
        }).orElse(null);
    }

    @OnThread(Tag.Simulation)
    protected abstract Transformation makeWithSource(TableManager mgr, CellPosition destination, Table srcTable) throws InternalException;
}
