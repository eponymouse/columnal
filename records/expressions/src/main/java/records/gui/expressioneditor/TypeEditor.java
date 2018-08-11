package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeSaver;
import records.transformations.expression.type.UnfinishedTypeExpression;
import utility.FXPlatformConsumer;
import utility.Utility;

import java.util.stream.Stream;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeSaver>
{
    private final FXPlatformConsumer<@Nullable DataType> onChange;

    public TypeEditor(TypeManager typeManager, TypeExpression startingValue, FXPlatformConsumer<@Nullable DataType> onChange)
    {
        super(new TypeExpressionOps(), typeManager, "type-editor");
        
        this.onChange = onChange;
        onChange.consume(null);
        
        // Safe because at end of constructor:
        Utility.later(this).loadContent(startingValue, true);
    }

    @Override
    protected void selfChanged()
    {
        super.selfChanged();
        clearAllErrors();
        @UnknownIfRecorded TypeExpression typeExpression = save();
        @Nullable DataType dataType = typeExpression.toDataType(getTypeManager());
        Log.debug("Latest type: " + dataType + " from expression: " + typeExpression.save(new TableAndColumnRenames(ImmutableMap.of())));
        onChange.consume(dataType);
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
    }

    @Override
    public @Recorded TypeExpression save()
    {
        TypeSaver saver = new TypeSaver(this);
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
}
