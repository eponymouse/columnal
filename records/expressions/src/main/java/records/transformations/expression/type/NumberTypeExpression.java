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
    private final UnitExpression unitExpression;

    public NumberTypeExpression(UnitExpression unitExpression)
    {
        this.unitExpression = unitExpression;
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        if (unitExpression.isEmpty() || unitExpression.isScalar())
            return "NUMBER";
        else
            return "NUMBER {" + unitExpression.save(true) + "}"; 
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return unitExpression.asUnit(typeManager.getUnitManager())
            .<@Nullable DataType>either(err -> null, unitExp -> {
                @Nullable Unit unit = unitExp.toConcreteUnit();
                return unit == null ? null : DataType.number(new NumberInfo(unit));
            });
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
        return StyledString.concat(StyledString.s("NUMBER {"), unitExpression.toStyledString(), StyledString.s("}"));
    }

    public UnitExpression _test_getUnits()
    {
        return unitExpression;
    }
}
