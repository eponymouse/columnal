package records.importers;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class Format
{
    public final int headerRows;
    public final List<Integer> blankRowsToIgnore;
    public final List<ColumnInfo> columnTypes;
    public final List<String> problems = new ArrayList<>();

    public Format(int headerRows, List<ColumnInfo> columnTypes, List<Integer> blankRowsToIgnore)
    {
        this.headerRows = headerRows;
        this.columnTypes = columnTypes;
        this.blankRowsToIgnore = blankRowsToIgnore;
    }

    public Format(Format copyFrom)
    {
        this(copyFrom.headerRows, copyFrom.columnTypes, copyFrom.blankRowsToIgnore);
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

        if (headerRows != format.headerRows) return false;
        return columnTypes.equals(format.columnTypes);

    }

    @Override
    public int hashCode()
    {
        int result = headerRows;
        result = 31 * result + columnTypes.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "Format{" +
            "headerRows=" + headerRows +
            ", columnTypes=" + columnTypes +
            '}';
    }
}
