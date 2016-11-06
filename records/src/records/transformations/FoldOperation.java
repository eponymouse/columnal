package records.transformations;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 06/11/2016.
 */
@OnThread(Tag.Simulation)
public interface FoldOperation<T, R>
{
    List<R> start();

    List<R> process(@NonNull T n);

    List<R> end() throws UserException;
}
