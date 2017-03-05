package records.data;

import javafx.application.Platform;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.grammar.FormatLexer;
import records.grammar.MainLexer;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Workers;

import java.io.File;

/**
 * Created by neil on 09/11/2016.
 */
public class ImmediateDataSource extends DataSource
{
    private final RecordSet data;

    public ImmediateDataSource(TableManager mgr, RecordSet data)
    {
        super(mgr, null);
        this.data = data;
    }

    public ImmediateDataSource(TableManager mgr, TableId tableId, RecordSet data)
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
    public @OnThread(Tag.FXPlatform) void save(@Nullable File destination, Saver then)
    {
        //dataSourceImmedate : DATA tableId BEGIN NEWLINE;
        //immediateDataLine : ITEM+ NEWLINE;
        //dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(getId()).t(MainLexer.FORMAT).begin().nl();
        Utility.alertOnErrorFX_(() ->
        {
            for (Column c : data.getColumns())
            {
                b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).quote(c.getName());
                c.getType().save(b, false).nl();
            }
        });
        b.end().t(MainLexer.FORMAT).nl();
        Workers.onWorkerThread("Fetching data for save", () -> {
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
            Platform.runLater(() -> {
                b.end().t(MainLexer.VALUES).nl();
                savePosition(b);
                b.end().id(getId()).nl();
                then.saveTable(b.toString());
            });
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
