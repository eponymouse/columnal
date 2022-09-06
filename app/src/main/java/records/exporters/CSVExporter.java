package records.exporters;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import records.data.Column;
import records.data.RecordSet;
import records.data.Table;
import records.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.TranslationUtility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CSVExporter implements Exporter
{
    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.files.text");
    }

    @Override
    public @OnThread(Tag.Any) ImmutableList<String> getSupportedFileTypes()
    {
        return ImmutableList.of("*.csv", "*.txt");
    }

    @Override
    @OnThread(Tag.Simulation)
    public void exportData(File dest, Table data) throws UserException, InternalException
    {
        RecordSet rs = data.getData();
        // Write column names:
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8)))
        {
            List<Column> columns = rs.getColumns();
            for (int i = 0; i < columns.size(); i++)
            {
                Column column = columns.get(i);
                out.write(quoteCSV(column.getName().getRaw()));
                if (i < columns.size() - 1)
                    out.write(",");
            }
            out.write("\n");
            for (int row = 0; rs.indexValid(row); row += 1)
            {
                for (int i = 0; i < columns.size(); i++)
                {
                    out.write(quoteCSV(DataTypeUtility.valueToString(columns.get(i).getType().getCollapsed(row))));
                    if (i < columns.size() - 1)
                        out.write(",");
                }
                out.write("\n");
            }
        }
        catch (IOException e)
        {
            throw new UserException("Problem writing to file: " + dest.getAbsolutePath(), e);
        }
    }

    @OnThread(Tag.Any)
    private static String quoteCSV(String original)
    {
        return "\"" + original.replace("\"", "\"\"\"") + "\"";
    }
}
