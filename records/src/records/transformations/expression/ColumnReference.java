package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Created by neil on 25/11/2016.
 */
public class ColumnReference extends Expression
{
    private final @Nullable TableId tableName;
    private final ColumnId columnName;

    public ColumnReference(@Nullable TableId tableName, ColumnId columnName)
    {
        this.tableName = tableName;
        this.columnName = columnName;
    }

    public ColumnReference(ColumnId columnName)
    {
        this(null, columnName);
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

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.of(columnName);
    }
}


