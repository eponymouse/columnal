package records.importers;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 31/10/2016.
 */
public class TextFormat extends Format
{
    public char separator;

    public TextFormat(Format copyFrom, char separator)
    {
        super(copyFrom);
        this.separator = separator;
    }

    public TextFormat(int headerRows, List<ColumnInfo> columnTypes, char separator)
    {
        super(headerRows, columnTypes);
        this.separator = separator;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TextFormat that = (TextFormat) o;

        return separator == that.separator;

    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (int) separator;
        return result;
    }

    @Override
    public String toString()
    {
        return "TextFormat{" +
            "headerRows=" + headerRows +
            ", columnTypes=" + columnTypes.stream().map(c -> "\n" + c).collect(Collectors.joining()) +
            ", separator=" + separator +
            '}';
    }
}
