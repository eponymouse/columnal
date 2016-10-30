package records.data.type;

/**
 * Created by neil on 30/10/2016.
 */
public class BlankColumnType extends ColumnType
{
    public static final BlankColumnType INSTANCE = new BlankColumnType();

    // Singleton:
    private BlankColumnType() {};

    @Override
    public boolean isBlank()
    {
        return true;
    }
}
