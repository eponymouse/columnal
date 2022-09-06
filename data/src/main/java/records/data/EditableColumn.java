package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.Table.Display;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationRunnable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Created by neil on 29/05/2017.
 */
public abstract class EditableColumn extends Column
{
    protected EditableColumn(RecordSet recordSet, ColumnId name)
    {
        super(recordSet, name);
    }

    // Returns a revert operation
    @OnThread(Tag.Simulation)
    public abstract SimulationRunnable insertRows(int index, int count) throws InternalException, UserException;

    // Returns a revert operation
    @OnThread(Tag.Simulation)
    public abstract SimulationRunnable removeRows(int index, int count) throws InternalException, UserException;

    @OnThread(Tag.Any)
    public boolean isEditable()
    {
        return true;
    }

    @OnThread(Tag.Any)
    public abstract @NonNull @Value Object getDefaultValue();

    @Override
    public @OnThread(Tag.Any) AlteredState getAlteredState()
    {
        // If we're editable, we must be new:
        return AlteredState.OVERWRITTEN;
    }
}
