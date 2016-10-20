package records.data;

/**
 * Created by neil on 20/10/2016.
 */
public abstract class Column<T>
{
    public abstract T get(int index) throws Exception;

    public abstract String getName();
}
