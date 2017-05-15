package records.importers;

import com.google.common.io.Files;
import javafx.application.Platform;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.Column;
import records.data.DataSource;
import records.data.ImmediateDataSource;
import records.data.LinkedDataSource;
import records.data.RecordSet;
import records.data.TableId;
import records.data.TableManager;
import records.data.TextFileColumn;
import records.data.TextFileColumn.TextFileColumnListener;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.error.FetchException;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.importers.GuessFormat.ImportInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ReadState;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by neil on 31/10/2016.
 */
public class TextImport
{
    @OnThread(Tag.Simulation)
    public static void importTextFile(TableManager mgr, File textFile, FXPlatformConsumer<DataSource> then) throws IOException, InternalException, UserException
    {
        Map<Charset, List<String>> initial = getInitial(textFile);
        GuessFormat.guessTextFormatGUI_Then(mgr, textFile, Files.getNameWithoutExtension(textFile.getName()), initial, format ->
        {
            try
            {
                DataSource ds = makeDataSource(mgr, textFile, format.getFirst(), format.getSecond());
                Platform.runLater(() -> then.consume(ds));
            }
            catch (InternalException | UserException | IOException e)
            {
                // TODO display
                Utility.log(e);
            }
        });
    }

    @OnThread(Tag.Simulation)
    public static ChoicePoint<?, DataSource> _test_importTextFile(TableManager mgr, File textFile, boolean link) throws IOException, InternalException, UserException
    {
        Map<Charset, List<String>> initial = getInitial(textFile);
        return GuessFormat.guessTextFormat(mgr.getUnitManager(), initial).then(format -> {
            try
            {
                return makeDataSource(mgr, textFile, new ImportInfo(new TableId(textFile.getName()), link), format);
            }
            catch (IOException e)
            {
                throw new UserException("IO exception", e);
            }
        });
    }

    @OnThread(Tag.Simulation)
    private static DataSource makeDataSource(TableManager mgr, final File textFile, final ImportInfo importInfo, final TextFormat format) throws IOException, InternalException, UserException
    {
        RecordSet rs = makeRecordSet(mgr.getTypeManager(), textFile, format, null);
        if (importInfo.linkFile)
            return new LinkedDataSource(mgr, importInfo.tableName, rs, MainLexer.TEXTFILE, textFile);
        else
            return new ImmediateDataSource(mgr, importInfo.tableName, rs);
    }

    @OnThread(Tag.Simulation)
    public static RecordSet makeRecordSet(TypeManager typeManager, File textFile, TextFormat format, @Nullable TextFileColumnListener listener) throws IOException, InternalException, UserException
    {
        List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
        int totalColumns = format.columnTypes.size();
        for (int i = 0; i < totalColumns; i++)
        {
            // Must be one per column:
            ReadState reader = Utility.skipFirstNRows(textFile, format.charset, format.headerRows);

            ColumnInfo columnInfo = format.columnTypes.get(i);
            int iFinal = i;
            if (columnInfo.type instanceof NumericColumnType)
            {
                columns.add(rs ->
                {
                    NumericColumnType numericColumnType = (NumericColumnType) columnInfo.type;
                    return TextFileColumn.numericColumn(rs, reader, format.separator, columnInfo.title, iFinal, totalColumns, listener, new NumberInfo(numericColumnType.unit, numericColumnType.displayInfo), numericColumnType::removePrefix);
                });
            }
            else if (columnInfo.type instanceof OrBlankColumnType)
            {
                OrBlankColumnType orBlankColumnType = (OrBlankColumnType)columnInfo.type;
                if (orBlankColumnType.getInner() instanceof NumericColumnType)
                {
                    NumericColumnType numericColumnType = (NumericColumnType) orBlankColumnType.getInner();
                    DataType numberOrBlank = typeManager.registerTaggedType("Number?", Arrays.asList(
                        new TagType<>("Number", DataType.number(new NumberInfo(numericColumnType.unit, numericColumnType.displayInfo))),
                        new TagType<>("Blank", null)
                    ));
                    columns.add(rs -> {
                        return TextFileColumn.taggedColumn(rs, reader, format.separator, columnInfo.title, iFinal, totalColumns, listener, numberOrBlank.getTaggedTypeName(), numberOrBlank.getTagTypes(), str -> {
                            if (str.equals(orBlankColumnType.getBlankString()))
                            {
                                return new TaggedValue(1, null);
                            }
                            else
                            {
                                return new TaggedValue(0, DataTypeUtility.value(Utility.parseNumber(str)));
                            }
                        });
                    });
                }
                else
                    throw new InternalException("Unhandled or-blank column type: " + orBlankColumnType.getInner().getClass());
            }
            else if (columnInfo.type instanceof TextColumnType)
            {
                columns.add(rs -> TextFileColumn.stringColumn(rs, reader, format.separator, columnInfo.title, iFinal, totalColumns, listener));
            }
            else if (columnInfo.type instanceof CleanDateColumnType)
            {
                columns.add(rs ->
                {
                    CleanDateColumnType dateColumnType = (CleanDateColumnType) columnInfo.type;
                    return TextFileColumn.dateColumn(rs, reader, format.separator, columnInfo.title, iFinal, totalColumns, listener, dateColumnType.getDateTimeInfo(), dateColumnType.getDateTimeFormatter(), dateColumnType.getQuery());
                });
            }
            else if (columnInfo.type instanceof BlankColumnType)
            {
                // If it's blank, should we add any column?
                // Maybe if it has title?                }
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
            public int getLength() throws UserException
            {
                if (rowCount == -1)
                {
                    try
                    {
                        rowCount = Utility.countLines(textFile, format.charset) - format.headerRows;
                    } catch (IOException e)
                    {
                        throw new FetchException("Error counting rows", e);
                    }
                }
                return rowCount;
            }
        };
    }

    @NotNull
    private static Map<Charset, List<String>> getInitial(File textFile) throws IOException
    {
        Map<Charset, List<String>> initial = new LinkedHashMap<>();
        Set<Charset> charsets = new LinkedHashSet<>();
        charsets.addAll(Arrays.asList(Charset.forName("UTF-8"), Charset.forName("ISO-8859-1"), Charset.forName("UTF-16")));
        charsets.add(Charset.defaultCharset());
        for (Charset charset : charsets)
        {
            ArrayList<String> initialLines = new ArrayList<>();
            // Read the first few lines:
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), charset)))
            {
                String line;
                while ((line = br.readLine()) != null && initialLines.size() < GuessFormat.INITIAL_ROWS_TEXT_FILE)
                {
                    initialLines.add(line);
                }
            }
            initial.put(charset, initialLines);
        }
        return initial;
    }
}
