package records.data.unit;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;

public class SingleUnitVar extends SingleUnit
{
    private final String unitVarName;

    public SingleUnitVar(String unitVarName)
    {
        this.unitVarName = unitVarName;
    }

    @Override
    public String getPrefix()
    {
        return "";
    }

    @Override
    public String getSuffix()
    {
        return "";
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SingleUnitVar that = (SingleUnitVar) o;
        return Objects.equals(unitVarName, that.unitVarName);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(unitVarName);
    }

    public String getVarName()
    {
        return unitVarName;
    }
}
