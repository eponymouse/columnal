package records.importers;

import org.apache.commons.lang.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.ColumnId;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.TextColumnType;
import records.data.unit.UnitManager;
import utility.Utility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Created by neil on 20/10/2016.
 */
public class GuessFormat
{
    public static final int MAX_HEADER_ROWS = 20;
    public static final int INITIAL_ROWS_TEXT_FILE = 100;

    public static Format guessGeneralFormat(UnitManager mgr, List<List<String>> vals)
    {
        try
        {
            // All-text formats, indexed by number of header rows:
            final TreeMap<Integer, Format> allText = new TreeMap<>();
            // Guesses header rows:
            for (int headerRows = 0; headerRows < Math.min(MAX_HEADER_ROWS, vals.size() - 1); headerRows++)
            {
                try
                {
                    Format format = guessBodyFormat(mgr, vals.get(headerRows).size(), headerRows, vals);
                    // If they are all text record this as feasible but keep going in case we get better
                    // result with more header rows:
                    if (format.columnTypes.stream().allMatch(c -> c.type.isText() || c.type.isBlank()))
                        allText.put(headerRows, format);
                    else // Not all just text; go with it:
                        return format;
                }
                catch (GuessException e)
                {
                    // Ignore and skip more header rows
                }
            }
            throw new GuessException("Problem figuring out header rows, or data empty");
        }
        catch (GuessException e)
        {
            // Always valid backup: a single text column, no header
            TextFormat fmt = new TextFormat(new Format(0, Collections.singletonList(new ColumnInfo(new TextColumnType(), new ColumnId("Content")))), (char) -1);
            String msg = e.getLocalizedMessage();
            fmt.recordProblem(msg == null ? "Unknown" : msg);
            return fmt;
        }
    }

    public static class GuessException extends Exception
    {
        public GuessException(String message)
        {
            super(message);
        }
    }

    public static TextFormat guessTextFormat(UnitManager mgr, List<String> initial)
    {
        try
        {
            // All-text formats, indexed by number of header rows:
            final TreeMap<Integer, TextFormat> allText = new TreeMap<>();
            // We advance the number of header rows until everything makes sense:
            for (int headerRows = 0; headerRows < Math.min(MAX_HEADER_ROWS, initial.size() - 1); headerRows++)
            {

                Map<String, Double> sepScores = new HashMap<>();
                // Guess the separator:
                // Earlier in this list is most preferred:
                List<String> seps = Arrays.asList(";", ",", "\t", ":", " ");
                for (String sep : seps)
                {
                    List<Integer> counts = new ArrayList<>(initial.size() - headerRows);
                    for (int i = headerRows; i < initial.size(); i++)
                        counts.add(Utility.countIn(sep, initial.get(i)));
                    if (counts.stream().allMatch(c -> c.intValue() == 0))
                    {
                        // None found; so rubbish we shouldn't record
                    } else
                    {
                        sepScores.put(sep, Utility.variance(counts));
                    }
                }

                if (sepScores.isEmpty())
                    continue;

                Entry<String, Double> sep = sepScores.entrySet().stream().min(Comparator.<Entry<String, Double>, Double>comparing(e -> e.getValue()).thenComparing(e -> seps.indexOf(e.getKey()))).get();

                if (sep.getValue().doubleValue() == 0.0)
                {
                    // Spot on!  Read first line after headers to get column count
                    int columnCount = initial.get(headerRows).split(sep.getKey(), -1).length;

                    List<@NonNull List<@NonNull String>> initialVals = Utility.<@NonNull String, @NonNull List<@NonNull String>>mapList(initial, s -> Arrays.asList(s.split(sep.getKey(), -1)));

                    //List<Function<RecordSet, Column>> columns = new ArrayList<>();
                    Format format = guessBodyFormat(mgr, columnCount, headerRows, initialVals);
                    TextFormat textFormat = new TextFormat(format, sep.getKey().charAt(0));
                    // If they are all text record this as feasible but keep going in case we get better
                    // result with more header rows:
                    if (format.columnTypes.stream().allMatch(c -> c.type.isText() || c.type.isBlank()))
                        allText.put(headerRows, textFormat);
                    else // Not all just text; go with it:
                        return textFormat;

                }
            }
            Entry<Integer, TextFormat> firstAllText = allText.firstEntry();
            if (firstAllText != null)
                return firstAllText.getValue();
            else
                throw new GuessException("Couldn't guess column separator");
        }
        catch (GuessException e)
        {
            // Always valid backup: a single text column, no header
            TextFormat fmt = new TextFormat(new Format(0, Collections.singletonList(new ColumnInfo(new TextColumnType(), new ColumnId("Content")))), (char)-1);
            String msg = e.getLocalizedMessage();
            fmt.recordProblem(msg == null ? "Unknown" : msg);
            return fmt;
        }
    }

