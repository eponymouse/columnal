package records.importers;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.columntype.BlankColumnType;
import records.data.columntype.BoolColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.importers.gui.ImportChoicesDialog;
import records.importers.gui.ImporterGUI;
import records.transformations.function.ToDate;
import records.transformations.function.ToTime;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationConsumer;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.LabelledGrid;
import utility.gui.TranslationUtility;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 20/10/2016.
 */
public class GuessFormat
{
    public static final int INITIAL_ROWS_TEXT_FILE = 100;
    public static final ImmutableList<String> SEP_CHOICES = ImmutableList.of(",", ";", "\t", ":", "|", " ");
    public static final ImmutableList<String> QUOTE_CHOICES = ImmutableList.of("", "\"", "\'");

    // SRC_FORMAT is what you need to get the initial source table
    // DEST_FORMAT is what you calculate post-trim for the column formats for the final item. 
    public static interface Import<SRC_FORMAT, DEST_FORMAT>
    {
        @OnThread(Tag.FXPlatform)
        public default @Nullable Node getGUI()
        {
            return null;
        }
        
        @OnThread(Tag.FXPlatform)
        public ObjectExpression<@Nullable SRC_FORMAT> currentSrcFormat();

        // Get the function which would load the source (import LHS) for the given format.
        // For text files, this will be CSV split into columns, but untrimmed and all columns text
        // For XLS it is original sheet, untrimmed
        SimulationFunction<SRC_FORMAT, Pair<TrimChoice, RecordSet>> loadSource();
        
        // Get the function which would load the final record set (import RHS) for the given format and trim.
        // After trimming, the types of the columns are guessed at.
        SimulationFunction<Pair<SRC_FORMAT, TrimChoice>, Pair<DEST_FORMAT, RecordSet>> loadDest();
        
        @OnThread(Tag.Simulation)
        public default DEST_FORMAT _test_getResultNoGUI() throws UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
        {
            CompletableFuture<SRC_FORMAT> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                SRC_FORMAT srcFormat = currentSrcFormat().get();
                if (srcFormat != null)
                    future.complete(srcFormat);
            });
            SRC_FORMAT srcFormat = future.get(1, TimeUnit.SECONDS);
            Pair<TrimChoice, RecordSet> p = loadSource().apply(srcFormat);
            return loadDest().apply(new Pair<>(srcFormat, p.getFirst())).getFirst();
        }
    }
    
    /**
     *
     * @param mgr
     * @param vals List of rows, where each row is list of values
     * @return
     */
    public static ImmutableList<ColumnInfo> guessGeneralFormat(UnitManager mgr, List<? extends List<String>> vals, TrimChoice trimChoice) throws GuessException
    {
        return guessBodyFormat(mgr, trimChoice, vals);
    }

    public static class GuessException extends UserException
    {
        public GuessException(String message)
        {
            super(message);
        }
    }

    public static <C extends Charset> ChoiceDetails<Charset> charsetChoiceDetails(Collection<C> available)
    {
        // In future, we should allow users to specify
        // (For now, they can just re-save as UTF-8)
        return new ChoiceDetails<>("guess.charset", "guess-format/charset", ImmutableList.copyOf(available), null);
    }

    // public for testing
    public static class TrimChoice
    {
        public final int trimFromTop;
        public final int trimFromLeft;
        public final int trimFromRight;
        public final int trimFromBottom;

        // public for testing
        public TrimChoice(int trimFromTop, int trimFromBottom, int trimFromLeft, int trimFromRight)
        {
            this.trimFromTop = trimFromTop;
            this.trimFromLeft = trimFromLeft;
            this.trimFromRight = trimFromRight;
            this.trimFromBottom = trimFromBottom;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TrimChoice that = (TrimChoice) o;

            if (trimFromTop != that.trimFromTop) return false;
            if (trimFromLeft != that.trimFromLeft) return false;
            if (trimFromRight != that.trimFromRight) return false;
            return trimFromBottom == that.trimFromBottom;
        }

        @Override
        public int hashCode()
        {
            int result = trimFromTop;
            result = 31 * result + trimFromLeft;
            result = 31 * result + trimFromRight;
            result = 31 * result + trimFromBottom;
            return result;
        }

        @SuppressWarnings("i18n")
        @Override
        public @Localized String toString()
        {
            return "<trim, vert +" + trimFromTop + " -" + trimFromBottom + " horiz +" + trimFromLeft + " -" + trimFromRight + ">";
        }

        public List<List<String>> trim(List<? extends List<String>> original)
        {
            ArrayList<List<String>> trimmed = new ArrayList<>();
            for (int i = Math.max(0, trimFromTop); i < original.size() - Math.max(0, trimFromBottom); i++)
            {
                List<String> originalLine = original.get(i);
                int left = Utility.clampIncl(0, trimFromLeft, originalLine.size());
                int right = Utility.clampIncl(left, originalLine.size() - trimFromRight, originalLine.size());
                trimmed.add(originalLine.subList(left, right));
            }
            return trimmed;
        }
    }
