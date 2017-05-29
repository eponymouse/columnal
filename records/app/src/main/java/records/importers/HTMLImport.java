package records.importers;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import records.data.*;
import records.data.datatype.DataTypeUtility;
import utility.TaggedValue;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.unit.Unit;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class HTMLImport
{
    @OnThread(Tag.Simulation)
    public static List<DataSource> importHTMLFile(TableManager mgr, File htmlFile) throws IOException, InternalException, UserException
    {
        List<DataSource> results = new ArrayList<>();
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

            Format format = GuessFormat.guessGeneralFormat(mgr.getUnitManager(), vals);

            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (int i = 0; i < format.columnTypes.size(); i++)
            {
                ColumnInfo columnInfo = format.columnTypes.get(i);
                int iFinal = i;
                List<String> slice = Utility.sliceSkipBlankRows(vals, format.headerRows, iFinal);
                ColumnType columnType = columnInfo.type;
                if (columnType instanceof NumericColumnType)
                {
                    //TODO remove prefix
                    // TODO treat maybe blank as a tagged type
                    columns.add(rs ->
                    {
                        NumericColumnType numericColumnType = (NumericColumnType) columnType;
                        return new MemoryNumericColumn(rs, columnInfo.title, new NumberInfo(numericColumnType.unit, numericColumnType.displayInfo), slice.stream().map(numericColumnType::removePrefix));
                    });
                }
                else if (columnType instanceof TextColumnType)
                {
                    columns.add(rs -> new MemoryStringColumn(rs, columnInfo.title, slice));
                }
                else if (columnType instanceof CleanDateColumnType)
                {
                    columns.add(rs -> new MemoryTemporalColumn(rs, columnInfo.title, ((CleanDateColumnType) columnType).getDateTimeInfo(), Utility.<String, TemporalAccessor>mapList(slice, s -> ((CleanDateColumnType) columnType).parse(s))));
                }
                else if (columnType instanceof OrBlankColumnType && ((OrBlankColumnType)columnType).getInner() instanceof NumericColumnType)
                {
                    OrBlankColumnType or = (OrBlankColumnType) columnType;
                    NumericColumnType inner = (NumericColumnType) or.getInner();
                    String idealTypeName = "?Number{" + (inner.unit.equals(Unit.SCALAR) ? "" : inner.unit) + "}";
                    @Nullable DataType type = mgr.getTypeManager().lookupType(idealTypeName);
                    // Only way it's there already is if it's same from another column, so use it.
                    if (type == null)
                    {
                        type = mgr.getTypeManager().registerTaggedType(idealTypeName, Arrays.asList(
                            new TagType<DataType>("Blank", null),
                            new TagType<DataType>(idealTypeName.substring(1), DataType.number(new NumberInfo(inner.unit, inner.displayInfo)))
                        ));
                    }
                    @NonNull DataType typeFinal = type;
                    columns.add(rs -> new MemoryTaggedColumn(rs, columnInfo.title, typeFinal.getTaggedTypeName(), typeFinal.getTagTypes(), Utility.mapListEx(slice, item -> {
                        if (item.isEmpty())
                            return new TaggedValue(0, null);
                        else
                            return new TaggedValue(1, DataTypeUtility.value(Utility.parseNumber(inner.removePrefix(item))));
                    })));
                }
                else if (!(columnType instanceof BlankColumnType))
                {
                    throw new InternalException("Unhandled column type: " + columnType.getClass());
                }
                // If it's blank, should we add any column?
                // Maybe if it has title?                }
            }

            int len = vals.size() - format.headerRows - (int)vals.stream().skip(format.headerRows).filter(r -> r.stream().allMatch(String::isEmpty)).count();

            vals = null; // Make sure we don't keep a reference
            // Not because we null it, but because we make it non-final.
            results.add(new ImmediateDataSource(mgr, new EditableRecordSet(columns, () -> len)));

        }
        return results;
    }

    @SuppressWarnings("nullness")
    private static Document parse(File htmlFile) throws IOException
    {
        return Jsoup.parse(htmlFile, null);
    }
}
