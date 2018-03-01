package records.transformations;

import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 27/11/2016.
 */
public abstract class SingleSourceTransformationInfo extends TransformationInfo
{
    public SingleSourceTransformationInfo(String canonicalName, String displayName, String imageFileName, List<String> keywords)
    {
        super(canonicalName, displayName, imageFileName, keywords);
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
}
