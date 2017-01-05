package records.transformations;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 06/11/2016.
 */
@OnThread(Tag.Simulation)
public interface FoldOperation<T, R>
{
    default List<@NonNull R> start() { return Collections.emptyList(); }

    default List<@NonNull R> process(@NonNull T n, int index) throws InternalException, UserException { return Collections.emptyList(); }

    default List<@NonNull R> end() throws UserException  { return Collections.emptyList(); }
}
