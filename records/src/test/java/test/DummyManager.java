package test;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;

/**
 * Created by neil on 16/11/2016.
 */
public class DummyManager extends TableManager
{
    public static final DummyManager INSTANCE = new DummyManager();

    private DummyManager() {};
}
