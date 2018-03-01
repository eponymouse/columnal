package records.transformations;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.gui.View;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 01/05/2017.
 */
public abstract class TransformationEditable extends Transformation
{
    public TransformationEditable(TableManager mgr, InitialLoadDetails initialLoadDetails)
    {
        super(mgr, initialLoadDetails);
    }

    @OnThread(Tag.FXPlatform)
    public abstract TransformationEditor edit(View view);
}