    private static Format guessBodyFormat(UnitManager mgr, int columnCount, int headerRows, @NonNull List<@NonNull List<@NonNull String>> initialVals) throws GuessException
    {
        // Per row, for how many columns is it viable to get column name?
        Map<Integer, Integer> viableColumnNameRows = new HashMap<>();
        List<ColumnType> columnTypes = new ArrayList<>();
        List<Integer> blankRows = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++)
        {
            // Have a guess at column columntype:
            boolean allNumeric = true;
            // Only false if we find content which is not parseable as a number:
            boolean allNumericOrBlank = true;
            boolean allBlank = true;
            List<DateFormat> possibleDateFormats = new ArrayList<>(Utility.mapList(CleanDateColumnType.DATE_FORMATS, (formatString) -> new DateFormat(formatString, LocalDate::from)));
            String commonPrefix = "";
            List<Integer> decimalPlaces = new ArrayList<>();
            for (int rowIndex = headerRows; rowIndex < initialVals.size(); rowIndex++)
            {
                List<String> row = initialVals.get(rowIndex);
                if (row.isEmpty() || row.stream().allMatch(String::isEmpty))
                {
                    // Whole row is blank
                    // Only add it once, not once per column:
                    if (columnIndex == 0)
                        blankRows.add(rowIndex - headerRows);
                }
                else
                {
                    String val = row.get(columnIndex).trim();
                    if (!val.isEmpty())
                    {
                        allBlank = false;

                        if (commonPrefix.isEmpty())
                        {
                            // Look for a prefix of currency symbol:
                            for (int i = 0; i < val.length(); i = val.offsetByCodePoints(i, 1))
                            {
                                if (Character.getType(val.codePointAt(i)) == Character.CURRENCY_SYMBOL)
                                    commonPrefix += val.substring(i, val.offsetByCodePoints(i, 1));
                                else
                                    break;
                            }
                        }
                        int first;
                        // Not an else; if we just picked commonPrefix, we should find it here:
                        if (!commonPrefix.isEmpty() && val.startsWith(commonPrefix))
                        {
                            // Take off prefix and continue as is:
                            val = val.substring(commonPrefix.length()).trim();
                        }
                        else if (!commonPrefix.isEmpty() && !Character.isDigit(first = val.codePointAt(0)) && first != '+' && first != '-')
                        {
                            // We thought we had a prefix, but we haven't found it here, so give up:
                            commonPrefix = "";
                            allNumeric = false;
                            allNumericOrBlank = false;
                            break;
                        }
                        try
                        {
                            // TODO: support . as thousands separator and comma as decimal point
                            BigDecimal bd = new BigDecimal(val.replace(",", ""));
                            int dot = val.indexOf(".");
                            if (dot == -1)
                                decimalPlaces.add(0);
                            else
                                decimalPlaces.add(val.length() - (dot + 1));

                        }
                        catch (NumberFormatException e)
                        {
                            allNumeric = false;
                            allNumericOrBlank = false;
                            commonPrefix = "";
                        }
                        // Minimum length for date is 6 by my count
                        if (val.length() < 6)
                            possibleDateFormats.clear();
                        else
                        {
                            // Seems expensive but most will be knocked out immediately:
                            for (Iterator<DateFormat> dateFormatIt = possibleDateFormats.iterator(); dateFormatIt.hasNext(); )
                            {
                                try
                                {

                                    dateFormatIt.next().formatter.parse(val, LocalDate::from);
                                } catch (DateTimeParseException e)
                                {
                                    dateFormatIt.remove();
                                }
                            }
                        }
                    }
                    else
                    {
                        // Found a blank:
                        allNumeric = false;
                    }
                }
            }
            int minDP = decimalPlaces.stream().mapToInt(i -> i).min().orElse(0);

            if (allBlank)
                columnTypes.add(ColumnType.BLANK);
            else if (!possibleDateFormats.isEmpty())
                columnTypes.add(new CleanDateColumnType(possibleDateFormats.get(0).formatString, possibleDateFormats.get(0).destQuery));
            else if (allNumeric)
            {
                columnTypes.add(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix));
            }
            // TODO
            //else if (allNumericOrBlank)
                //columnTypes.add(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, true));
            else
                columnTypes.add(new TextColumnType());
            // Go backwards to find column titles:

            for (int headerRow = headerRows - 1; headerRow >= 0; headerRow--)
            {
                // Must actually have our column in it:
                if (columnIndex < initialVals.get(headerRow).size() && !initialVals.get(headerRow).get(columnIndex).isEmpty())
                {
                    viableColumnNameRows.compute(headerRow, (a, pre) -> pre == null ? 1 : (1 + pre));
                }
            }
        }
        int nonBlankColumnCount = (int)columnTypes.stream().filter(c -> !c.isBlank()).count();
        // All must think it's viable, and then pick last one:
        Optional<List<String>> headerRow = viableColumnNameRows.entrySet().stream().filter(e -> e.getValue() == nonBlankColumnCount || e.getValue() == columnTypes.size()).max(Entry.comparingByKey()).map(e -> initialVals.get(e.getKey()));

        List<ColumnInfo> columns = new ArrayList<>(columnCount);
        for (int columnIndex = 0; columnIndex < columnTypes.size(); columnIndex++)
            columns.add(new ColumnInfo(columnTypes.get(columnIndex), new ColumnId(headerRow.isPresent() ? headerRow.get().get(columnIndex) : ("C" + (columnIndex + 1)))));
        return new Format(headerRows, columns);
    }
}
