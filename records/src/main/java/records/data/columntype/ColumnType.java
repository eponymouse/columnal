package records.data.columntype;

/**
 * Created by neil on 30/10/2016.
 */
public class ColumnType
{
    public static final BlankColumnType BLANK = BlankColumnType.INSTANCE;

    public boolean isText() { return false; }
    public boolean isBlank() { return false; }
    public boolean isNumeric() { return false; }
    public boolean isDate() { return false; }
}
