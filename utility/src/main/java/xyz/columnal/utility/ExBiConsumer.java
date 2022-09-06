package utility;

import records.error.InternalException;
import records.error.UserException;

/**
 * Created by neil on 30/11/2016.
 */
@FunctionalInterface
public interface ExBiConsumer<A, B>
{
    public void accept(A a, B b) throws UserException, InternalException;
}
