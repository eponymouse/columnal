package records.data;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.DataLexer;
import records.grammar.DataParser;
import records.grammar.DataParser.ItemContext;
import records.grammar.DataParser.RowContext;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.ColumnContext;
import records.grammar.FormatParser.ColumnNameContext;
import records.grammar.FormatParser.TypeContext;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.DataFormatContext;
import records.grammar.MainParser.DataSourceContext;
import records.grammar.MainParser.DataSourceImmediateContext;
import records.grammar.MainParser.DetailContext;
import records.grammar.MainParser.TableContext;
import records.transformations.expression.TypeState;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

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
        return Utility.parseAsOne(src, MainLexer::new, MainParser::new, parser -> loadOne(manager, parser.table()));
    }

    public static Table loadOne(TableManager manager, TableContext table) throws UserException, InternalException
    {
        DataSourceContext dataSource = table.dataSource();
        if (dataSource == null)
        {
            throw new UserException("Error parsing: \"" + (table.getText().length() > 100 ? (table.getText().substring(0, 100) + "...") : table.getText()) + "\"");
        }

        if (dataSource.dataSourceImmediate() != null)
        {
            DataSourceImmediateContext immed = dataSource.dataSourceImmediate();
            List<Pair<ColumnId, DataType>> format = loadFormat(manager.getUnitManager(), manager.getTypeState(), immed.dataFormat());
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();

            List<List<ItemContext>> dataRows = loadData(immed.detail());
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

    private static List<List<ItemContext>> loadData(DetailContext detail) throws UserException, InternalException
    {
        List<List<ItemContext>> rows = new ArrayList<>();
        for (TerminalNode line : detail.DETAIL_LINE())
        {
            Utility.parseAsOne(line.getText(), DataLexer::new, DataParser::new, p -> {
                RowContext row = p.row();
                if (row != null)
                {
                    rows.add(row.item());
                }
                return 0;
            });
        }
        return rows;
    }

    private static List<Pair<ColumnId, DataType>> loadFormat(UnitManager mgr, TypeState typeState, DataFormatContext dataFormatContext) throws UserException, InternalException
    {
        List<Pair<ColumnId, DataType>> r = new ArrayList<>();
        for (TerminalNode line : dataFormatContext.detail().DETAIL_LINE())
        {
            Utility.parseAsOne(line.getText(), FormatLexer::new, FormatParser::new, p -> {
                ColumnContext column = p.column();
                ColumnNameContext colName;
                if (column == null || (colName = column.columnName()) == null)
                    throw new UserException("Problem on line " + line.getText());
                ColumnId name = new ColumnId(colName.getText());
                if (r.stream().anyMatch(pr -> pr.getFirst().equals(name)))
                    throw new UserException("Duplicate column name: \"" + name + "\"");

                TypeContext type = column.type();
                if (type == null)
                    throw new UserException("Null type on line \"" + line.getText() + "\" name: " + name + " type: " + type.getText());
                r.add(new Pair<>(name, DataType.loadType(mgr, typeState, type)));
                return 0;
            });
        }
        return r;
    }
}
