package records.data;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.DataFormatContext;
import records.grammar.MainParser.DataSourceContext;
import records.grammar.MainParser.DataSourceImmediateContext;
import records.grammar.MainParser.DetailContext;
import records.grammar.MainParser.TableContext;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class DataSource extends Table
{
    public DataSource(TableManager mgr, @Nullable TableId id)
    {
        super(mgr, id);
    }

    public static Table loadOne(TableManager manager, String src) throws UserException, InternalException
    {
        return Utility.parseAsOne(src, MainLexer::new, MainParser::new, parser -> load(manager, parser.table()));
    }

    private static Table load(TableManager manager, TableContext table) throws UserException, InternalException
    {
        DataSourceContext dataSource = table.dataSource();
        if (dataSource.dataSourceImmediate() != null)
        {
            DataSourceImmediateContext immed = dataSource.dataSourceImmediate();
            List<Pair<ColumnId, DataType>> format = loadFormat(immed.dataFormat());
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();

            List<List<String>> dataRows = loadRawData(immed.detail());
            //TODO check for data row even length, error if not (allow & ignore blank lines)
            for (int i = 0; i < format.size(); i++)
            {
                DataType t = format.get(i).getSecond();
                ColumnId columnId = format.get(i).getFirst();
                int iFinal = i;
                columns.add(rs -> t.makeImmediateColumn(rs, columnId, dataRows, iFinal));
            }
            int length = dataRows.size();
            return new ImmediateDataSource(manager, new TableId(immed.tableId().getText()), new KnownLengthRecordSet("Data", columns, length));
        }
        else
            throw new UnimplementedException();
    }

    private static List<List<String>> loadRawData(DetailContext detail)
    {
        //TODO split on space is not enough, if strings have spaces.  Need to lex.
        return detail.DETAIL_LINE().stream().map(l -> l.getText().trim()).filter(l -> !l.isEmpty()).map(l -> Arrays.<@NonNull String>asList(l.split("\\s+"))).collect(Collectors.<@NonNull List<@NonNull String>>toList());
    }

    private static List<Pair<ColumnId, DataType>> loadFormat(DataFormatContext dataFormatContext)
    {
        //TODO
        return Collections.singletonList(new Pair<>(new ColumnId("DUMMY"), DataType.TEXT));
    }
}
