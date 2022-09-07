package xyz.columnal.data;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.ColumnMaker;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UnimplementedException;
import xyz.columnal.error.UserException;
import records.grammar.*;
import records.grammar.FormatParser.ColumnContext;
import records.grammar.FormatParser.ColumnNameContext;
import records.grammar.FormatParser.DefaultValueContext;
import records.grammar.FormatParser.TypeContext;
import records.grammar.MainParser.DataFormatContext;
import records.grammar.MainParser.DataSourceContext;
import records.grammar.MainParser.DataSourceImmediateContext;
import records.grammar.MainParser.DetailLineContext;
import records.grammar.MainParser.TableContext;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.ExFunction;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class DataSource extends Table
{
    public DataSource(TableManager mgr, InitialLoadDetails initialLoadDetails)
    {
        super(mgr, initialLoadDetails);
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
            List<LoadedFormat> format = loadFormat(manager.getTypeManager(), Utility.getDetailLines(immed.dataFormat().detailPrefixed()), true);
            List<ColumnMaker<?, ?>> columns = new ArrayList<>();

            //TODO check for data row even length, error if not (allow & ignore blank lines)
            for (int i = 0; i < format.size(); i++)
            {
                DataType t = format.get(i).dataType;
                ColumnId columnId = format.get(i).columnId;
                String defaultValueUnparsed = format.get(i).defaultValueUnparsed;
                if (defaultValueUnparsed == null)
                {
                    throw new InternalException("Null default value even though we are editable; should have thrown earlier.");
                }
                Either<String, @Value Object> defaultValue = Utility.<Either<String, @Value Object>, DataParser>parseAsOne(defaultValueUnparsed.trim(), DataLexer::new, DataParser::new, p -> DataType.loadSingleItem(t, p, false));
                columns.add(t.makeImmediateColumn(columnId, defaultValue.getRight("Default values cannot be invalid")));
            }
            LoadedRecordSet recordSet = new LoadedRecordSet(columns, immed);
            @ExpressionIdentifier String columnName = IdentifierUtility.fixExpressionIdentifier(immed.tableId().getText(), "Table");
            ImmediateDataSource immediateDataSource = new ImmediateDataSource(manager, loadDetails(new TableId(columnName), table.dataSource().dataSourceImmediate().dataFormat().detailPrefixed(), table.display()), recordSet);
            manager.record(immediateDataSource);
            return immediateDataSource;
        }
        else
            throw new UnimplementedException();
    }

    public static Table loadOne(TableManager manager, SaveTag saveTag, TableParser2.TableDataContext table) throws UserException, InternalException
    {
        TableParser2.DataSourceContext dataSource = table.dataSource();
        if (dataSource == null)
        {
            throw new UserException("Error parsing: \"" + (table.getText().length() > 100 ? (table.getText().substring(0, 100) + "...") : table.getText()) + "\"");
        }

        if (dataSource.dataSourceImmediate() != null)
        {
            TableParser2.DataSourceImmediateContext immed = dataSource.dataSourceImmediate();
            List<LoadedFormat> format = loadFormat(manager.getTypeManager(), Utility.getDetailLines(immed.dataFormat().detail()), true);
            List<ColumnMaker<?, ?>> columns = new ArrayList<>();

            //TODO check for data row even length, error if not (allow & ignore blank lines)
            for (int i = 0; i < format.size(); i++)
            {
                DataType t = format.get(i).dataType;
                ColumnId columnId = format.get(i).columnId;
                String defaultValueUnparsed = format.get(i).defaultValueUnparsed;
                if (defaultValueUnparsed == null)
                {
                    throw new InternalException("Null default value even though we are editable; should have thrown earlier.");
                }
                Either<String, @Value Object> defaultValue = Utility.<Either<String, @Value Object>, DataParser2>parseAsOne(defaultValueUnparsed.trim(), DataLexer2::new, DataParser2::new, p -> DataType.loadSingleItem(t, p, false));
                columns.add(t.makeImmediateColumn(columnId, defaultValue.getRight("Default values cannot be invalid")));
            }
            LoadedRecordSet recordSet = new LoadedRecordSet(columns, immed);
            @ExpressionIdentifier String columnName = IdentifierUtility.fixExpressionIdentifier(immed.tableId().getText(), "Table");
            ImmediateDataSource immediateDataSource = Utility.parseAsOne(Utility.getDetail(table.display().detail()), DisplayLexer::new, DisplayParser::new, p -> new ImmediateDataSource(manager, loadDetails(new TableId(columnName), saveTag, p.tableDisplayDetails()), recordSet));
            manager.record(immediateDataSource);
            return immediateDataSource;
        }
        else
            throw new UnimplementedException();
    }

    @OnThread(Tag.Any)
    public static class LoadedFormat
    {
        public final ColumnId columnId;
        public final DataType dataType;
        public final @Nullable String defaultValueUnparsed;

        public LoadedFormat(ColumnId columnId, DataType dataType, @Nullable String defaultValueUnparsed)
        {
            this.columnId = columnId;
            this.dataType = dataType;
            this.defaultValueUnparsed = defaultValueUnparsed;
        }
    }

    @OnThread(Tag.Any)
    public static List<LoadedFormat> loadFormat(TypeManager typeManager, List<String> formatLines, boolean editable) throws UserException, InternalException
    {
        List<LoadedFormat> r = new ArrayList<>();
        for (String line : formatLines)
        {
            Utility.parseAsOne(line, FormatLexer::new, FormatParser::new, p -> {
                ColumnContext column = p.column();
                ColumnNameContext colName;
                if (column == null || (colName = column.columnName()) == null)
                    throw new UserException("Problem on line " + line);
                ColumnId name = new ColumnId(IdentifierUtility.fromParsed(colName));
                if (r.stream().anyMatch(pr -> pr.columnId.equals(name)))
                    throw new UserException("Duplicate column name: \"" + name + "\"");

                TypeContext type = column.type();
                if (type == null)
                    throw new UserException("Null type on line \"" + line + "\" name: " + name + " type: " + type.getText());
                DefaultValueContext defaultCtx = column.defaultValue();
                if (defaultCtx == null && editable)
                    throw new UserException("Default value missing for potentially-editable table " + name);
                DataType dataType = typeManager.loadTypeUse(type);
                r.add(new LoadedFormat(name, dataType, defaultCtx == null ? null : defaultCtx.VALUE().getText()));
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
        public LoadedRecordSet(List<ColumnMaker<?, ?>> columns, DataSourceImmediateContext immed) throws InternalException, UserException
        {
            super(Utility.<ColumnMaker<?, ?>, SimulationFunction<RecordSet, EditableColumn>>mapList(columns, c -> create(c)), () -> Utility.loadDataOld1(Utility.getDetailLines(immed.values().detailPrefixed()), p ->
            {
                for (int i = 0; i < columns.size(); i++)
                {
                    columns.get(i).loadRow1(p);
                }
            }));
        }

        public LoadedRecordSet(List<ColumnMaker<?, ?>> columns, TableParser2.DataSourceImmediateContext immed) throws InternalException, UserException
        {
            super(Utility.<ColumnMaker<?, ?>, SimulationFunction<RecordSet, EditableColumn>>mapList(columns, c -> create(c)), () -> Utility.loadData(Utility.getDetailLines(immed.values().detail()), p ->
            {
                for (int i = 0; i < columns.size(); i++)
                {
                    if (i != 0)
                        p.comma();
                    columns.get(i).loadRow(p);
                }
            }));
        }

        public static <C extends EditableColumn, V> SimulationFunction<RecordSet, EditableColumn> create(DataType.ColumnMaker<C, V> c)
        {
            return rs -> c.apply(rs);
        }
    }
}
