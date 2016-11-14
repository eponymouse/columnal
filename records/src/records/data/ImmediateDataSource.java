package records.data;

import javafx.application.Platform;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.UserException;
import records.grammar.MainLexer;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Utility;
import utility.Workers;

import java.io.File;

/**
 * Created by neil on 09/11/2016.
 */
public class ImmediateDataSource extends DataSource
{
    private final RecordSet data;

    public ImmediateDataSource(RecordSet data)
    {
        this.data = data;
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData()
    {
        return data;
    }

    @Override
    public @OnThread(Tag.FXPlatform) void save(@Nullable File destination, FXPlatformConsumer<String> then)
    {
        //dataSourceImmedate : DATA tableId BEGIN NEWLINE;
        //immediateDataLine : ITEM+ NEWLINE;
        //dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(getId()).t(MainLexer.BEGIN).nl();

        Workers.onWorkerThread("Fetching data for save", () -> {
            Utility.alertOnError_(() -> {
                for (int i = 0; data.indexValid(i); i++)
                {
                    b.indent();
                    for (Column c : data.getColumns())
                        b.data(c.getType(), i);
                    b.nl();
                }
            });
            Platform.runLater(() -> {
                b.t(MainLexer.END).t(MainLexer.DATA).nl();
                then.consume(b.toString());
            });
        });
    }
}
