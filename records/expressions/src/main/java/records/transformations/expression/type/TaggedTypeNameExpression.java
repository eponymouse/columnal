package records.transformations.expression.type;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.TypeEntry;
import records.loadsave.OutputBuilder;
import styled.StyledString;

public class TaggedTypeNameExpression extends TypeExpression
{
    private final TypeId typeName;

    public TaggedTypeNameExpression(TypeId typeName)
    {
        this.typeName = typeName;
    }

    @Override
    public SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>> loadAsSingle()
    {
        return (p, s) -> new TypeEntry(p, s, typeName.getRaw());
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s(typeName.getRaw());
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        return "TAGGED " + OutputBuilder.quotedIfNecessary(typeName.getRaw());
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        // By itself, not a valid type.  We rely on the type-application operator to spot us before calling us.
        return null;
    }
}
