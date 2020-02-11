package records.importers;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.DataSource;
import records.data.DataSource.LoadedFormat;
import records.data.EditableColumn;
import records.data.RecordSet;
import records.data.SaveTag;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import records.grammar.DataParser;
import records.grammar.FormatLexer;
import records.grammar.MainLexer;
import records.grammar.MainLexer2;
import records.grammar.MainParser;
import records.grammar.MainParser.IsolatedValuesContext;
import records.grammar.MainParser2;
import records.grammar.MainParser2.ContentContext;
import records.grammar.Versions.OverallVersion;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;
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
import java.util.concurrent.CompletableFuture;

@OnThread(Tag.FXPlatform)
public class ClipboardUtils
{

    public static final DataFormat DATA_FORMAT = FXUtility.getDataFormat("application/records");

    public static class LoadedColumnInfo
    {
        public final @Nullable ColumnId columnName; // May not be known
        public final DataType dataType;
        public final ImmutableList<Either<String, @Value Object>> dataValues;

        public LoadedColumnInfo(@Nullable ColumnId columnName, DataType dataType, ImmutableList<Either<String, @Value Object>> dataValues)
        {
            this.columnName = columnName;
            this.dataType = dataType;
            this.dataValues = dataValues;
        }

        public SimulationFunction<RecordSet, EditableColumn> load(Integer i)
        {
            return rs -> {
                return dataType.makeImmediateColumn(columnName != null ? columnName : new ColumnId(IdentifierUtility.identNum("Column", i)), dataValues, DataTypeUtility.makeDefaultValue(dataType)).apply(rs);
            };
        }
    }
    
