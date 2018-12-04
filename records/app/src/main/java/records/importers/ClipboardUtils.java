package records.importers;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.DataSource;
import records.data.DataSource.LoadedFormat;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.IsolatedValuesContext;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class ClipboardUtils
{

    public static final DataFormat DATA_FORMAT = FXUtility.getDataFormat("application/records");

    // Returns column-major, i.e. a list of columns
    @OnThread(Tag.FXPlatform)
    public static Optional<List<Pair<ColumnId, List<@Value Object>>>> loadValuesFromClipboard(TypeManager typeManager)
    {
        @Nullable Object content = Clipboard.getSystemClipboard().getContent(DATA_FORMAT);

        if (content == null)
            return Optional.empty();
        else
        {
            try
            {
                return Optional.of(Utility.<List<Pair<ColumnId,List<@Value Object>>>, MainParser>parseAsOne(content.toString(), MainLexer::new, MainParser::new, p -> loadIsolatedValues(p, typeManager)));
            }
            catch (UserException e)
            {
                FXUtility.showError("Error copying content", e);
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
            return Optional.empty();
        }
    }

    private static List<Pair<ColumnId, List<@Value Object>>> loadIsolatedValues(MainParser main, TypeManager typeManager) throws InternalException, UserException
    {
        IsolatedValuesContext ctx = main.isolatedValues();
        // TODO check that units, types match
        List<LoadedFormat> format = DataSource.loadFormat(typeManager, ctx.dataFormat(), false);
        List<Pair<ColumnId, List<@Value Object>>> cols = new ArrayList<>();
        for (int i = 0; i < format.size(); i++)
        {
            cols.add(new Pair<>(format.get(i).columnId, new ArrayList<>()));
        }
        Utility.loadData(ctx.values().detail(), p -> {
            for (int i = 0; i < format.size(); i++)
            {
                LoadedFormat colFormat = format.get(i);
                cols.get(i).getSecond().add(DataType.loadSingleItem(colFormat.dataType, p, false));
            }
        });
        return cols;
    }
    
    public static class RowRange
    {
        private final @TableDataRowIndex int startRowIncl;
        private final @TableDataRowIndex int endRowIncl;

        public RowRange(@TableDataRowIndex int startRowIncl, @TableDataRowIndex int endRowIncl)
        {
            this.startRowIncl = startRowIncl;
            this.endRowIncl = endRowIncl;
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void copyValuesToClipboard(UnitManager unitManager, TypeManager typeManager, List<Pair<ColumnId, DataTypeValue>> columns, SimulationSupplier<RowRange> rowRangeSupplier)
    {
        if (columns.isEmpty())
            return;

        Workers.onWorkerThread("Copying to clipboard", Priority.FETCH, () -> {
            OutputBuilder b = new OutputBuilder();
            b.t(MainLexer.UNITS).begin().nl();
            b.end().t(MainLexer.UNITS).nl();
            b.t(MainLexer.TYPES).begin().nl();
            // TODO Utility.alertOnError_(() -> b.raw(unitManager.save()));
            b.raw(typeManager.save()).nl();
            b.end().t(MainLexer.TYPES).nl();
            b.t(MainLexer.FORMAT).begin().nl();
            for (Pair<ColumnId, DataTypeValue> c : columns)
            {
                b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).unquoted(c.getFirst()).t(FormatLexer.TYPE, FormatLexer.VOCABULARY);
                FXUtility.alertOnError_("Error copying column: " + c.getFirst().getRaw(), () -> c.getSecond().save(b));
                b.nl();
            }
            b.end().t(MainLexer.FORMAT).nl();
            StringBuilder plainText = new StringBuilder();
            FXUtility.alertOnError_("Error copying data values", () -> {
                b.t(MainLexer.VALUES).begin().nl();
                RowRange rowRange = rowRangeSupplier.get();
                for (int i = rowRange.startRowIncl; i <= rowRange.endRowIncl; i++)
                {
                    b.indent();
                    boolean firstValueInRow = true;
                    for (Pair<ColumnId, DataTypeValue> c : columns)
                    {
                        b.data(c.getSecond(), i);
                        if (!firstValueInRow)
                            plainText.append("\t");
                        plainText.append(DataTypeUtility.valueToString(c.getSecond(), c.getSecond().getCollapsed(i), null));
                        firstValueInRow = false;
                    }
                    if (i < rowRange.endRowIncl)
                        plainText.append("\n");
                    b.nl();
                }
            });
            b.end().t(MainLexer.VALUES).nl();
            String str = b.toString();
            Platform.runLater(() -> {
                Map<DataFormat, Object> copyData = new HashMap<>();
                copyData.put(DATA_FORMAT, str);
                copyData.put(DataFormat.PLAIN_TEXT, plainText.toString());
                System.out.println("Copying: {{{\n" + str + "\n}}}");
                Clipboard.getSystemClipboard().setContent(copyData);
            });
        });
    }
}
