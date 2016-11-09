package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import javax.validation.constraints.NotNull;
import java.io.File;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class Item
{
    @NotNull
    @OnThread(Tag.Any)
    public abstract RecordSet getData();

    @OnThread(Tag.FXPlatform)
    public abstract String save(@Nullable File destination);
}
