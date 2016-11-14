package records.data;

import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class Table
{
    private static final AtomicInteger nextId = new AtomicInteger(1);
    private static final Set<TableId> allIds = new HashSet<>();
    private final TableId id;

    // Assigns a new arbitrary ID which is not in use
    protected Table()
    {
        this.id = getNextFreeId();
        allIds.add(this.id);
    }

    @NonNull
    private static TableId getNextFreeId()
    {
        String id;
        do
        {
            id = "T" + nextId.getAndIncrement();
        }
        while (allIds.contains(id));
        return new TableId(id);
    }

    protected Table(@Nullable TableId id) throws UserException
    {
        if (id == null)
            this.id = getNextFreeId();
        else
        {
            if (allIds.contains(id))
                throw new UserException("Duplicate table identifiers in file: \"" + id + "\"");
            this.id = id;
        }
        allIds.add(this.id);
    }

    @OnThread(Tag.Any)
    public final TableId getId(@UnknownInitialization(Table.class) Table this)
    {
        return id;
    }

    @NotNull
    @OnThread(Tag.Any)
    public abstract RecordSet getData() throws UserException;

    @OnThread(Tag.FXPlatform)
    public abstract void save(@Nullable File destination, FXPlatformConsumer<String> then);

    protected class WholeTableException extends UserException
    {
        @OnThread(Tag.Any)
        public WholeTableException(String message)
        {
            super(message);
        }
    }
}
