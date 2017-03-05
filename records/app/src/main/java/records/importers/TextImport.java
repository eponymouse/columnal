package records.importers;

import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;
import records.data.Column;
import records.data.DataSource;
import records.data.LinkedDataSource;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.TextFileDateColumn;
import records.data.TextFileNumericColumn;
import records.data.TextFileStringColumn;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataType.NumberInfo;
import records.error.FetchException;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class TextImport
{
    @OnThread(Tag.Simulation)
    public static void importTextFile(TableManager mgr, File textFile, FXPlatformConsumer<DataSource> then) throws IOException, InternalException, UserException
    {
        List<String> initial = getInitial(textFile);
        GuessFormat.guessTextFormatGUI_Then(mgr.getUnitManager(), initial, format ->
        {
            try
            {
                LinkedDataSource ds = makeDataSource(mgr, textFile, format);
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
    public static ChoicePoint<DataSource> _test_importTextFile(TableManager mgr, File textFile) throws IOException, InternalException, UserException
    {
        List<String> initial = getInitial(textFile);
        return GuessFormat.guessTextFormat(mgr.getUnitManager(), initial).then(format -> {
            try
            {
                return makeDataSource(mgr, textFile, format);
            }
            catch (IOException e)
            {
                throw new UserException("IO exception", e);
            }
        });
    }

    @OnThread(Tag.Simulation)
    private static LinkedDataSource makeDataSource(TableManager mgr, final File textFile, final TextFormat format) throws IOException, InternalException, UserException
    {
        long startPosition = Utility.skipFirstNRows(textFile, format.headerRows).startFrom;

        List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
        for (int i = 0; i < format.columnTypes.size(); i++)
        {
            ColumnInfo columnInfo = format.columnTypes.get(i);
            int iFinal = i;
            if (columnInfo.type instanceof NumericColumnType)
            {
                columns.add(rs ->
                {
                    NumericColumnType numericColumnType = (NumericColumnType) columnInfo.type;
                    return new TextFileNumericColumn(rs, textFile, startPosition, (byte) format.separator, columnInfo.title, iFinal, new NumberInfo(numericColumnType.unit, numericColumnType.minDP), numericColumnType::removePrefix);
                });
            } else if (columnInfo.type instanceof TextColumnType)
                columns.add(rs -> new TextFileStringColumn(rs, textFile, startPosition, (byte) format.separator, columnInfo.title, iFinal));
            else if (columnInfo.type instanceof CleanDateColumnType)
            {
                columns.add(rs ->
                {
                    CleanDateColumnType dateColumnType = (CleanDateColumnType) columnInfo.type;
                    return new TextFileDateColumn(rs, textFile, startPosition, (byte) format.separator, columnInfo.title, iFinal, dateColumnType.getDateTimeInfo(), dateColumnType.getDateTimeFormatter(), dateColumnType.getQuery());
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


        RecordSet rs = new RecordSet(textFile.getName(), columns)
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
                        rowCount = Utility.countLines(textFile) - format.headerRows;
                    } catch (IOException e)
                    {
                        throw new FetchException("Error counting rows", e);
                    }
                }
                return rowCount;
            }
        };
        return new LinkedDataSource(mgr, rs, MainLexer.TEXTFILE, textFile);
    }

    @NotNull
    private static List<String> getInitial(File textFile) throws IOException
    {
        List<String> initial = new ArrayList<>();
        // Read the first few lines:
        try (BufferedReader br = new BufferedReader(new FileReader(textFile)))
        {
            String line;
            while ((line = br.readLine()) != null && initial.size() < GuessFormat.INITIAL_ROWS_TEXT_FILE)
            {
                initial.add(line);
            }
        }
        return initial;
    }
}
