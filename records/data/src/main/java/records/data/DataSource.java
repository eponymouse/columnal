package records.data;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.ColumnMaker;
import records.data.datatype.TypeManager;
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
import utility.ExConsumer;
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
            List<Pair<ColumnId, DataType>> format = loadFormat(manager.getTypeManager(), immed.dataFormat());
            List<ColumnMaker<?>> columns = new ArrayList<>();

            //TODO check for data row even length, error if not (allow & ignore blank lines)
            for (int i = 0; i < format.size(); i++)
            {
                DataType t = format.get(i).getSecond();
                ColumnId columnId = format.get(i).getFirst();
                columns.add(t.makeImmediateColumn(columnId).markEditable());
            }
            LoadedRecordSet recordSet = new LoadedRecordSet(columns, immed, format);
            return new ImmediateDataSource(manager, new TableId(immed.tableId().getText()), recordSet);
        }
        else
            throw new UnimplementedException();
    }

    private static int loadData(DetailContext detail, ExConsumer<List<ItemContext>> withEachRow) throws UserException, InternalException
    {
        int count = 0;
        for (TerminalNode line : detail.DETAIL_LINE())
        {
            count += 1;
            String lineText = line.getText();
            try
            {
                Utility.parseAsOne(lineText, DataLexer::new, DataParser::new, p ->
                {
                    RowContext row = p.row();
                    if (row != null)
                    {
                        withEachRow.accept(row.item());
                    }
                    return 0;
                });
            }
            catch (UserException e)
            {
                throw new UserException("Error loading data line: \"" + lineText + "\"", e);
            }
        }
        return count;
    }

    private static List<Pair<ColumnId, DataType>> loadFormat(TypeManager typeManager, DataFormatContext dataFormatContext) throws UserException, InternalException
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
                r.add(new Pair<>(name, typeManager.loadTypeUse(type)));
                return 0;
            });
        }
        return r;
    }

    // hashCode and equals must be implemented properly (used for testing).
    // To make sure we don't forget, we introduce abstract methods which must
    // be overridden.  (We don't make hashCode and equals themselves abstract
    // because subclasses would then lose access to Table.hashCode which they'd need).
    @Override
    public final int hashCode()
    {
        return 31 * super.hashCode() + dataHashCode();
    }

    protected abstract int dataHashCode();

    @Override
    public final boolean equals(@Nullable Object obj)
    {
        if (!super.equals(obj))
            return false;
        return dataEquals((DataSource)obj);
    }

    protected abstract boolean dataEquals(DataSource obj);

    private static class LoadedRecordSet extends EditableRecordSet
    {
        public LoadedRecordSet(List<ColumnMaker<?>> columns, DataSourceImmediateContext immed, List<Pair<ColumnId, DataType>> format) throws InternalException, UserException
        {
            super(Utility.mapList(columns, c -> c::apply), () -> loadData(immed.detail(), row ->
            {
                for (int i = 0; i < format.size(); i++)
                {
                    columns.get(i).loadRow(row.get(i));
                }
            }));
        }
    }
}
