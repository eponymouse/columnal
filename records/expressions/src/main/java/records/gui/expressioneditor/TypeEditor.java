package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeSaver;
import records.transformations.expression.type.UnfinishedTypeExpression;
import utility.FXPlatformConsumer;

import java.util.stream.Stream;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeSaver>
{
    private final FXPlatformConsumer<@Nullable DataType> onChange;

    public TypeEditor(TableManager tableManager, TypeExpression startingValue, FXPlatformConsumer<@Nullable DataType> onChange)
    {
        super(new TypeExpressionOps(), tableManager, "type-editor");
        
        this.onChange = onChange;
        onChange.consume(null);
        
        loadContent(startingValue);
    }

    @Override
    protected void selfChanged()
    {
        super.selfChanged();
        ErrorDisplayerRecord errorDisplayers = new ErrorDisplayerRecord();
        ErrorAndTypeRecorder recorder = errorDisplayers.getRecorder();
        clearAllErrors();
        /*
        @UnknownIfRecorded TypeExpression typeExpression = saveUnrecorded(errorDisplayers, recorder);
        @Nullable DataType dataType = errorDisplayers.recordType(this, typeExpression).toDataType(getTypeManager());
        Log.debug("Latest type: " + dataType + " from expression: " + typeExpression.save(new TableAndColumnRenames(ImmutableMap.of())));
        onChange.consume(dataType);
        */
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
    }

    @Override
    @SuppressWarnings("recorded") // TODO implement this method and remove this
    public @Recorded TypeExpression save()
    {
        return new UnfinishedTypeExpression("TODO");
    }

    @Override
    protected void parentFocusRightOfThis(Focus side)
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
}
