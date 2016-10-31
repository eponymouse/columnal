package records.importers;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import records.data.Column;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.TextFileNumericColumn;
import records.data.TextFileStringColumn;
import records.data.type.NumericColumnType;
import records.error.FetchException;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by neil on 31/10/2016.
 */
public class HTMLImport
{
    @OnThread(Tag.Simulation)
    public static List<RecordSet> importHTMLFile(File htmlFile) throws IOException, InternalException
    {
        List<RecordSet> results = new ArrayList<>();
        Document doc = parse(htmlFile);
        Elements tables = doc.select("table");

        for (Element table : tables)
        {
            List<List<String>> vals = new ArrayList<>();
            for (Element tableBit : table.children())
            {
                if (!tableBit.tagName().equals("tbody"))
                    continue;

                for (Element row : tableBit.children())
                {
                    if (!row.tagName().equals("tr"))
                        continue;
                    List<String> rowVals = new ArrayList<>();
                    vals.add(rowVals);
                    for (Element cell : row.children())
                    {
                        if (!cell.tagName().equals("td"))
                            continue;
                        rowVals.add(cell.text());
                    }
                }
            }

            Format format = GuessFormat.guessGeneralFormat(vals);

            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (int i = 0; i < format.columnTypes.size(); i++)
            {
                ColumnInfo columnInfo = format.columnTypes.get(i);
                int iFinal = i;
                List<String> slice = Utility.slice(vals, format.headerRows, iFinal);
                if (columnInfo.type.isNumeric())
                {
                    columns.add(rs -> new MemoryNumericColumn(rs, columnInfo.title, ((NumericColumnType)columnInfo.type), format.blankRowsToIgnore, slice));
                }
                else if (columnInfo.type.isText())
                {
                    columns.add(rs -> new MemoryStringColumn(rs, columnInfo.title, format.blankRowsToIgnore, slice));
                }
                // If it's blank, should we add any column?
                // Maybe if it has title?                }
            }

            int len = vals.size() - format.headerRows;

            vals = null; // Make sure we don't keep a reference
            // Not because we null it, but because we make it non-final.
            results.add(new RecordSet(htmlFile.getName(), columns) {
                @Override
                public final boolean indexValid(int index) throws UserException
                {
                    return index < getLength();
                }

                @Override
                public int getLength() throws UserException
                {
                    return len;
                }
            });

        }
        return results;
    }

    @SuppressWarnings("nullness")
    private static Document parse(File htmlFile) throws IOException
    {
        return Jsoup.parse(htmlFile, null);
    }
}
