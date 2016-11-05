package records.data;

import records.gui.Table;
import threadchecker.OnThread;
import threadchecker.Tag;

import javax.validation.constraints.NotNull;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class Transformation
{
    @NotNull
    @OnThread(Tag.Any)
    public abstract RecordSet getResult();

    @OnThread(Tag.FXPlatform)
    public abstract String getTransformationLabel();

    @OnThread(Tag.FXPlatform)
    public abstract Table getSource();

    //@OnThread(Tag.FXPlatform)
    //public abstract void edit();
}
