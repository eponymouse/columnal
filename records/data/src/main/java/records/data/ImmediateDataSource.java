package records.data;

import annotation.qual.Value;
import log.ErrorHandler;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableOperations.DeleteColumn;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.MainLexer;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;
import utility.Workers.Priority;

import java.io.File;
import java.util.Optional;

/**
 * Created by neil on 09/11/2016.
 */
public class ImmediateDataSource extends DataSource
{
    private final EditableRecordSet data;
    
    public ImmediateDataSource(TableManager mgr, InitialLoadDetails initialLoadDetails, EditableRecordSet data)
    {
        super(mgr, initialLoadDetails);
        this.data = data;
    }

    @Override
    public @OnThread(Tag.Any) EditableRecordSet getData()
    {
        return data;
    }

    @Override
    public @OnThread(Tag.Simulation) void save(@Nullable File destination, Saver then, TableAndColumnRenames renames)
    {
        //dataSourceImmedate : DATA tableId BEGIN NEWLINE;
        //immediateDataLine : ITEM+ NEWLINE;
        //dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).begin().raw(saveTag.getTag());
        b.pushPrefix(saveTag);
        b.nl();
        b.id(renames.tableId(getId())).nl();
        b.t(MainLexer.FORMAT, MainLexer.VOCABULARY).begin().nl();
        String errorTitle = "Error saving table: " + getId().getRaw();
        ErrorHandler.getErrorHandler().alertOnError_(errorTitle, () ->
        {
            for (Column c : data.getColumns())
            {
                b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).unquoted(renames.columnId(getId(), c.getName(), null).getSecond());
                b.t(FormatLexer.TYPE, FormatLexer.VOCABULARY);
                c.getType().getType().save(b);

                @Nullable @Value Object defaultValue = c.getDefaultValue();
                if (defaultValue != null)
                {
                    b.t(FormatLexer.DEFAULT, FormatLexer.VOCABULARY);
                    b.dataValue(c.getType().getType(), defaultValue);
                }
                b.nl();
            }
        });
        b.end().t(MainLexer.FORMAT).nl();
        b.t(MainLexer.VALUES).begin().nl();
        ErrorHandler.getErrorHandler().alertOnError_(errorTitle, () -> {
            for (int i = 0; data.indexValid(i); i++)
            {
                b.indent();
                for (Column c : data.getColumns())
                    b.data(c.getType(), i);
                b.nl();
            }
        });
        b.end().t(MainLexer.VALUES).nl();
        savePosition(b);
        b.pop();
        b.end().raw(saveTag.getTag()).t(MainLexer.DATA, MainLexer.VOCABULARY).nl();
        then.saveTable(b.toString());
    }

    @Override
    public @OnThread(Tag.Any) TableOperations getOperations()
    {
        return new TableOperations(getManager().getRenameTableOperation(this)
        , _c -> new DeleteColumn()
        {
            @Override
            public @OnThread(Tag.Simulation) void deleteColumn(ColumnId deleteColumnName)
            {
                data.deleteColumn(deleteColumnName);
            }
        }, appendRowCount -> {
            ErrorHandler.getErrorHandler().alertOnError_("Error find table length for: " + getId().getRaw(), () ->
            {
                data.insertRows(data.getLength(), appendRowCount);
            });
        }, (rowIndex, insertRowCount) -> {
            data.insertRows(rowIndex, insertRowCount);
        }, (deleteRowFrom, deleteRowCount) -> {
            data.removeRows(deleteRowFrom, deleteRowCount);
        });
    }

    @Override
    public boolean dataEquals(DataSource o)
    {
        ImmediateDataSource that = (ImmediateDataSource) o;

        return data.equals(that.data);
    }

    @Override
    public int dataHashCode()
    {
        return data.hashCode();
    }
}