/*
        @Override
        public @Localized String toString()
        {
            if (separator == null)
                return TranslationUtility.getString("importer.sep.none");
            if (separator.equals(" "))
                return TranslationUtility.getString("importer.sep.space");
            if (separator.equals("\t"))
                return TranslationUtility.getString("importer.sep.tab");
            else
                return Utility.universal(separator);
        }
*/
    public static ChoiceDetails<String> separatorChoiceDetails()
    {
        return new ChoiceDetails<>("guess.separator", "guess-format/separator", SEP_CHOICES, enterSingleChar(x -> x));
    }

        /*
        private final @Nullable String quote;
        private final @Nullable String escapedQuote;

        public QuoteChoice(@Nullable String quote)
        {
            this.quote = quote;
            // Only option at the moment is doubled quote:
            this.escapedQuote = quote == null ? null : (quote + quote);
        }
        */

/*
        @Override
        public @Localized String toString()
        {
            return quote == null ? TranslationUtility.getString("importer.quote.none") : Utility.universal(quote);
        }
*/
    public static ChoiceDetails<String> quoteChoiceDetails()
    {
        return new ChoiceDetails<>("guess.quote", "guess-format/quote", QUOTE_CHOICES, enterSingleChar(x -> x));
    }
    
    public static class InitialTextFormat
    {
        public final Charset charset;
        // empty means no separator; just one big column
        public final String separator;
        // empty means no quote
        public final String quote;

        public InitialTextFormat(Charset charset, String separator, String quote)
        {
            this.charset = charset;
            this.separator = separator;
            this.quote = quote;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InitialTextFormat that = (InitialTextFormat) o;
            return Objects.equals(charset, that.charset) &&
                Objects.equals(separator, that.separator) &&
                Objects.equals(quote, that.quote);
        }

        @Override
        public int hashCode()
        {

            return Objects.hash(charset, separator, quote);
        }

        @Override
        public String toString()
        {
            return "InitialTextFormat{" +
                "charset=" + charset +
                ", separator='" + separator + '\'' +
                ", quote='" + quote + '\'' +
                '}';
        }
    }
    
    public static class FinalTextFormat
    {
        public final InitialTextFormat initialTextFormat;
        public final TrimChoice trimChoice;
        public final ImmutableList<ColumnInfo> columnTypes;

        public FinalTextFormat(InitialTextFormat initialTextFormat, TrimChoice trimChoice, ImmutableList<ColumnInfo> columnTypes)
        {
            this.initialTextFormat = initialTextFormat;
            this.trimChoice = trimChoice;
            this.columnTypes = columnTypes;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FinalTextFormat that = (FinalTextFormat) o;
            return Objects.equals(initialTextFormat, that.initialTextFormat) &&
                Objects.equals(trimChoice, that.trimChoice) &&
                Objects.equals(columnTypes, that.columnTypes);
        }

        @Override
        public int hashCode()
        {

            return Objects.hash(initialTextFormat, trimChoice, columnTypes);
        }

        @Override
        public String toString()
        {
            return "FinalTextFormat{" +
                "initialTextFormat=" + initialTextFormat +
                ", trimChoice=" + trimChoice +
                ", columnTypes=" + columnTypes +
                '}';
        }
    }

    @OnThread(Tag.Simulation)
    public static Import<InitialTextFormat, FinalTextFormat> guessTextFormat(TypeManager typeManager, UnitManager unitManager, Map<Charset, List<String>> initialByCharset)
    {
        @KeyFor("initialByCharset") Charset initialCharsetGuess = GuessFormat.<@KeyFor("initialByCharset") Charset>guessCharset(initialByCharset.keySet());
        SimpleObjectProperty<@Nullable Charset> charsetChoice = new SimpleObjectProperty<>(initialCharsetGuess);
        Pair<String, String> sepAndQuot = guessSepAndQuot(initialByCharset.get(initialCharsetGuess));
        SimpleObjectProperty<@Nullable String> sepChoice = new SimpleObjectProperty<>(sepAndQuot.getFirst());
        SimpleObjectProperty<@Nullable String> quoteChoice = new SimpleObjectProperty<>(sepAndQuot.getSecond());
        ObjectProperty<@Nullable InitialTextFormat> srcFormat = new SimpleObjectProperty<>(makeInitialTextFormat(charsetChoice, sepChoice, quoteChoice));
        
        return new Import<InitialTextFormat, FinalTextFormat>()
        {
            @MonotonicNonNull LabelledGrid labelledGrid;
            
            @Override
            @OnThread(Tag.FXPlatform)
            public Node getGUI()
            {
                if (labelledGrid == null)
                {
                    labelledGrid = new LabelledGrid();
                    labelledGrid.addRow(ImporterGUI.makeGUI(charsetChoiceDetails(initialByCharset.keySet()), charsetChoice));
                    labelledGrid.addRow(ImporterGUI.makeGUI(separatorChoiceDetails(), sepChoice));
                    labelledGrid.addRow(ImporterGUI.makeGUI(quoteChoiceDetails(), quoteChoice));

                    
                    FXPlatformConsumer<@Nullable Object> update = o -> {
                        srcFormat.set(makeInitialTextFormat(charsetChoice, sepChoice, quoteChoice));
                    };
                    FXUtility.addChangeListenerPlatform(charsetChoice, update);
                    FXUtility.addChangeListenerPlatform(quoteChoice, update);
                    FXUtility.addChangeListenerPlatform(sepChoice, update);
                }
                return labelledGrid;
            }

            @Override
            public ObjectExpression<@Nullable InitialTextFormat> currentSrcFormat()
            {
                return srcFormat;
            }

            @Override
            public SimulationFunction<InitialTextFormat, Pair<TrimChoice, RecordSet>> loadSource()
            {
                return this::loadSrc;
            }

            @OnThread(Tag.Simulation)
            public Pair<TrimChoice, RecordSet> loadSrc(InitialTextFormat initialTextFormat) throws GuessException, InternalException, UserException
            {
                List<String> initialCheck = initialByCharset.get(initialTextFormat.charset);
                if (initialCheck == null)
                    throw new InternalException("initialByCharset key lookup returned null");

                @NonNull List<String> initial = initialCheck;

                String sep = initialTextFormat.separator;
                String quot = initialTextFormat.quote;

                ImmutableList<ArrayList<String>> values = loadValues(initial, sep, quot);
                ImporterUtility.rectangularise(values);
                TrimChoice trimChoice = guessTrim(values);
                ImmutableList<ColumnInfo> columnInfos = guessBodyFormat(unitManager, trimChoice, values);
                return new Pair<>(trimChoice, ImporterUtility.makeEditableRecordSet(typeManager, trimChoice.trim(values), columnInfos));


/*
                double score;
                    Quality quality;
                    if (counts.stream().allMatch(c -> c.intValue() == 0))
                    {
                        // None found; totally rubbish:
                        score = -Double.MAX_VALUE;
                        quality = Quality.FALLBACK;
                    } else
                    {
                        // Higher is better choice so negate:
                        score = -Utility.variance(counts);
                        quality = Quality.PROMISING;
                    }
                    List<ColumnCountChoice> viableColumnCounts = Multisets.copyHighestCountFirst(counts).entrySet().stream().limit(10).<@NonNull ColumnCountChoice>map(e -> new ColumnCountChoice(e.getElement())).collect(Collectors.<@NonNull ColumnCountChoice>toList());

                        double proportionNonText = (double)textFormat.columnTypes.stream().filter(c -> !(c.type instanceof TextColumnType) && !(c.type instanceof BlankColumnType)).count() / (double)textFormat.columnTypes.size();
                        return ChoicePoint.<TextFormat>success(proportionNonText > 0 ? Quality.PROMISING : Quality.FALLBACK, proportionNonText, textFormat); */
            }

            public ImmutableList<ArrayList<String>> loadValues(@NonNull List<String> initial, String sep, String quot)
            {
                List<RowInfo> rowInfos = new ArrayList<>();
                for (int i = 0; i < initial.size(); i++)
                {
                    if (!initial.get(i).isEmpty())
                    {
                        RowInfo rowInfo = splitIntoColumns(initial.get(i), sep, quot);
                        rowInfos.add(rowInfo);
                    }
                }
                return Utility.mapListI(rowInfos, r -> r.columnContents);
            }

            @Override
            public SimulationFunction<Pair<InitialTextFormat, TrimChoice>, Pair<FinalTextFormat, RecordSet>> loadDest()
            {
                return p -> {
                    List<String> initialCheck = initialByCharset.get(p.getFirst().charset);
                    if (initialCheck == null)
                        throw new InternalException("initialByCharset key lookup returned null");

                    @NonNull List<String> initial = initialCheck;
                    
                    ImmutableList<ArrayList<String>> vals = loadValues(initial, p.getFirst().separator, p.getFirst().quote);
                    ImporterUtility.rectangularise(vals);
                    ImmutableList<ColumnInfo> columnTypes = guessBodyFormat(unitManager, p.getSecond(), vals);
                    return new Pair<>(new FinalTextFormat(p.getFirst(), p.getSecond(), columnTypes), ImporterUtility.makeEditableRecordSet(typeManager, p.getSecond().trim(vals), columnTypes));
                };
            }
        };
    }

    public static @Nullable InitialTextFormat makeInitialTextFormat(SimpleObjectProperty<@Nullable Charset> charsetChoice, SimpleObjectProperty<@Nullable String> sepChoice, SimpleObjectProperty<@Nullable String> quoteChoice)
    {
        @Nullable Charset charset = charsetChoice.get();
        @Nullable String sep = sepChoice.get();
        @Nullable String quote = quoteChoice.get();
        if (charset != null && sep != null && quote != null)
            return new InitialTextFormat(charset, sep, quote);
        return null;
    }
    
    private static enum AlphabetItem
    {
        NUMERIC, PUNCTUATION, BOOLEAN, OTHER
    }

    // vals must be rectangular
    public static TrimChoice guessTrim(List<? extends List<String>> values)
    {
        if (values.isEmpty())
            return new TrimChoice(0, 0, 0,0);
        
        int numColumns = values.get(0).size();
        // We begin in the middle, and then expand outwards, as long as our alphabet hasn't had too many changes
        // in too many columns. 
        
        ArrayList<EnumSet<AlphabetItem>> columnAlphabets = new ArrayList<>();
        
        int trimFromTop = 0;
        for (int row = values.size() < 60 ? (values.size() / 2) : 30; row >= 0; row--)
        {
            List<String> rowVals = values.get(row);
            if (columnAlphabets.isEmpty())
            {
                columnAlphabets.addAll(Utility.mapList(rowVals, GuessFormat::calculateAlphabet));
            }
            else
            {
                int alphabetsChanged = 0;
                for (int i = 0; i < numColumns; i++)
                {
                    EnumSet<AlphabetItem> alphabet = calculateAlphabet(rowVals.get(i));
                    alphabetsChanged += columnAlphabets.get(i).addAll(alphabet) && alphabet.contains(AlphabetItem.OTHER) ? 1 : 0;
                }
                if (alphabetsChanged > numColumns / 4)
                {
                    trimFromTop = row + 1;
                    break;
                }
            }
        }
        
        // Now that we have trim from top, we trim the sides by removing any columns which are entirely blank
        // This is equivalent to finding the first non-blank:
        long trimFromTopFinal = trimFromTop;
        int trimLeft = IntStream.range(0, numColumns).filter(c -> values.stream().skip(trimFromTopFinal).anyMatch(row -> !row.get(c).isEmpty())).findFirst().orElse(0);
        int trimRight = IntStream.range(0, numColumns - trimLeft).filter(c -> values.stream().skip(trimFromTopFinal).anyMatch(row -> !row.get(numColumns - 1 - c).isEmpty())).findFirst().orElse(0);
        
        
        return new TrimChoice(trimFromTop, 0, trimLeft, trimRight);
    }

    private static EnumSet<AlphabetItem> calculateAlphabet(String s)
    {
        // Replace numbers with zero:
        s = s.replaceAll("[+-]?[0-9]+(\\.[0-9]+)?(e[+-]?[0-9]+)", "0");
        switch (s.toLowerCase())
        {
            case "t": case "f": case "true": case "false": case "yes": case "no": case "y": case "n":
                return EnumSet.of(AlphabetItem.BOOLEAN);
        }
        EnumSet<AlphabetItem> r = EnumSet.noneOf(AlphabetItem.class);
        s.codePoints().forEach(n -> {
            if (Character.isDigit(n) || Character.getType(n) == Character.CURRENCY_SYMBOL)
            {
                r.add(AlphabetItem.NUMERIC);
            }
            else if (Character.isLetter(n))
            {
                r.add(AlphabetItem.OTHER);
            }
            else if (!Character.isWhitespace(n))
            {
                r.add(AlphabetItem.PUNCTUATION);
            }
        });
        return r;
    }

    private static Pair<String, String> guessSepAndQuot(List<String> lines)
    {
        Pair<String, String> bestSepAndQuot = new Pair<>("", "");
        double bestScore = -Double.MAX_VALUE;

        for (String quot : QUOTE_CHOICES)
        {
            for (String sep : SEP_CHOICES)
            {
                Multiset<Integer> counts = HashMultiset.create();
                for (int i = 0; i < lines.size(); i++)
                {
                    if (!lines.get(i).isEmpty())
                    {
                        counts.add(splitIntoColumns(lines.get(i), sep, quot).columnContents.size());
                    }
                }

                if (counts.elementSet().equals(ImmutableSet.of(1)))
                {
                    // All columns size one; totally rubbish, don't bother remembering
                }
                else if (counts.elementSet().size() == 1)
                {
                    // All sizes the same, but not all zero
                    return new Pair<>(sep, quot);
                }
                else
                {
                    // Higher is better choice so negate:
                    double score = -Utility.variance(counts);
                    if (score > bestScore)
                    {
                        bestScore = score;
                        bestSepAndQuot = new Pair<>(sep, quot);
                    }

                }
            }
        }
        
        return bestSepAndQuot;
    }

    private static <C extends Charset> @Nullable C guessCharset(Collection<C> charsets)
    {
        // Pretty simple: if UTF-8 is in there, use that, else use any.
        Charset utf8 = Charset.forName("UTF-8");
        C arbitrary = null;
        for (C charset : charsets)
        {
            if (charset.equals(utf8))
                return charset;
        }
        return arbitrary;
    }

    private static Either<@Localized String, Charset> pickCharset(String s)
    {
        try
        {
            return Either.right(Charset.forName(s));
        }
        catch (Exception e)
        {
            return Either.left(TranslationUtility.getString("charset.not.available", s));
        }
    }

    private static <T> Function<String, Either<@Localized String, T>> enterSingleChar(Function<String, T> make)
    {
        return s -> {
            if (s.length() == 1)
                return Either.right(make.apply(s));
            else
                return Either.left(TranslationUtility.getString("error.single.char.only"));
        };
    }

    private static class RowInfo
    {
        // Each item is one column's content on this row
        private final ArrayList<String> columnContents = new ArrayList<>();
        // Each pair is (content, style)
        private final List<Pair<String, String>> originalContentAndStyle = new ArrayList<>();

    }

    // Split a row of text into columns, given a separator and a quote character
    private static RowInfo splitIntoColumns(String row, String sep, String quote)
    {
        String escapedQuote = quote + quote;
        
        boolean inQuoted = false;
        StringBuilder sb = new StringBuilder();
        RowInfo r = new RowInfo();
        for (int i = 0; i < row.length();)
        {
            // First check for escaped quote (which may otherwise look like a quote):
            if (inQuoted && !escapedQuote.isEmpty() && row.startsWith(escapedQuote, i))
            {
                // Skip it:
                sb.append(quote);
                i += escapedQuote.length();

                if (!quote.isEmpty() && escapedQuote.endsWith(quote))
                {
                    r.originalContentAndStyle.add(new Pair<>(escapedQuote.substring(0, escapedQuote.length() - quote.length()), "escaped-quote-escape"));
                    r.originalContentAndStyle.add(new Pair<>(quote, "escaped-quote-quote"));
                }
                else
                {
                    r.originalContentAndStyle.add(new Pair<>(escapedQuote, "escaped-quote"));
                }
            }
            else if (!quote.isEmpty() && row.startsWith(quote, i) && (inQuoted || sb.toString().trim().isEmpty()))
            {
                if (!inQuoted)
                {
                    // Ignore the spaces beforehand:
                    sb = new StringBuilder();
                }
                inQuoted = !inQuoted;
                i += quote.length();
                r.originalContentAndStyle.add(new Pair<>(quote, inQuoted ? "quote-begin" : "quote-end"));
            }
            else if (!inQuoted && !sep.isEmpty() && row.startsWith(sep, i))
            {
                r.columnContents.add(sb.toString().trim());
                r.originalContentAndStyle.add(new Pair<>(replaceTab(sep), "separator"));
                sb = new StringBuilder();
                i += sep.length();
            }
            else
            {
                // Nothing special:
                sb.append(row.charAt(i));
                r.originalContentAndStyle.add(new Pair<>(replaceTab(row.substring(i, i+1)), "normal"));
                i += 1;

            }
        }
        r.columnContents.add(sb.toString().trim());
        return r;
    }

    private static String replaceTab(String s)
    {
        return s.replace("\t", "\u27FE");
    }

    // Note that the trim choice should not already have been applied.  But values should be rectangular
    private static ImmutableList<ColumnInfo> guessBodyFormat(UnitManager mgr, TrimChoice trimChoice, @NonNull List<@NonNull ? extends List<@NonNull String>> untrimmed) throws GuessException
    {
        // true should be the first item in each sub-list:
        final ImmutableList<ImmutableList<String>> BOOLEAN_SETS = ImmutableList.of(ImmutableList.of("t", "f"), ImmutableList.of("true", "false"), ImmutableList.of("y", "n"), ImmutableList.of("yes", "no"));
        List<List<String>> initialVals = trimChoice.trim(untrimmed);
        int columnCount = initialVals.isEmpty() ? 0 : initialVals.get(0).size();
        List<ColumnType> columnTypes = new ArrayList<>();
        List<Integer> blankRows = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++)
        {
            // Have a guess at column columntype:
            boolean allNumeric = true;
            // The "blank", which may be empty string, or might be another value (e.g. "NA")
            String numericBlank = null;
            // Only false if we find content which is not parseable as a number:
            boolean allNumericOrBlank = true;
            boolean allBlank = true;
            ArrayList<DateFormat> possibleDateFormats = new ArrayList<>(
                ToDate.FORMATS.stream().<DateTimeFormatter>flatMap(l -> l.stream())
                    .map(formatter -> new DateFormat(DateTimeType.YEARMONTHDAY, true, formatter, LocalDate::from)).collect(Collectors.<DateFormat>toList())
            );
            possibleDateFormats.addAll(
                ToTime.FORMATS.stream()
                    .map(formatter -> new DateFormat(DateTimeType.TIMEOFDAY, false, formatter, LocalTime::from)).collect(Collectors.<DateFormat>toList())
            );
            ArrayList<ImmutableList<String>> possibleBooleanSets = new ArrayList<>(BOOLEAN_SETS);
            @Nullable String commonPrefix = null;
            @Nullable String commonSuffix = null;
            List<Integer> decimalPlaces = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < initialVals.size(); rowIndex++)
            {
                List<String> row = initialVals.get(rowIndex);
                if (row.isEmpty() || row.stream().allMatch(String::isEmpty))
                {
                    // Whole row is blank
                    // Only add it once, not once per column:
                    if (columnIndex == 0)
                        blankRows.add(rowIndex);
                }
                else
                {
                    String val = columnIndex < row.size() ? row.get(columnIndex).trim() : "";
                    if (!val.isEmpty())
                    {
                        allBlank = false;
                        
                        String originalVal = val;
                        possibleBooleanSets.removeIf(l -> !l.contains(originalVal.toLowerCase()));

                        if (commonPrefix == null)
                        {
                            // Look for a prefix of currency symbol:
                            for (int i = 0; i < val.length(); i = val.offsetByCodePoints(i, 1))
                            {
                                if (Character.getType(val.codePointAt(i)) == Character.CURRENCY_SYMBOL)
                                {
                                    commonPrefix = val.substring(0, val.offsetByCodePoints(i, 1));
                                }
                                else
                                    break;
                            }
                        }
                        if (commonSuffix == null)
                        {
                            if (val.length() < 100)
                            {
                                int[] codepoints = val.codePoints().toArray();
                                // Look for a suffix of currency symbol:
                                for (int i = codepoints.length - 1; i >= 0 ; i--)
                                {
                                    if (codepoints[i] == '%' || Character.getType(codepoints[i]) == Character.CURRENCY_SYMBOL)
                                    {
                                        commonSuffix = new String(codepoints, i, codepoints.length - i);
                                    }
                                    else
                                        break;
                                }
                            }
                        }



                        int first;
                        // Not an else; if we just picked commonPrefix, we should find it here:
                        if (commonPrefix != null && val.startsWith(commonPrefix))
                        {
                            // Take off prefix and continue as is:
                            val = val.substring(commonPrefix.length()).trim();
                        }
                        else if (commonPrefix != null && !Character.isDigit(first = val.codePointAt(0)) && first != '+' && first != '-')
                        {
                            // We thought we had a prefix, but we haven't found it here, so give up:
                            commonPrefix = null;
                            allNumeric = false;
                            allNumericOrBlank = false;
                            //break;
                        }
                        
                        if (commonSuffix != null && val.endsWith(commonSuffix))
                        {
                            // Take off suffix and continue:
                            val = val.substring(0, val.length() - commonSuffix.length());
                        }
                        else if (commonSuffix != null && !Character.isDigit(val.length() - 1))
                        {
                            // We thought we had a prefix, but we haven't found it here, so give up:
                            commonSuffix = null;
                            allNumeric = false;
                            allNumericOrBlank = false;
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
                            if (numericBlank == null || numericBlank.equals(val))
                            {
                                // First non-number we've seen; this might be our blank:
                                numericBlank = val;
                            }
                            else
                            {
                                allNumericOrBlank = false;
                            }
                            commonPrefix = null;
                        }
                        // Minimum length for date or time is 5 by my count
                        if (val.length() < 5)
                        {
                            possibleDateFormats.clear();
                        }
                        else
                        {
                            String valPreprocessed = Utility.preprocessDate(val);
                            // Seems expensive but most will be knocked out immediately:
                            for (Iterator<DateFormat> dateFormatIt = possibleDateFormats.iterator(); dateFormatIt.hasNext(); )
                            {
                                DateFormat dateFormat = dateFormatIt.next();
                                try
                                {
                                    dateFormat.formatter.parse(dateFormat.preprocessDate ? valPreprocessed : val, dateFormat.destQuery);
                                }
                                catch (DateTimeParseException e)
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
            {
                columnTypes.add(ColumnType.BLANK);
            }
            else if (!possibleDateFormats.isEmpty())
            {
                DateFormat chosen = possibleDateFormats.get(0);
                columnTypes.add(new CleanDateColumnType(chosen.dateTimeType, chosen.preprocessDate, chosen.formatter, chosen.destQuery));
            }
            else if (!possibleBooleanSets.isEmpty())
            {
                columnTypes.add(new BoolColumnType(possibleBooleanSets.get(0).get(0)));
            }
            else if (allNumeric)
            {
                columnTypes.add(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix, commonSuffix));
            }
            else if (allNumericOrBlank && numericBlank != null)
            {
                columnTypes.add(new OrBlankColumnType(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix, commonSuffix), numericBlank));
            }
            else
                columnTypes.add(new TextColumnType());
            // Go backwards to find column titles:

            /*
            for (int headerRow = headerRows - 1; headerRow >= 0; headerRow--)
            {
                // Must actually have our column in it:
                if (columnIndex < initialVals.get(headerRow).size() && !initialVals.get(headerRow).get(columnIndex).isEmpty())
                {
                    viableColumnNameRows.compute(headerRow, (a, pre) -> pre == null ? 1 : (1 + pre));
                }
            }
            */
        }
        int nonBlankColumnCount = (int)columnTypes.stream().filter(c -> !(c instanceof BlankColumnType)).count();
        
        ImmutableList.Builder<ColumnInfo> columns = ImmutableList.builderWithExpectedSize(columnCount);
        HashSet<ColumnId> usedNames = new HashSet<>();
        for (int columnIndex = 0; columnIndex < columnTypes.size(); columnIndex++)
        {
            String original = trimChoice.trimFromTop == 0 ? "" : untrimmed.get(trimChoice.trimFromTop - 1).get(columnIndex);
            StringBuilder stringBuilder = new StringBuilder();
            int[] codepoints = original.codePoints().toArray();
            boolean lastWasSpace = false;
            final int SPACE_CODEPOINT = 32;
            for (int i = 0; i < codepoints.length; i++)
            {
                int codepoint = codepoints[i];
                if (!ColumnId.validCharacter(codepoint, i == 0))
                {
                    // Can we make it valid with a prefix?
                    if (i == 0 && ColumnId.validCharacter(codepoint, false))
                    {
                        stringBuilder.append('C').append(new String(codepoints, i, 1));
                    }
                    // Otherwise invalid, so drop it.
                }
                else if (!lastWasSpace || codepoint != SPACE_CODEPOINT)
                {
                    lastWasSpace = codepoint == SPACE_CODEPOINT;
                    stringBuilder.append(new String(codepoints, i, 1));
                }
            }
            String validated = stringBuilder.toString().trim();
            String prospectiveName = validated;
            if (validated.isEmpty())
            {
                validated = "C";
                prospectiveName = "C1";
            }
            // Now check if it is taken:
            
            int appendNum = 1;
            while (usedNames.contains(new ColumnId(prospectiveName)))
            {
                prospectiveName = validated + appendNum;
                appendNum += 1;
            }
            ColumnId columnName = new ColumnId(prospectiveName);
            
            columns.add(new ColumnInfo(columnTypes.get(columnIndex), columnName));
            usedNames.add(columnName);
        }
        return columns.build();
    }

    public static class ImportInfo<FORMAT>
    {
        private final TableId suggestedTableId;
        private final FORMAT format; 
        //public final boolean linkFile;

        public ImportInfo(String suggestedName/*, boolean linkFile*/, FORMAT format)
        {
            this.suggestedTableId = new TableId(suggestedName);
            this.format = format;
            //this.linkFile = linkFile;
        }
        
        public InitialLoadDetails getInitialLoadDetails(CellPosition destination)
        {
            return new InitialLoadDetails(suggestedTableId, destination, null);
        }
        
        public FORMAT getFormat()
        {
            return format;
        }
    }

    @OnThread(Tag.Simulation)
    public static void guessTextFormatGUI_Then(TableManager mgr, File file, String suggestedName, Map<Charset, List<String>> initial, SimulationConsumer<ImportInfo<FinalTextFormat>> then)
    {
        Import<InitialTextFormat, FinalTextFormat> imp = guessTextFormat(mgr.getTypeManager(), mgr.getUnitManager(), initial);
        Platform.runLater(() -> {
            new ImportChoicesDialog<FinalTextFormat>(mgr, suggestedName, imp).showAndWait().ifPresent(importInfo -> {
                Workers.onWorkerThread("Importing", Priority.SAVE_ENTRY, () -> FXUtility.alertOnError_(() -> then.consume(importInfo)));
            });
        });
    }
}
