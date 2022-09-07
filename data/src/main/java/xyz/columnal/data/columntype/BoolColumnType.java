package records.data.columntype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.utility.Either;

import java.util.Objects;

public class BoolColumnType extends ColumnType
{
    private final String lowerCaseTrue;
    private final String lowerCaseFalse;

    public BoolColumnType(String lowerCaseTrue, String lowerCaseFalse)
    {
        this.lowerCaseTrue = lowerCaseTrue;
        this.lowerCaseFalse = lowerCaseFalse;
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

    public Either<String, Boolean> parse(@NonNull String s)
    {
        if (s.trim().equalsIgnoreCase(lowerCaseTrue))
            return Either.right(Boolean.TRUE);
        else if (s.trim().equalsIgnoreCase(lowerCaseFalse))
            return Either.right(Boolean.FALSE);
        else
            return Either.left(s);
    }
}
