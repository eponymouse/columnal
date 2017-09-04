package records.transformations;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.gui.View;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 02/11/2016.
 */
public abstract class TransformationInfo
{
    /**
     * The name, as will be used for saving and loading.
     */
    protected final String canonicalName;

    /**
     * The name, as will be shown in the search bar and display.
     */
    protected final String displayName;

    /**
     * Keywords to search (e.g. alternative names for this function).
     */
    protected final List<String> keywords;

    @OnThread(Tag.Any)
    public TransformationInfo(String canonicalName, String displayName, List<String> keywords)
    {
        this.canonicalName = canonicalName;
        this.displayName = displayName;
        this.keywords = keywords;
    }

    public final String getCanonicalName()
    {
        return canonicalName;
    }

    public final String getDisplayName()
    {
        return displayName;
    }

    @OnThread(Tag.Simulation)
    public abstract Transformation load(TableManager mgr, TableId tableId, List<TableId> source, String detail) throws InternalException, UserException;

    @OnThread(Tag.FXPlatform)
    public abstract TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src);

}
