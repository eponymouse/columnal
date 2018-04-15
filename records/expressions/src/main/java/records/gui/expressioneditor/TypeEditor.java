package records.gui.expressioneditor;

import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import utility.UnitType;

import java.util.stream.Stream;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeParent> implements TypeParent
{
    public TypeEditor(TableManager tableManager, TypeExpression startingValue)
    {
        super(new TypeExpressionOps(), tableManager, "type-editor");
        
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

    public @Nullable DataType getValue()
    {
        ErrorDisplayerRecord errorDisplayers = new ErrorDisplayerRecord();
        ErrorAndTypeRecorder recorder = errorDisplayers.getRecorder();
        clearAllErrors();
        return errorDisplayers.recordType(this, saveUnrecorded(errorDisplayers, recorder)).toDataType();
    }
}
