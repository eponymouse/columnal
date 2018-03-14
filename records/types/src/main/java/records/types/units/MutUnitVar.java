package records.types.units;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.unit.SingleUnit;
import styled.CommonStyles;
import styled.StyledShowable;
import styled.StyledString;
import styled.StyledString.Style;

public class MutUnitVar implements Comparable<MutUnitVar>, StyledShowable
{
    // For Comparable and printing purposes
    private static long nextId = 0;
    private final long id = nextId++;
    
    // package-visible:
    @Nullable UnitExp pointer;

    @Override
    public int compareTo(@NotNull MutUnitVar o)
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
