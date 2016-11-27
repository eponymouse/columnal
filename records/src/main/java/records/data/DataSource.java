package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.UserException;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class DataSource extends Table
{
    public DataSource(TableManager mgr, @Nullable TableId id)
    {
        super(mgr, id);
    }
}
