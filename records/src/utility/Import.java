package utility;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.Column;
import records.data.RecordSet;
import records.data.TextFileNumericColumn;
import records.data.TextFileStringColumn;
import records.error.FetchException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Created by neil on 20/10/2016.
 */
public class Import
{
    @OnThread(Tag.Simulation)
    public static RecordSet importFile(File textFile) throws IOException
    {
        // Read the first few lines:
        try (BufferedReader br = new BufferedReader(new FileReader(textFile))) {
            String line;
            List<String> initial = new ArrayList<>();
            while ((line = br.readLine()) != null && initial.size() < 100) {
                initial.add(line);
            }
            Map<String, Double> sepScores = new HashMap<>();
            // Guess the separator:
            for (String sep : Arrays.asList(";", ",", "\t"))
            {
                // Ignore first row; often a header:
                
                List<Integer> counts = new ArrayList<>(initial.size() - 1);
                for (int i = 1; i < initial.size(); i++)
                    counts.add(Utility.countIn(sep, initial.get(i)));
                if (counts.stream().allMatch(c -> c.intValue() == 0))
                {
                    // None found; so rubbish we shouldn't record
                }
                else
                {
                    sepScores.put(sep, Utility.variance(counts));
                }
            }

            if (sepScores.isEmpty())
                throw new IOException("Couldn't deduce separator"); // TODO: ask!
            Entry<String, Double> sep = sepScores.entrySet().stream().min(Entry.comparingByValue()).get();

            int headerRows = 1;

            if (sep.getValue().doubleValue() == 0.0)
            {
                // Spot on!  Read first line of initial to get column count
                int columnCount = initial.get(0).split(sep.getKey()).length;

                List<@NonNull String @NonNull[]> initialVals = Utility.<@NonNull String, @NonNull String @NonNull []>mapList(initial, s -> s.split(sep.getKey()));

                List<Function<RecordSet, Column>> columns = new ArrayList<>();
                for (int i = 0; i < columnCount; i++)
                {
                    // Have a guess at column type:
                    boolean allNumeric = true;
                    for (int j = 1; j < initialVals.size(); j++)
                    {
                        try
                        {
                            new BigDecimal(initialVals.get(j)[i]);
                        }
                        catch (NumberFormatException e)
                        {
                            allNumeric = false;
                        }
                    }
                    boolean allNumericFinal = allNumeric;
                    String colName = initialVals.get(0)[i];
                    int colIndex = i;
                    long startPosition = Utility.skipFirstNRows(textFile, headerRows).startFrom;
                    columns.add(rs -> allNumericFinal ?
                        new TextFileNumericColumn(rs, textFile, startPosition, (byte) sep.getKey().charAt(0), colName, colIndex)
                        : new TextFileStringColumn(rs, textFile, startPosition, (byte) sep.getKey().charAt(0), colName, colIndex));
                }

                return new RecordSet(textFile.getName(), columns) {
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
                                rowCount = Utility.countLines(textFile) - headerRows;
                            }
                            catch (IOException e)
                            {
                                throw new FetchException("Error counting rows", e);
                            }
                        }
                        return rowCount;
                    }
                };
            }
            else
                throw new IOException("Uncertain of number of columns");
        }
    }
}
