package records.data;

import records.error.UserException;
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
    public @OnThread(Tag.FXPlatform) String save(File destination)
    {
        //dataSourceImmedate : DATA tableId BEGIN NEWLINE;
        //immediateDataLine : ITEM+ NEWLINE;
        //dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(getId()).t(MainLexer.BEGIN).nl();

        Utility.alertOnError_(() -> {
            for (int i = 0; data.indexValid(i); i++)
            {
                //TODO!
                //for (Column c : data.getColumns())
                    //c.getSave(i);
            }
        });

        b.t(MainLexer.END).t(MainLexer.DATA).nl();
        return b.toString();
    }
}
