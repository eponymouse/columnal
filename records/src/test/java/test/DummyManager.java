package test;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;

/**
 * Created by neil on 16/11/2016.
 */
public class DummyManager extends TableManager
{
    public static final DummyManager INSTANCE;

    static
    {
        try
        {
            INSTANCE = new DummyManager();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private DummyManager() throws InternalException, UserException
    {
        super();
    };
}
