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
import records.importers.GuessFormat.Import;
import records.importers.GuessFormat.TrimChoice;
import utility.Pair;
import utility.SimulationFunction;
import utility.UnitType;

import java.util.List;

abstract class ImportPlainTable implements Import<UnitType, ImmutableList<ColumnInfo>>
{
    private final int numSrcColumns;
    private final TableManager mgr;
    private final List<? extends List<String>> vals;

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
    public SimulationFunction<UnitType, Pair<TrimChoice, RecordSet>> loadSource()
    {
        return u -> {
            ImmutableList.Builder<ColumnInfo> columns = ImmutableList.builder();
            for (int i = 0; i < numSrcColumns; i++)
            {
                columns.add(new ColumnInfo(new TextColumnType(), columnName(i)));
            }
            EditableRecordSet recordSet = ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), vals, columns.build());
            return new Pair<>(new TrimChoice(0, 0, 0, 0), recordSet);
        };
    }

    public abstract ColumnId columnName(int index);

    @Override
    public SimulationFunction<Pair<UnitType, TrimChoice>, Pair<ImmutableList<ColumnInfo>, RecordSet>> loadDest()
    {
        return p -> {
            ImmutableList<ColumnInfo> columns = GuessFormat.guessGeneralFormat(mgr.getUnitManager(), vals, p.getSecond());
            return new Pair<>(columns, ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), vals, columns));
        };
    }
}
