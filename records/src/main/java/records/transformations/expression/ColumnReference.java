package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;
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
    public DataTypeValue getTypeValue(RecordSet data) throws UserException, InternalException
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

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src) throws InternalException, UserException
    {
        return getType(src).apply(new DataTypeVisitor<Formula>()
        {
            @Override
            public Formula number(NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return formulaManager.getRationalFormulaManager().makeVariable(columnName.getOutput());
            }

            @Override
            public Formula text() throws InternalException, UserException
            {
                throw new UserException("Can't do text...");
            }

            @Override
            public Formula date() throws InternalException, UserException
            {
                throw new UserException("Can't do dates...");
            }

            @Override
            public Formula tagged(List<TagType<DataType>> tags) throws InternalException, UserException
            {
                throw new UserException("Can't do tags...");
            }

            @Override
            public Formula bool() throws InternalException, UserException
            {
                return formulaManager.getBooleanFormulaManager().makeVariable(columnName.getOutput());
            }
        });
    }
}


