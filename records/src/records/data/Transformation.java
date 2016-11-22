package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.gui.TableDisplay;
import records.loadsave.OutputBuilder;
import records.transformations.TransformationInfo;
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
    public Transformation(TableManager mgr, @Nullable TableId tableId)
    {
        super(mgr, tableId);
    }

    // Label to show between arrows:
    @OnThread(Tag.FXPlatform)
    public abstract String getTransformationLabel();

    @OnThread(Tag.FXPlatform)
    public abstract List<TableId> getSources();

    @OnThread(Tag.FXPlatform)
    public abstract TransformationInfo.TransformationEditor edit();


    @Override
    @OnThread(Tag.FXPlatform)
    public final void save(@Nullable File destination, FXPlatformConsumer<String> then)
    {
        OutputBuilder b = new OutputBuilder();
        // transformation : TRANSFORMATION tableId transformationName NEWLINE transformationDetail+;
        b.t(MainLexer.TRANSFORMATION).id(getId()).id(getTransformationName()).inner(() -> saveDetail(destination));
        savePosition(b);
        b.end().id(getId()).nl();
        then.consume(b.toString());
    }

    // The name as used when saving:
    @OnThread(Tag.Any)
    protected abstract String getTransformationName();

    @OnThread(Tag.FXPlatform)
    protected abstract List<String> saveDetail(@Nullable File destination);
}
