package records.data;

import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class DataSource extends Item
{
    private static AtomicInteger nextId = new AtomicInteger(1);
    private final int id;

    protected DataSource()
    {
        this.id = nextId.getAndIncrement();
    }

    @OnThread(Tag.Any)
    protected final String getId()
    {
        return "T" + id;
    }
}
