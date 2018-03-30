package records.data.columntype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;

public class BoolColumnType extends ColumnType
{
    private final String lowerCaseTrue;

    public BoolColumnType(String lowerCaseTrue)
    {
        this.lowerCaseTrue = lowerCaseTrue;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoolColumnType that = (BoolColumnType) o;
        return Objects.equals(lowerCaseTrue, that.lowerCaseTrue);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lowerCaseTrue);
    }

    @Override
    public String toString()
    {
        return "Boolean[" + lowerCaseTrue + "]";
    }

    public boolean isTrue(@NonNull String s)
    {
        return s.trim().equalsIgnoreCase(lowerCaseTrue);
    }
}
