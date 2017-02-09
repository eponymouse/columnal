package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.UserException;
import records.grammar.MainLexer;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

import java.io.File;

/**
 * Created by neil on 09/11/2016.
 */
public class LinkedDataSource extends DataSource
{
    private final RecordSet data;
    private final int typeToken;
    private final File path;

    public LinkedDataSource(TableManager mgr, RecordSet rs, int typeToken, File path)
    {
        super(mgr, null);
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
    public @OnThread(Tag.FXPlatform) void save(@Nullable File destination, Saver then)
    {
        //dataSourceLinkHeader : DATA tableId LINKED importType filePath NEWLINE;
        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(getId()).t(MainLexer.LINKED).t(this.typeToken);
        b.path(destination == null ? this.path.toPath() : destination.toPath().relativize(this.path.toPath()));
        b.nl();
        then.saveTable(b.toString());
    }

    @Override
    public boolean dataEquals(DataSource o)
    {
        LinkedDataSource that = (LinkedDataSource) o;

        if (typeToken != that.typeToken) return false;
        if (!data.equals(that.data)) return false;
        return path.equals(that.path);
    }

    @Override
    public int dataHashCode()
    {
        int result = data.hashCode();
        result = 31 * result + typeToken;
        result = 31 * result + path.hashCode();
        return result;
    }
}
