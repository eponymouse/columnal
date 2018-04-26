package records.transformations.expression.type;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.TypeEntry;
import records.loadsave.OutputBuilder;
import records.transformations.expression.LoadableExpression;
import styled.StyledString;
import utility.Pair;
import utility.UnitType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class UnfinishedTypeExpression extends TypeExpression
{
    private final String value;

    public UnfinishedTypeExpression(String value)
    {
        this.value = value;
    }

    @Override
    public SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>> loadAsSingle()
    {
        return (p, s) -> new TypeEntry(p, s, value);
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s("Unfinished: \"" + value + "\"");
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        return "@INCOMPLETE " + OutputBuilder.quoted(value);
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return null;
    }

    @Override
    public boolean isEmpty()
    {
        return value.isEmpty();
    }

    public String _test_getContent()
    {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnfinishedTypeExpression that = (UnfinishedTypeExpression) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(value);
    }
}
