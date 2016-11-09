package records.data;

import records.grammar.MainLexer;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;

/**
 * Created by neil on 09/11/2016.
 */
public class LinkedDataSource extends DataSource
{
    private final RecordSet data;
    private final int typeToken;
    private final File path;

    public LinkedDataSource(RecordSet rs, int typeToken, File path)
    {
        this.data = rs;
        this.typeToken = typeToken;
        this.path = path;
    }

    @Override
    @OnThread(Tag.Any)
    public RecordSet getData()
    {
        return data;
    }

    @Override
    public @OnThread(Tag.FXPlatform) String save(File destination)
    {
        //dataSourceLinkHeader : DATA tableId LINKED importType filePath NEWLINE;
        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(getId()).t(MainLexer.LINKED).t(this.typeToken);
        b.path(destination.toPath().relativize(this.path.toPath()));
        b.nl();
        return b.toString();
    }

}
