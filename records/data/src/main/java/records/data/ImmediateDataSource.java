package records.data;

import annotation.qual.Value;
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
import utility.gui.FXUtility;

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
    public @OnThread(Tag.Simulation) void save(@Nullable File destination, Saver then, TableAndColumnRenames renames)
    {
        //dataSourceImmedate : DATA tableId BEGIN NEWLINE;
        //immediateDataLine : ITEM+ NEWLINE;
        //dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(renames.tableId(getId())).t(MainLexer.FORMAT).begin().nl();
        FXUtility.alertOnError_(() ->
        {
            for (Column c : data.getColumns())
            {
                b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).quote(renames.columnId(getId(), c.getName()));
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
        FXUtility.alertOnError_(() -> {
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
        b.end().id(renames.tableId(getId())).nl();
        then.saveTable(b.toString());
    }

    @Override
    public @OnThread(Tag.Any) TableOperations getOperations()
    {
        return new TableOperations(getManager().getRenameTableOperation(this), (newColumnName, newColumnType, defaultValue) -> {
            FXUtility.alertOnError_(() -> {
                @MonotonicNonNull ColumnId name = newColumnName;
                if (name == null)
                {
                    String stem = "C";
                    for (int i = 1; i < 100000; i++)
                    {
                        name = new ColumnId(stem + i);
                        if (!getData().getColumnIds().contains(name))
                            break;
                    }
                    // Give up!
                }
                if (name == null || getData().getColumnIds().contains(name))
                    throw new UserException("Column name already exists in table: " + name);
                
                data.addColumn(newColumnType.makeImmediateColumn(name, defaultValue));
            });
            // All columns in ImmediateDataSource can be renamed:
        }, _c -> null /*((oldColumnName, newColumnName) -> {
            data.renameColumn(oldColumnName, newColumnName);
        })*/, _c -> new DeleteColumn()
        {
            @Override
            public @OnThread(Tag.Simulation) void deleteColumn(ColumnId deleteColumnName)
            {
                FXUtility.alertOnError_(() -> {
                    data.deleteColumn(deleteColumnName);
                });
            }
        }, appendRowCount -> {
            FXUtility.alertOnError_(() ->
            {
                data.insertRows(data.getLength(), appendRowCount);
            });
        }, (rowIndex, insertRowCount) -> {
            FXUtility.alertOnError_(() ->
            {
                data.insertRows(rowIndex, insertRowCount);
            });
        }, (deleteRowFrom, deleteRowCount) -> {
            FXUtility.alertOnError_(() -> data.removeRows(deleteRowFrom, deleteRowCount));
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
