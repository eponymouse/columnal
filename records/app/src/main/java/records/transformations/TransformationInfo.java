package records.transformations;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.Table.InitialLoadDetails;
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

    private final String imageFileName;

    /**
     * Keywords to search (e.g. alternative names for this function).
     */
    protected final List<String> keywords;


    @OnThread(Tag.Any)
    public TransformationInfo(String canonicalName, String displayName, String imageFileName, List<String> keywords)
    {
        this.canonicalName = canonicalName;
        this.displayName = displayName;
        this.imageFileName = imageFileName;
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
    public abstract Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail) throws InternalException, UserException;

    @OnThread(Tag.FXPlatform)
    public abstract TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src);

    public final String getImageFileName()
    {
        return imageFileName;
    }


    // TransformationInfo is uniquely keyed by the canonical name:
    @Override
    public final int hashCode()
    {
        return getCanonicalName().hashCode();
    }

    @Override
    public final boolean equals(@Nullable Object obj)
    {
        return obj instanceof TransformationInfo && obj != null && getCanonicalName().equals(((TransformationInfo)obj).getCanonicalName());
    }
}
