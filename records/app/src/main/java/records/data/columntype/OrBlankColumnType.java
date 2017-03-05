package records.data.columntype;

/**
 * A column type which also has the option to be blank.
 */
public class OrBlankColumnType extends ColumnType
{
    // The actual column type (when non-blank)
    private final ColumnType columnType;

    public OrBlankColumnType(ColumnType columnType)
    {
        this.columnType = columnType;
    }

    public Object getInner()
    {
        return columnType;
    }
}
