package records.importers;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by neil on 31/10/2016.
 */
public class TextFormat extends Format
{
    public final Charset charset;
    // null means no separator; just one big column
    public final @Nullable String separator;

    public TextFormat(Format copyFrom, @Nullable String separator, Charset charset)
    {
        super(copyFrom);
        this.separator = separator;
        this.charset = charset;
    }

    public TextFormat(int headerRows, List<ColumnInfo> columnTypes, @Nullable String separator, Charset charset)
    {
        super(headerRows, columnTypes);
        this.separator = separator;
        this.charset = charset;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TextFormat that = (TextFormat) o;

        return Objects.equals(separator, that.separator);

    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (separator == null ? 0 : separator.hashCode());
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

    // Copies format but replaces separator
    public TextFormat withSeparator(String sep)
    {
        return new TextFormat(this, sep, charset);
    }
}
