package records.gui.expressioneditor;

import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import utility.FXPlatformConsumer;

import java.util.stream.Stream;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeParent> implements TypeParent
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
    public TypeParent getThisAsSemanticParent()
    {
        return this;
    }

    @Override
    protected void selfChanged()
    {
        ErrorDisplayerRecord errorDisplayers = new ErrorDisplayerRecord();
        ErrorAndTypeRecorder recorder = errorDisplayers.getRecorder();
        clearAllErrors();
        @Nullable DataType dataType = errorDisplayers.recordType(this, saveUnrecorded(errorDisplayers, recorder)).toDataType(getTypeManager());
        onChange.consume(dataType);
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
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
    protected boolean isMatchNode()
    {
        return false;
    }

    @Override
    public ImmutableSet<Character> terminatedByChars()
    {
        return ImmutableSet.of();
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
    }

    @Override
    public boolean isTuple()
    {
        return false;
    }

}
