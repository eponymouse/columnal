package records.typeExp.units;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.unit.Unit;
import styled.CommonStyles;
import styled.StyledShowable;
import styled.StyledString;

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
