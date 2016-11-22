package records.transformations;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 02/11/2016.
 */
public abstract class TransformationInfo
{
    /**
     * The name, as will be shown in the search bar and used for saving and loading.
     */
    protected final String name;
    /**
     * Keywords to search (e.g. alternative names for this function).
     */
    protected final List<String> keywords;

    @OnThread(Tag.Any)
    public TransformationInfo(String name, List<String> keywords)
    {
        this.name = name;
        this.keywords = keywords;
    }

    public final String getName()
    {
        return name;
    }

    @OnThread(Tag.Simulation)
    public abstract Transformation load(TableManager mgr, TableId tableId, String detail) throws InternalException, UserException;

    @OnThread(Tag.FXPlatform)
    public abstract TransformationEditor editNew(TableId srcTableId, @Nullable Table src);

}
