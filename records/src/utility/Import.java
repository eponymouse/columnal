package utility;

import records.data.Column;
import records.data.RecordSet;
import records.data.TextFileColumn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Created by neil on 20/10/2016.
 */
public class Import
{
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
                List<Integer> counts = initial.stream().skip(1).map(l -> Utility.countIn(sep, l)).collect(Collectors.toList());
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

            if (sep.getValue().doubleValue() == 0.0)
            {
                // Spot on!  Read first line of initial to get column count
                int columnCount = initial.get(0).split(sep.getKey()).length;

                // TODO work out type of field
                List<Column<String>> columns = new ArrayList<>();
                for (int i = 0; i < columnCount; i++)
                    columns.add(new TextFileColumn(textFile, sep.getKey(), i));

                return new RecordSet(columns, initial.size() - 1);
            }
            else
                throw new IOException("Uncertain of number of columns");
        }
    }
}