    // Returns column-major, i.e. a list of columns
    @OnThread(Tag.FXPlatform)
    public static Optional<ImmutableList<LoadedColumnInfo>> loadValuesFromClipboard(TypeManager typeManager)
    {
        @Nullable Object content = Clipboard.getSystemClipboard().getContent(DATA_FORMAT);

        if (content == null)
            return Optional.empty();
        else
        {
            try
            {
                return Optional.of(Utility.<ImmutableList<LoadedColumnInfo>, MainParser2>parseAsOne(content.toString(), MainLexer2::new, MainParser2::new, p -> loadIsolatedValues(p, typeManager)));
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

    private static ImmutableList<LoadedColumnInfo> loadIsolatedValues(MainParser2 main, TypeManager typeManager) throws InternalException, UserException
    {
        Map<String, List<String>> sections = new HashMap<>();
        for (ContentContext contentContext : main.file().content())
        {
            sections.put(contentContext.ATOM(0).getText(), Utility.getDetailLines(contentContext.detail()));
        }
        
        // TODO check that units, types match
        List<String> formatLines = sections.get("FORMAT");
        if (formatLines == null)
            throw new UserException("Missing FORMAT on clipboard (not copied from here?)");
        List<LoadedFormat> format = DataSource.loadFormat(typeManager, formatLines, false);
        List<Pair<LoadedFormat, ImmutableList.Builder<Either<String, @Value Object>>>> cols = new ArrayList<>();
        for (int i = 0; i < format.size(); i++)
        {
            cols.add(new Pair<>(format.get(i), ImmutableList.<Either<String, @Value Object>>builder()));
        }
        List<String> valueLines = sections.get("VALUES");
        if (valueLines == null)
            throw new UserException("Missing VALUES on clipboard (not copied from here?)");
        Utility.loadData(valueLines, p -> {
            for (int i = 0; i < format.size(); i++)
            {
                if (i > 0)
                    p.comma();
                LoadedFormat colFormat = format.get(i);
                cols.get(i).getSecond().add(DataType.loadSingleItem(colFormat.dataType, p, false));
            }
        });
        return cols.stream().map(p -> new LoadedColumnInfo(p.getFirst().columnId, p.getFirst().dataType, p.getSecond().build())).collect(ImmutableList.<LoadedColumnInfo>toImmutableList());
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
    public static void copyValuesToClipboard(UnitManager unitManager, TypeManager typeManager, List<Pair<ColumnId, DataTypeValue>> columns, SimulationSupplier<RowRange> rowRangeSupplier, @Nullable CompletableFuture<Boolean> onCompletion)
    {
        if (columns.isEmpty())
        {
            if (onCompletion != null)
                onCompletion.complete(false);
            return;
        }

        Workers.onWorkerThread("Copying to clipboard", Priority.FETCH, () -> {
            OutputBuilder b = new OutputBuilder();
            b.raw("COLUMNAL").nl();
            b.raw("VERSION " + OverallVersion.latest().asNumber()).nl();
            b.t(MainLexer.UNITS).begin().raw("UU").nl();
            b.pushPrefix(new SaveTag("UU"));
            for (String unitLine : unitManager.save(DataTypeUtility.featuresUnit(Utility.mapList(columns, p -> p.getSecond().getType()))))
            {
                b.raw(unitLine).nl();
            }
            b.pop();
            b.end().raw("UU").t(MainLexer.UNITS).nl();
            b.t(MainLexer.TYPES).begin().raw("TT").nl();
            b.pushPrefix(new SaveTag("TT"));
            for (String typeLine : typeManager.save(DataTypeUtility.featuresTaggedType(Utility.mapList(columns, p -> p.getSecond().getType()))))
            {
                b.raw(typeLine).nl();
            }
            b.pop();
            b.end().raw("TT").t(MainLexer.TYPES).nl();
            b.t(MainLexer.FORMAT).begin().raw("FF").nl();
            b.pushPrefix(new SaveTag("FF"));
            for (Pair<ColumnId, DataTypeValue> c : columns)
            {
                b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).unquoted(c.getFirst()).t(FormatLexer.TYPE, FormatLexer.VOCABULARY);
                FXUtility.alertOnError_("Error copying column: " + c.getFirst().getRaw(), () -> c.getSecond().getType().save(b));
                b.nl();
            }
            b.pop();
            b.end().raw("FF").t(MainLexer.FORMAT).nl();
            StringBuilder plainText = new StringBuilder();
            FXUtility.alertOnError_("Error copying data values", () -> {
                b.t(MainLexer.VALUES).begin().raw("VV").nl();
                b.pushPrefix(new SaveTag("VV"));
                RowRange rowRange = rowRangeSupplier.get();
                for (int i = rowRange.startRowIncl; i <= rowRange.endRowIncl; i++)
                {
                    b.indent();
                    boolean firstValueInRow = true;
                    for (Pair<ColumnId, DataTypeValue> c : columns)
                    {
                        if (!firstValueInRow)
                            b.raw(",");
                        b.data(c.getSecond(), i);
                        if (!firstValueInRow)
                            plainText.append(", ");
                        try
                        {
                            plainText.append(DataTypeUtility.valueToString(c.getSecond().getCollapsed(i)));
                        }
                        catch (InvalidImmediateValueException e)
                        {
                            plainText.append(OutputBuilder.token(DataParser.VOCABULARY, DataParser.INVALID) + OutputBuilder.quoted(e.getInvalid()));
                        }
                        firstValueInRow = false;
                    }
                    if (i < rowRange.endRowIncl)
                        plainText.append("\n");
                    b.nl();
                }
                b.pop();
                b.end().raw("VV").t(MainLexer.VALUES).nl();
                String str = b.toString();
                Platform.runLater(() -> {
                    Map<DataFormat, Object> copyData = new HashMap<>();
                    copyData.put(DATA_FORMAT, str);
                    copyData.put(DataFormat.PLAIN_TEXT, plainText.toString());
                    //System.out.println("Copying: {{{\n" + str + "\n}}}");
                    Clipboard.getSystemClipboard().setContent(copyData);
                    if (onCompletion != null)
                        onCompletion.complete(true);
                });
            });
            
        });
    }
}
