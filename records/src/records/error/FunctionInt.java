package records.error;

/**
 * Created by neil on 31/10/2016.
 */
@FunctionalInterface
public interface FunctionInt<T, R>
{
    public R apply(T param) throws InternalException;
}
