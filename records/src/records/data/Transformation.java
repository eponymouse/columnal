package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.gui.TableDisplay;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

import java.io.File;
import java.util.List;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class Transformation extends Table
{
    public Transformation(@Nullable TableId tableId) throws UserException
    {
        super(tableId);
    }

    @OnThread(Tag.FXPlatform)
    public abstract String getTransformationLabel();

    @OnThread(Tag.FXPlatform)
    public abstract Table getSource() throws InternalException, UserException;

    //@OnThread(Tag.FXPlatform)
    //public abstract void edit();


    @Override
    @OnThread(Tag.FXPlatform)
    public final void save(@Nullable File destination, FXPlatformConsumer<String> then)
    {
        OutputBuilder b = new OutputBuilder();
        // transformation : TRANSFORMATION tableId transformationName NEWLINE transformationDetail+;
        b.t(MainLexer.TRANSFORMATION).id(getId()).id(getTransformationLabel()).inner(() -> saveDetail(destination));
        then.consume(b.toString());
    }

    @OnThread(Tag.FXPlatform)
    protected abstract List<String> saveDetail(@Nullable File destination);
}
