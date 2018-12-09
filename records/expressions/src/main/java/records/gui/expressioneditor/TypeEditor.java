package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeSaver;
import utility.FXPlatformConsumer;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.stream.Stream;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeSaver>
{
    public static final DataFormat TYPE_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-type");
    private final FXPlatformConsumer<TypeExpression> onChange;

    public TypeEditor(TypeManager typeManager, TypeExpression startingValue, FXPlatformConsumer<TypeExpression> onChange)
    {
        super(new TypeExpressionOps(), typeManager, "type-editor");
        
        this.onChange = onChange;
        onChange.consume(startingValue);
        
        // Safe because at end of constructor:
        Utility.later(this).loadContent(startingValue, true);
    }

    @Override
    protected void selfChanged()
    {
        super.selfChanged();
        clearAllErrors();
        @UnknownIfRecorded TypeExpression typeExpression = save();
        saved();
        //String asStr = typeExpression.save(new TableAndColumnRenames(ImmutableMap.of()));
        //@Nullable DataType dataType = typeExpression.toDataType(getTypeManager());
        //Log.debug("Latest type: " + dataType + " from expression: " + asStr);
        onChange.consume(typeExpression);
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
    }

    @Override
    public @Recorded TypeExpression save()
    {
        TypeSaver saver = new TypeSaver(this, true);
        save(saver);
        return saver.finish(children.get(children.size() - 1));
    }

    @Override
    protected void parentFocusRightOfThis(Focus side, boolean becauseOfTab)
    {

    }

    @Override
    protected void parentFocusLeftOfThis()
    {

    }
    
    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
    }

    @Override
    public boolean showCompletionImmediately(@UnknownInitialization ConsecutiveChild<TypeExpression, TypeSaver> child)
    {
        // We show if the current type is at all incomplete.  Slight hack for complex types
        // (will show after [] when issue is between brackets) but works fine for simple types.
        
        // TODO this should be toJellyType, once we add type variable usage to the editor
        return mostRecentSave == null || mostRecentSave.toDataType(getTypeManager()) == null;
    }

    @Override
    public DataFormat getClipboardType()
    {
        return TYPE_CLIPBOARD_TYPE;
    }

    @Override
    protected @Nullable LoadableExpression<TypeExpression, TypeSaver> parse(String src) throws InternalException, UserException
    {
        return TypeExpression.parseTypeExpression(getTypeManager(), src);
    }
}
