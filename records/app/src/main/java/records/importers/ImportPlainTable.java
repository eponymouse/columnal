package records.importers;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.columntype.TextColumnType;
import records.error.InternalException;
import records.error.UserException;
import records.importers.GuessFormat.GuessException;
import records.importers.GuessFormat.Import;
import records.importers.GuessFormat.TrimChoice;
import records.importers.ImportPlainTable.PlainImportInfo;
import utility.Pair;
import utility.SimulationFunction;
import utility.UnitType;

import java.util.List;

abstract class ImportPlainTable implements Import<UnitType, PlainImportInfo>
{
    private final int numSrcColumns;
    private final TableManager mgr;
    private final List<? extends List<String>> vals;

    // vals must be rectangular
    public ImportPlainTable(int numSrcColumns, TableManager mgr, List<? extends List<String>> vals)
    {
        this.numSrcColumns = numSrcColumns;
        this.mgr = mgr;
        this.vals = vals;
    }

    @Override
    public ObjectExpression<@Nullable UnitType> currentSrcFormat()
    {
        return new ReadOnlyObjectWrapper<>(UnitType.UNIT);
    }

    @Override
    public Pair<TrimChoice, RecordSet> loadSource(UnitType u) throws UserException, InternalException
    {
        ImmutableList.Builder<ColumnInfo> columns = ImmutableList.builder();
        for (int i = 0; i < numSrcColumns; i++)
        {
            columns.add(new ColumnInfo(new TextColumnType(), srcColumnName(i)));
        }
        EditableRecordSet recordSet = ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), vals, columns.build());
        return new Pair<>(GuessFormat.guessTrim(vals), recordSet);
    }

    public abstract ColumnId srcColumnName(int index);

    @Override
    public Pair<PlainImportInfo, RecordSet> loadDest(UnitType u, TrimChoice trimChoice) throws UserException, InternalException
    {
        ImmutableList<ColumnInfo> columns = GuessFormat.guessGeneralFormat(mgr.getUnitManager(), vals, trimChoice);
        return new Pair<>(new PlainImportInfo(columns, trimChoice), ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), trimChoice.trim(vals), columns));
    }
    
    public static class PlainImportInfo
    {
        public final ImmutableList<ColumnInfo> columnInfo;
        public final TrimChoice trim;

        public PlainImportInfo(ImmutableList<ColumnInfo> columnInfo, TrimChoice trim)
        {
            this.columnInfo = columnInfo;
            this.trim = trim;
        }
    }
}
