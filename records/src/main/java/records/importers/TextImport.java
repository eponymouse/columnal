package records.importers;

import records.data.Column;
import records.data.DataSource;
import records.data.LinkedDataSource;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.TextFileDateColumn;
import records.data.TextFileNumericColumn;
import records.data.TextFileStringColumn;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.error.FetchException;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by neil on 31/10/2016.
 */
public class TextImport
{
    @OnThread(Tag.Simulation)
    public static DataSource importTextFile(TableManager mgr, File textFile) throws IOException, InternalException, UserException
    {
        // Read the first few lines:
        try (BufferedReader br = new BufferedReader(new FileReader(textFile))) {
            String line;
            List<String> initial = new ArrayList<>();
            while ((line = br.readLine()) != null && initial.size() < GuessFormat.INITIAL_ROWS_TEXT_FILE) {
                initial.add(line);
            }
            TextFormat format = GuessFormat.guessTextFormat(mgr.getUnitManager(), initial);

            long startPosition = Utility.skipFirstNRows(textFile, format.headerRows).startFrom;

            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (int i = 0; i < format.columnTypes.size(); i++)
            {
                ColumnInfo columnInfo = format.columnTypes.get(i);
                int iFinal = i;
                if (columnInfo.type.isNumeric())
                    columns.add(rs -> new TextFileNumericColumn(rs, textFile, startPosition, (byte) format.separator, columnInfo.title, iFinal, (NumericColumnType)columnInfo.type));
                else if (columnInfo.type.isText())
                    columns.add(rs -> new TextFileStringColumn(rs, textFile, startPosition, (byte) format.separator, columnInfo.title, iFinal));
                else if (columnInfo.type.isDate())
                    columns.add(rs -> new TextFileDateColumn(rs, textFile, startPosition, (byte) format.separator, columnInfo.title, iFinal, ((CleanDateColumnType)columnInfo.type).getDateTimeInfo()));
                // If it's blank, should we add any column?
                // Maybe if it has title?                }
            }



            RecordSet rs = new RecordSet(textFile.getName(), columns) {
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
                            }
                            catch (IOException e)
                            {
                                throw new FetchException("Error counting rows", e);
                            }
                        }
                        return rowCount;
                    }
                };
            return new LinkedDataSource(mgr, rs, MainLexer.TEXTFILE, textFile);
        }
    }
}
