/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.data;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UnimplementedException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.DataLexer;
import xyz.columnal.grammar.DataLexer2;
import xyz.columnal.grammar.DataParser;
import xyz.columnal.grammar.DataParser2;
import xyz.columnal.grammar.DisplayLexer;
import xyz.columnal.grammar.DisplayParser;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.grammar.FormatParser;
import xyz.columnal.grammar.FormatParser.ColumnContext;
import xyz.columnal.grammar.FormatParser.ColumnNameContext;
import xyz.columnal.grammar.FormatParser.DefaultValueContext;
import xyz.columnal.grammar.FormatParser.TypeContext;
import xyz.columnal.grammar.MainParser.DataSourceContext;
import xyz.columnal.grammar.MainParser.DataSourceImmediateContext;
import xyz.columnal.grammar.MainParser.TableContext;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.grammar.TableParser2;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.SaveTag;
import xyz.columnal.id.TableId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.function.simulation.SimulationFunction;
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
                columns.add(ColumnUtility.makeImmediateColumn(t, columnId, defaultValue.getRight("Default values cannot be invalid")));
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
                columns.add(ColumnUtility.makeImmediateColumn(t, columnId, defaultValue.getRight("Default values cannot be invalid")));
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

        public static <C extends EditableColumn, V> SimulationFunction<RecordSet, EditableColumn> create(ColumnMaker<C, V> c)
        {
            return rs -> c.apply(rs);
        }
    }
}
