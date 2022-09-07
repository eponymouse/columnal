package records.importers;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.importers.GuessFormat.TrimChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by neil on 31/10/2016.
 */
public class Format
{
    public final TrimChoice trimChoice;
    public final ImmutableList<ColumnInfo> columnTypes;
    public final List<String> problems = new ArrayList<>();

    public Format(TrimChoice trimChoice, ImmutableList<ColumnInfo> columnTypes)
    {
        this.trimChoice = trimChoice;
        this.columnTypes = columnTypes;
    }

    public Format(Format copyFrom)
    {
        this(copyFrom.trimChoice, copyFrom.columnTypes);
    }

    public void recordProblem(String problem)
    {
        problems.add(problem);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Format format = (Format) o;
        return Objects.equals(trimChoice, format.trimChoice) &&
                Objects.equals(columnTypes, format.columnTypes);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(trimChoice, columnTypes);
    }

    @Override
    public String toString()
    {
        return "Format{" +
            ", columnTypes=" + columnTypes +
            '}';
    }
}
