package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.Table;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

import javax.validation.constraints.NotNull;
import java.io.File;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class Transformation extends Item
{

    @OnThread(Tag.FXPlatform)
    public abstract String getTransformationLabel();

    @OnThread(Tag.FXPlatform)
    public abstract Table getSource();

    //@OnThread(Tag.FXPlatform)
    //public abstract void edit();


    @Override
    public @OnThread(Tag.FXPlatform) void save(@Nullable File destination, FXPlatformConsumer<String> then)
    {
        // TEMPORARY!
        then.consume("");
    }
}
