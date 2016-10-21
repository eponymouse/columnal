package records.data;

import org.jetbrains.annotations.NotNull;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class Transformation
{
    @NotNull
    public abstract RecordSet getResult();
}
