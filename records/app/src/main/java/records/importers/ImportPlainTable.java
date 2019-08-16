package records.importers;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import org.checkerframework.checker.i18n.qual.Localized;
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
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.UnitType;

import java.util.List;

abstract class ImportPlainTable implements Import<UnitType, PlainImportInfo>
{
    private final int numSrcColumns;
    private final TableManager mgr;
    private final List<List<String>> vals;
    protected final SimpleObjectProperty<@Nullable UnitType> format = new SimpleObjectProperty<>(UnitType.UNIT);

    // vals must be rectangular
    public ImportPlainTable(int numSrcColumns, TableManager mgr, List<? extends List<String>> vals)
    {
        this.numSrcColumns = numSrcColumns;
        this.mgr = mgr;
        // Fix type issue:
        this.vals = vals.stream().<List<String>>map(v -> v).collect(ImmutableList.<List<String>>toImmutableList());
    }

    @Override
    public ObjectExpression<@Nullable UnitType> currentSrcFormat()
    {
        return format;
    }

    @Override
    public SrcDetails loadSource(UnitType u) throws UserException, InternalException
    {
        TrimChoice trimChoice = GuessFormat.guessTrim(vals);
        ImmutableList.Builder<ColumnInfo> columns = ImmutableList.builder();
        ImmutableList.Builder<@Localized String> columnDisplayNames = ImmutableList.builder();
        for (int i = 0; i < numSrcColumns; i++)
        {
            Pair<ColumnId, @Localized String> name = srcColumnName(i);
            columns.add(new ColumnInfo(new TextColumnType(), name.getFirst()));
            columnDisplayNames.add(name.getSecond());
        }
        EditableRecordSet recordSet = ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), vals, columns.build());
        return new SrcDetails(trimChoice, recordSet, columnDisplayNames.build());
    }

    // Gets internal column name, and name to display in GUI for src table
    public abstract Pair<ColumnId, @Localized String> srcColumnName(int index);

    public ColumnId destColumnName(TrimChoice trimChoice, int index)
    {
        return srcColumnName(index).getFirst();
    }

    @Override
    public Pair<PlainImportInfo, RecordSet> loadDest(UnitType u, TrimChoice trimChoice) throws UserException, InternalException
    {
        ImmutableList<ColumnInfo> columns = GuessFormat.guessGeneralFormat(mgr.getUnitManager(), processTrimmed(vals), trimChoice, (trim, i) -> destColumnName(trim, i));
        return new Pair<>(new PlainImportInfo(columns, trimChoice), ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), processTrimmed(trimChoice.trim(vals)), columns));
    }

    // Allows for String trim()ing in subclass
    @OnThread(Tag.Simulation)
    public List<? extends List<String>> processTrimmed(List<List<String>> trimmed)
    {
        return trimmed;
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
