package test.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;

import java.io.File;
import java.nio.charset.Charset;

/**
 * Created by neil on 26/10/2016.
 */
public class GeneratedTextFile
{
    private final File file;
    private final Charset charset;
    private final int lineCount;
    private final String separator;
    private final String quote;
    private final ImmutableList<DataType> columnTypes;
    // Each item in outer list is a column.
    private final ImmutableList<ImmutableList<@Value Object>> expectedColumns;

    public GeneratedTextFile(File file, Charset charset, int lineCount, String separator, String quote, ImmutableList<DataType> columnTypes, ImmutableList<ImmutableList<@Value Object>> expectedColumns)
    {
        this.file = file;
        this.charset = charset;
        this.lineCount = lineCount;
        this.separator = separator;
        this.quote = quote;
        this.columnTypes = columnTypes;
        this.expectedColumns = expectedColumns;
    }

    public File getFile()
    {
        return file;
    }

    public int getLineCount()
    {
        return lineCount;
    }

    @Override
    public String toString()
    {
        return "GeneratedTextFile{" +
            "charset=" + charset +
            ", lineCount=" + lineCount +
            ", separator='" + separator + '\'' +
            ", quote='" + quote + '\'' +
            '}';
    }

    public Charset getCharset()
    {
        return charset;
    }

    public String getSeparator()
    {
        return separator;
    }

    public String getQuote()
    {
        return quote;
    }

    public ImmutableList<DataType> getColumnTypes()
    {
        return columnTypes;
    }

    public int getColumnCount()
    {
        return columnTypes.size();
    }

    public @Value Object getExpectedValue(int column, int line)
    {
        return expectedColumns.get(column).get(line);
    }
}
