package records.data;

import annotation.qual.Value;
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
import utility.Utility;

import java.io.File;

/**
 * Created by neil on 09/11/2016.
 */
public class ImmediateDataSource extends DataSource
{
    private final EditableRecordSet data;

    public ImmediateDataSource(TableManager mgr, EditableRecordSet data)
    {
        super(mgr, null);
        this.data = data;
    }

    public ImmediateDataSource(TableManager mgr, @Nullable TableId tableId, EditableRecordSet data)
    {
        super(mgr, tableId);
        this.data = data;
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData()
    {
        return data;
    }

    @Override
    public @OnThread(Tag.Simulation) void save(@Nullable File destination, Saver then)
    {
        //dataSourceImmedate : DATA tableId BEGIN NEWLINE;
        //immediateDataLine : ITEM+ NEWLINE;
        //dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(getId()).t(MainLexer.FORMAT).begin().nl();
        Utility.alertOnError_(() ->
        {
            for (Column c : data.getColumns())
            {
                b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).quote(c.getName());
                c.getType().save(b);

                @Nullable @Value Object defaultValue = c.getDefaultValue();
                if (defaultValue != null)
                {
                    b.t(FormatLexer.DEFAULT, FormatLexer.VOCABULARY);
                    b.dataValue(c.getType(), defaultValue);
                }
                b.nl();
            }
        });
        b.end().t(MainLexer.FORMAT).nl();
        Utility.alertOnError_(() -> {
            b.t(MainLexer.VALUES).begin().nl();
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
        b.end().id(getId()).nl();
        then.saveTable(b.toString());
    }

    @Override
    public @OnThread(Tag.Any) TableOperations getOperations()
    {
        return new TableOperations((newColumnName, newColumnType, defaultValue) -> {
            Utility.alertOnError_(() -> {
                data.addColumn(newColumnType.makeImmediateColumn(newColumnName, defaultValue));
            });
            // All columns in ImmediateDataSource can be renamed:
        }, _c -> null /*((oldColumnName, newColumnName) -> {
            data.renameColumn(oldColumnName, newColumnName);
        })*/, _c -> new DeleteColumn()
        {
            @Override
            public @OnThread(Tag.Simulation) void deleteColumn(ColumnId deleteColumnName)
            {
                Utility.alertOnError_(() -> {
                    data.deleteColumn(deleteColumnName);
                });
            }
        }, appendRowCount -> {
            Utility.alertOnError_(() ->
            {
                data.insertRows(data.getLength(), appendRowCount);
            });
        }, (rowIndex, insertRowCount) -> {
            Utility.alertOnError_(() ->
            {
                data.insertRows(rowIndex, insertRowCount);
            });
        }, (deleteRowFrom, deleteRowCount) -> {
            Utility.alertOnError_(() -> data.removeRows(deleteRowFrom, deleteRowCount));
        });
    }

    @Override
    public void addColumn(String newColumnName, DataType newColumnType, @Value Object defaultValue) throws InternalException, UserException
    {

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

    @Override
    @OnThread(Tag.Any)
    public MessageWhenEmpty getDisplayMessageWhenEmpty()
    {
        return new MessageWhenEmpty("table.immediate.noColumns", "table.immediate.noRows");
    }
}
