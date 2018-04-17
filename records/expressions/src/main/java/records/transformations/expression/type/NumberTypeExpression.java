package records.transformations.expression.type;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.TypeEntry;
import records.transformations.expression.UnitExpression;
import styled.StyledString;

public class NumberTypeExpression extends TypeExpression
{
    private final @Nullable UnitExpression unitExpression;

    public NumberTypeExpression(@Nullable UnitExpression unitExpression)
    {
        this.unitExpression = unitExpression;
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        if (unitExpression == null || unitExpression.isEmpty() || unitExpression.isScalar())
            return "NUMBER";
        else
            return "NUMBER {" + unitExpression.save(true) + "}"; 
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return unitExpression == null ? DataType.NUMBER : unitExpression.asUnit(typeManager.getUnitManager())
            .<@Nullable DataType>either(err -> null, unitExp -> {
                @Nullable Unit unit = unitExp.toConcreteUnit();
                return unit == null ? null : DataType.number(new NumberInfo(unit));
            });
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>> loadAsSingle()
    {
        // TODO
        return (p, s) -> new TypeEntry(p, s, "TODONUMBER");
    }

    @Override
    public StyledString toStyledString()
    {
        if (unitExpression == null)
            return StyledString.s("Number");
        else
            return StyledString.concat(StyledString.s("Number{"), unitExpression.toStyledString(), StyledString.s("}"));
    }

    public @Nullable UnitExpression _test_getUnits()
    {
        return unitExpression;
    }
}
