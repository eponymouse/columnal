package records.importers;

import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import javafx.application.Platform;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.*;
import records.data.Table.InitialLoadDetails;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.FetchException;
import records.error.InternalException;
import records.error.UserException;
import records.importers.GuessFormat.FinalTextFormat;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ReadState;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.TranslationUtility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Created by neil on 31/10/2016.
 */
public class TextImporter implements Importer
{
    @OnThread(Tag.Any)
    @Override
    public ImmutableList<Pair<@Localized String, ImmutableList<String>>> getSupportedFileTypes()
    {
        return ImmutableList.of(new Pair<@Localized String, ImmutableList<String>>(TranslationUtility.getString("importer.text.files"), ImmutableList.of("*.txt", "*.csv")));
    }

    @Override
    public @OnThread(Tag.FXPlatform) void importFile(Window parent, TableManager tableManager, CellPosition destination, File src, URL origin, FXPlatformConsumer<DataSource> onLoad)
    {
        Workers.onWorkerThread("GuessFormat data", Priority.LOAD_FROM_DISK, () ->
        {
            try
            {
                importTextFile(parent, tableManager, src, destination, rs -> onLoad.consume(rs));
            }
            catch (InternalException | UserException | IOException ex)
            {
                FXUtility.logAndShowError("import.text.error", ex);
            }
        });
    }

    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.files.text");
    }

    @OnThread(Tag.Simulation)
    public static void importTextFile(@Nullable Window parentWindow, TableManager mgr, File textFile, CellPosition destination, FXPlatformConsumer<DataSource> then) throws IOException, InternalException, UserException
    {
        Map<Charset, List<String>> initial = getInitial(textFile);
        GuessFormat.guessTextFormatGUI_Then(parentWindow, mgr, textFile, Files.getNameWithoutExtension(textFile.getName()), initial, impInfo ->
        {
            try
            {
                Log.debug("Importing format " + impInfo.getFormat());
                DataSource ds = makeDataSource(mgr, textFile, impInfo.getInitialLoadDetails(destination), impInfo.getFormat());
                Platform.runLater(() -> then.consume(ds));
            }
            catch (InternalException | UserException | IOException e)
            {
                // TODO display
                Log.log(e);
            }
        });
    }

    @OnThread(Tag.Simulation)
    public static CompletableFuture<RecordSet> _test_importTextFile(TableManager mgr, File textFile) throws IOException, InternalException, UserException, InterruptedException, ExecutionException, TimeoutException
    {
        Map<Charset, List<String>> initial = getInitial(textFile);
        CompletableFuture<RecordSet> f = new CompletableFuture<>();
        // TODO need some test code to operate the GUI
        importTextFile(null, mgr, textFile, CellPosition.ORIGIN, data -> {
            try
            {
                f.complete(data.getData());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
        return f;
    }

    @OnThread(Tag.Simulation)
    private static DataSource makeDataSource(TableManager mgr, final File textFile, final InitialLoadDetails initialLoadDetails, final FinalTextFormat format) throws IOException, InternalException, UserException
    {
        RecordSet rs = makeRecordSet(mgr.getTypeManager(), textFile, format);
        //if (importInfo.linkFile)
            //return new LinkedDataSource(mgr, importInfo.tableName, rs, MainLexer.TEXTFILE, textFile);
        //else
            return new ImmediateDataSource(mgr, initialLoadDetails, new EditableRecordSet(rs));
    }

    @OnThread(Tag.Simulation)
    public static RecordSet makeSrcRecordSet(File textFile, Charset charset, @Nullable String separator, @Nullable String quote, int totalColumns) throws IOException, InternalException, UserException
    {
        List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
        for (int i = 0; i < totalColumns; i++)
        {
            // Must be one per column:
            ReadState reader = Utility.skipFirstNRows(textFile, charset, 0);
            int iFinal = i;
            columns.add(rs -> {
                ColumnId columnName = new ColumnId(IdentifierUtility.identNum("Column", (iFinal + 1)));
                return TextFileColumn.stringColumn(rs, reader, separator, quote, columnName, iFinal, totalColumns);
            });
        }

        return new KnownLengthRecordSet(columns, Utility.countLines(textFile, charset));
    }

    @OnThread(Tag.Simulation)
    public static RecordSet makeRecordSet(TypeManager typeManager, File textFile, FinalTextFormat format) throws IOException, InternalException, UserException
    {
        List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
        int totalColumns = format.columnTypes.size();
        for (int i = 0; i < totalColumns; i++)
        {
            // Must be one per column:
            ReadState reader = Utility.skipFirstNRows(textFile, format.initialTextFormat.charset, format.trimChoice.trimFromTop);

            ColumnInfo columnInfo = format.columnTypes.get(i);
            int columnIndexInSrc = i + format.trimChoice.trimFromLeft;
            if (columnInfo.type instanceof NumericColumnType)
            {
                columns.add(rs ->
                {
                    NumericColumnType numericColumnType = (NumericColumnType) columnInfo.type;
                    return TextFileColumn.numericColumn(rs, reader, format.initialTextFormat.separator, format.initialTextFormat.quote, columnInfo.title, columnIndexInSrc, totalColumns, new NumberInfo(numericColumnType.unit), numericColumnType::removePrefixAndSuffix);
                });
            }
            else if (columnInfo.type instanceof OrBlankColumnType)
            {
                OrBlankColumnType orBlankColumnType = (OrBlankColumnType)columnInfo.type;
                if (orBlankColumnType.getInner() instanceof NumericColumnType)
                {
                    NumericColumnType numericColumnType = (NumericColumnType) orBlankColumnType.getInner();
                    DataType numberType = DataType.number(new NumberInfo(numericColumnType.unit));
                    DataType numberOrBlank = typeManager.getMaybeType().instantiate(ImmutableList.of(Either.<Unit, DataType>right(numberType)), typeManager);
                    columns.add(rs -> {
                        return TextFileColumn.<DataType>taggedColumn(rs, reader, format.initialTextFormat.separator, format.initialTextFormat.quote, columnInfo.title, columnIndexInSrc, totalColumns, DataTypeUtility.getTaggedTypeName(numberOrBlank), ImmutableList.of(Either.<Unit, DataType>right(numberType)), DataTypeUtility.getTagTypes(numberOrBlank), str -> {
                            if (str.equals(orBlankColumnType.getBlankString()))
                            {
                                return Either.<String, TaggedValue>right(new TaggedValue(0, null));
                            }
                            else
                            {
                                return Utility.parseNumberOpt(str).map(n -> {
                                    return Either.<String, TaggedValue>right(new TaggedValue(1, DataTypeUtility.value(n)));
                                }).orElse(Either.<String, TaggedValue>left(str));
                            }
                        });
                    });
                }
                else
                    throw new InternalException("Unhandled or-blank column type: " + orBlankColumnType.getInner().getClass());
            }
            else if (columnInfo.type instanceof TextColumnType || columnInfo.type instanceof BlankColumnType)
            {
                columns.add(rs -> TextFileColumn.stringColumn(rs, reader, format.initialTextFormat.separator, format.initialTextFormat.quote, columnInfo.title, columnIndexInSrc, totalColumns));
            }
            else if (columnInfo.type instanceof CleanDateColumnType)
            {
                columns.add(rs ->
                {
                    CleanDateColumnType dateColumnType = (CleanDateColumnType) columnInfo.type;
                    return TextFileColumn.dateColumn(rs, reader, format.initialTextFormat.separator, format.initialTextFormat.quote, columnInfo.title, columnIndexInSrc, totalColumns, dateColumnType.getDateTimeInfo(), dateColumnType::parse);
                });
            }
            else
                throw new InternalException("Unhandled column type: " + columnInfo.type.getClass());
        }


        return new RecordSet(columns)
        {
            protected int rowCount = -1;

            @Override
            public final boolean indexValid(int index) throws UserException
            {
                return index < getLength();
            }

            @Override
            @SuppressWarnings("units")
            public @TableDataRowIndex int getLength() throws UserException
            {
                if (rowCount == -1)
                {
                    try
                    {
                        rowCount = Utility.countLines(textFile, format.initialTextFormat.charset) - format.trimChoice.trimFromTop - format.trimChoice.trimFromBottom;
                    } catch (IOException e)
                    {
                        throw new FetchException("Error counting rows", e);
                    }
                }
                return rowCount;
            }
        };
    }

    @NonNull
    private static Map<Charset, List<String>> getInitial(File textFile) throws IOException
    {
        Map<Charset, List<String>> initial = new LinkedHashMap<>();
        Set<Charset> charsets = new LinkedHashSet<>();
        charsets.addAll(Arrays.asList(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16));
        charsets.add(Charset.defaultCharset());
        for (Charset charset : charsets)
        {
            ArrayList<String> initialLines = new ArrayList<>();
            // Read the first few lines:
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), charset)))
            {
                String line;
                while ((line = br.readLine()) != null/* && initialLines.size() < GuessFormat.INITIAL_ROWS_TEXT_FILE*/)
                {
                    initialLines.add(line);
                }
            }
            initial.put(charset, initialLines);
        }
        return initial;
    }
}
