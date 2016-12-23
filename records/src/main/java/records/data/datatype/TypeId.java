package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TypeId
{
    private final String typeId;

    // package-visible
    TypeId(String typeId)
    {
        this.typeId = typeId;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeId tableId1 = (TypeId) o;

        return typeId.equals(tableId1.typeId);

    }

    @Override
    public int hashCode()
    {
        return typeId.hashCode();
    }

    @Override
    public String toString()
    {
        return typeId;
    }

    public String getOutput()
    {
        return typeId;
    }

    public String getRaw()
    {
        return typeId;
    }

    public static TypeId _testMake(String typeName)
    {
        return new TypeId(typeName);
    }
}
