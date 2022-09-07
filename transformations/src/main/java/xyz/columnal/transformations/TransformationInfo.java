package records.transformations;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.grammar.Versions.ExpressionVersion;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformSupplier;
import xyz.columnal.utility.SimulationSupplier;

import java.util.List;
import java.util.Optional;

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
    protected final @LocalizableKey String displayNameKey;

    private final String imageFileName;

    /**
     * Keywords to search (e.g. alternative names for this function).
     */
    protected final List<String> keywords;

    /**
     * The key for the text explaining this transformation
     */
    private final @LocalizableKey String explanationKey;


    @OnThread(Tag.Any)
    public TransformationInfo(String canonicalName, @LocalizableKey String displayNameKey, String imageFileName, @LocalizableKey String explanationKey, List<String> keywords)
    {
        this.canonicalName = canonicalName;
        this.displayNameKey = displayNameKey;
        this.imageFileName = imageFileName;
        this.keywords = keywords;
        this.explanationKey = explanationKey;
    }

    public final String getCanonicalName()
    {
        return canonicalName;
    }

    public final @LocalizableKey String getDisplayNameKey()
    {
        return displayNameKey;
    }

    @OnThread(Tag.Simulation)
    public abstract Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException;

    @OnThread(Tag.FXPlatform)
    public abstract @Nullable SimulationSupplier<Transformation> make(TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable);
    
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

    public final @LocalizableKey String getExplanationKey()
    {
        return explanationKey;
    }
}
