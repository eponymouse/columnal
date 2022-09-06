package utility;

import records.error.InternalException;
import records.error.UserException;

/**
 * Created by neil on 30/11/2016.
 */
@FunctionalInterface
public interface ConsumerInt<A>
{
    public void accept(A a) throws InternalException;
}
