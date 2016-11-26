package records.transformations.expression;

import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 25/11/2016.
 */
public class ColumnReference extends Expression
{
    private final ColumnId columnName;

    public ColumnReference(ColumnId columnName)
    {
        this.columnName = columnName;
    }

    @Override
    public DataType getType(RecordSet data) throws UserException, InternalException
    {
        Column c = data.getColumn(columnName);
        return c.getType();
    }

    @Override
    public @OnThread(Tag.FXPlatform) String save()
    {
        return "@" + OutputBuilder.quoted(columnName.getOutput());
    }
}
