package records.transformations.expression.type;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.jellytype.JellyUnit;
import records.transformations.expression.QuickFix;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpression.UnitLookupException;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Objects;

public class NumberTypeExpression extends TypeExpression
{
    private final @Nullable @Recorded UnitExpression unitExpression;

    public NumberTypeExpression(@Nullable @Recorded UnitExpression unitExpression)
    {
        this.unitExpression = unitExpression;
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        if (unitExpression == null || unitExpression.isEmpty() || unitExpression.isScalar())
            return "Number";
        else
            return "Number{" + unitExpression.save(structured, true) + "}"; 
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        try
        {
            return unitExpression == null ? DataType.NUMBER : DataType.number(new NumberInfo(unitExpression.asUnit(typeManager.getUnitManager()).makeUnit(ImmutableMap.of())));
        }
        catch (UnitLookupException e)
        {
            return null;
        }
        catch (InternalException e)
        {
            Log.log(e);
            return null;
        }
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded NumberTypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression
    {
        if (unitExpression == null)
            return jellyRecorder.record(JellyType.number(JellyUnit.fromConcrete(Unit.SCALAR)), this);

        try
        {
            return jellyRecorder.record(JellyType.number(unitExpression.asUnit(typeManager.getUnitManager())), this);
        }
        catch (UnitLookupException e)
        {
            throw new UnJellyableTypeExpression(e.errorMessage == null ? StyledString.s("Invalid unit") : e.errorMessage, e.errorItem, e.quickFixes);
        }
    }

    @Override
    public boolean isEmpty()
    {
        return false;
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

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumberTypeExpression that = (NumberTypeExpression) o;
        return Objects.equals(unitExpression, that.unitExpression)
            || (unitExpression == null && that.unitExpression != null && that.unitExpression.isScalar())
            || (that.unitExpression == null && unitExpression != null && unitExpression.isScalar());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(unitExpression);
    }

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    public boolean hasUnit()
    {
        return unitExpression != null;
    }
}
