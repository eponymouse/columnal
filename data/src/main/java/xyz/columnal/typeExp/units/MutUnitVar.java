package xyz.columnal.typeExp.units;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.styled.CommonStyles;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;

import java.util.Objects;

public class MutUnitVar implements Comparable<MutUnitVar>, StyledShowable
{
    // For Comparable and printing purposes
    private static long nextId = 0;
    private final long id = nextId++;
    
    // package-visible:
    @Nullable UnitExp pointer;

    public @Nullable Unit toConcreteUnit()
    {
        if (pointer == null)
            return null;
        return pointer.toConcreteUnit();
    }
    
    
    @Override
    public int compareTo(@NonNull MutUnitVar o)
    {
        return Long.compare(id, o.id);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MutUnitVar that = (MutUnitVar) o;
        return id == that.id;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }

    @Override
    public StyledString toStyledString()
    {
        String name = "_u" + id;
        if (pointer == null)
            return StyledString.styled(name, CommonStyles.ITALIC);
        else
            return StyledString.concat(StyledString.s(name + "[="), pointer.toStyledString(), StyledString.s("]"))
                .withStyle(CommonStyles.ITALIC);
    }
}
