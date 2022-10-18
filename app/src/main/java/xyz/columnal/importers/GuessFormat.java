/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.importers;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.math.Stats;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.columntype.BlankColumnType;
import xyz.columnal.data.columntype.BoolColumnType;
import xyz.columnal.data.columntype.CleanDateColumnType;
import xyz.columnal.data.columntype.ColumnType;
import xyz.columnal.data.columntype.NumericColumnType;
import xyz.columnal.data.columntype.OrBlankColumnType;
import xyz.columnal.data.columntype.TextColumnType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.importers.gui.ImportChoicesDialog;
import xyz.columnal.importers.gui.ImporterGUI;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumer;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.LabelledGrid;
import xyz.columnal.utility.TranslationUtility;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 20/10/2016.
 */
public class GuessFormat
{
    public static final int INITIAL_ROWS_TEXT_FILE = 10_000;
    public static final ImmutableList<String> SEP_CHOICES = ImmutableList.of(",", ";", "\t", ":", "|", " ");
    public static final ImmutableList<String> QUOTE_CHOICES = ImmutableList.of("", "\"", "\'");

    // SRC_FORMAT is what you need to get the initial source table
    // DEST_FORMAT is what you calculate post-trim for the column formats for the final item. 
    public static interface Import<SRC_FORMAT, DEST_FORMAT>
    {
        public static class SrcDetails
        {
            public final TrimChoice trimChoice;
            public final RecordSet recordSet;
            public final @Nullable ImmutableList<@Localized String> columnNameOverrides; // Same length and order as recordSet.getColumns()

            public SrcDetails(TrimChoice trimChoice, RecordSet recordSet, @Nullable ImmutableList<@Localized String> columnNameOverrides)
            {
                this.trimChoice = trimChoice;
                this.recordSet = recordSet;
                this.columnNameOverrides = columnNameOverrides;
            }
        }
        
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
        @OnThread(Tag.Simulation)
        SrcDetails loadSource(SRC_FORMAT srcFormat) throws InternalException, UserException;
        
        // Get the function which would load the final record set (import RHS) for the given format and trim.
        // After trimming, the types of the columns are guessed at.
        @OnThread(Tag.Simulation)
        Pair<DEST_FORMAT, RecordSet> loadDest(SRC_FORMAT srcFormat, TrimChoice trimChoice) throws InternalException, UserException;
        
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
            SrcDetails srcDetails = loadSource(srcFormat);
            return loadDest(srcFormat, srcDetails.trimChoice).getFirst();
        }
    }
    
    /**
     *
     * @param mgr
     * @param vals List of rows, where each row is list of values
     * @return
     */
    public static ImmutableList<ColumnInfo> guessGeneralFormat(UnitManager mgr, List<? extends List<String>> vals, TrimChoice trimChoice, BiFunction<TrimChoice, Integer, ColumnId> getColumnName) throws GuessException
    {
        return guessBodyFormat(mgr, trimChoice, vals, getColumnName);
    }

    public static class GuessException extends UserException
    {
        public GuessException(String message)
        {
            super(message);
        }
    }

    public static ChoiceDetails<Charset> charsetChoiceDetails(Stream<Charset> available)
    {
        // In future, we should allow users to specify
        // (For now, they can just re-save as UTF-8)
        return new ChoiceDetails<>("guess.charset", "guess-format/charset", available.collect(ImmutableList.<Charset>toImmutableList()), null);
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
        // null means no separator; just one big column
        public final @Nullable String separator;
        // empty means no quote
        public final @Nullable String quote;

        public InitialTextFormat(Charset charset, @Nullable String separator, @Nullable String quote)
        {
            this.charset = charset;
            this.separator = (separator != null && separator.isEmpty()) ? null : separator;
            this.quote = (quote != null && quote.isEmpty()) ? null : quote;
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
    public static Import<InitialTextFormat, FinalTextFormat> guessTextFormat(TypeManager typeManager, UnitManager unitManager, Map<Charset, List<String>> initialByCharset, @Nullable InitialTextFormat itfOverride, @Nullable TrimChoice trimOverride)
    {
        final Optional<@KeyFor("initialByCharset") Charset> initialCharsetGuess;
        if (itfOverride != null)
        {
            @SuppressWarnings("keyfor")
            @KeyFor("initialByCharset") Charset c = itfOverride.charset;
            initialCharsetGuess = Optional.of(c);
        }
        else
        {
            @SuppressWarnings("keyfor")
            Optional<@KeyFor("initialByCharset") Charset> c = GuessFormat.<@KeyFor("initialByCharset") Charset>guessCharset(initialByCharset.keySet());
            initialCharsetGuess = c;
        }
        SimpleObjectProperty<@Nullable Charset> charsetChoice = new SimpleObjectProperty<>(initialCharsetGuess.<Charset>map(c -> c).orElse(null));
        @Nullable Pair<@Nullable String, @Nullable String> sepAndQuot = itfOverride != null ? new Pair<>(itfOverride.separator, itfOverride.quote) :
            initialCharsetGuess.map(initialByCharset::get).map(GuessFormat::guessSepAndQuot).orElse(null);
        SimpleObjectProperty<@Nullable String> sepChoice = new SimpleObjectProperty<>(sepAndQuot == null ? null : sepAndQuot.getFirst());
        SimpleObjectProperty<@Nullable String> quoteChoice = new SimpleObjectProperty<>(sepAndQuot == null ? null : sepAndQuot.getSecond());
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
                    labelledGrid.addRow(ImporterGUI.makeGUI(charsetChoiceDetails(initialByCharset.keySet().stream().map(x -> x)), charsetChoice));
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

            @OnThread(Tag.Simulation)
            public SrcDetails loadSource(InitialTextFormat initialTextFormat) throws InternalException, UserException
            {
                List<String> initialCheck = initialByCharset.get(initialTextFormat.charset);
                if (initialCheck == null)
                    throw new InternalException("initialByCharset key lookup returned null");

                @NonNull List<String> initial = initialCheck;

                String sep = initialTextFormat.separator;
                String quot = initialTextFormat.quote;

                ImmutableList<ArrayList<String>> values = loadValues(initial, sep, quot);
                ImporterUtility.rectangulariseAndRemoveBlankRows(values);
                TrimChoice trimChoice = trimOverride != null ? trimOverride : guessTrim(values);
                ImmutableList.Builder<ColumnInfo> columnInfos = ImmutableList.builder();
                if (!values.isEmpty())
                {
                    for (int i = 0; i < values.get(0).size(); i++)
                    {
                        ColumnId columnId = new ColumnId(IdentifierUtility.identNum("Col", (i + 1)));
                        columnInfos.add(new ColumnInfo(new TextColumnType(), columnId));
                    }
                }
                return new SrcDetails(trimChoice, ImporterUtility.makeEditableRecordSet(typeManager, values, columnInfos.build()), null);
                

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

            public ImmutableList<ArrayList<String>> loadValues(@NonNull List<String> initial, @Nullable String sep, @Nullable String quot)
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
            public Pair<FinalTextFormat, RecordSet> loadDest(InitialTextFormat initialTextFormat, TrimChoice trimChoice) throws InternalException, UserException
            {
                List<String> initialCheck = initialByCharset.get(initialTextFormat.charset);
                if (initialCheck == null)
                    throw new InternalException("initialByCharset key lookup returned null");

                @NonNull List<String> initial = initialCheck;
                
                ImmutableList<ArrayList<String>> vals = loadValues(initial, initialTextFormat.separator, initialTextFormat.quote);
                ImporterUtility.rectangulariseAndRemoveBlankRows(vals);
                ImmutableList<ColumnInfo> columnTypes = guessBodyFormat(unitManager, trimChoice, vals, null);
                Log.debug("Vals width " + vals.get(0).size() + " cols " + columnTypes.size() + " which are: " + Utility.listToString(columnTypes));
                return new Pair<>(new FinalTextFormat(initialTextFormat, trimChoice, columnTypes), ImporterUtility.makeEditableRecordSet(typeManager, trimChoice.trim(vals), columnTypes));
            }
        };
    }

    public static @Nullable InitialTextFormat makeInitialTextFormat(SimpleObjectProperty<@Nullable Charset> charsetChoice, SimpleObjectProperty<@Nullable String> sepChoice, SimpleObjectProperty<@Nullable String> quoteChoice)
    {
        @Nullable Charset charset = charsetChoice.get();
        @Nullable String sep = sepChoice.get();
        @Nullable String quote = quoteChoice.get();
        if (charset != null)
            return new InitialTextFormat(charset, sep, quote);
        return null;
    }
    
    private static enum AlphabetItem
    {
        NUMERIC, PUNCTUATION, BOOLEAN, LETTER
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
        int startingRow = values.size() < 16 ? (values.size() / 2) : 8;
        for (int row = startingRow; row >= 0; row--)
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
                    boolean changed = false;
                    for (AlphabetItem alphabetItem : alphabet)
                    {
                        boolean wasNumericOnly = !columnAlphabets.get(i).contains(AlphabetItem.LETTER) && !columnAlphabets.get(i).contains(AlphabetItem.BOOLEAN);
                        boolean wasBooleanOnly = columnAlphabets.get(i).equals(EnumSet.of(AlphabetItem.BOOLEAN)) && startingRow - row > 1;
                        // Alphabet changes are: adding non-numeric to numeric
                        // or adding punctuation to non-letter
                        if (columnAlphabets.get(i).add(alphabetItem) && (wasNumericOnly || wasBooleanOnly))
                            changed = true;
                    }
                    if (changed)
                        alphabetsChanged += 1;
                }
                if (alphabetsChanged >= (numColumns < 4 ? Math.max(1, numColumns / 2) : numColumns / 4))
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
        s = s.replaceAll("[+-]?[0-9]+(,[0-9]+)*(\\.[0-9]+)?(e[+-]?[0-9]+)?", "0");
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
                r.add(AlphabetItem.LETTER);
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

                if (counts.isEmpty() || counts.elementSet().equals(ImmutableSet.of(1)))
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
                    // Higher score is better choice so negate variance:
                    // Also add mean to bias towards more columns if similar variance
                    double score = (Stats.meanOf(counts) / 10.0) - Stats.of(counts).sampleVariance();
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

    // Use Optional here, not Nullable, because the latter can accidentally fool checker framework, see
    // https://github.com/typetools/checker-framework/issues/1922
    private static <C extends Charset> Optional<C> guessCharset(Collection<C> charsets)
    {
        // Pretty simple: if UTF-8 is in there, use that, else use any.
        Charset utf8 = StandardCharsets.UTF_8;
        C arbitrary = null;
        for (C charset : charsets)
        {
            if (charset.equals(utf8))
                return Optional.of(charset);
            else
                arbitrary = charset;
        }
        return Optional.ofNullable(arbitrary);
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
    private static RowInfo splitIntoColumns(String row, @Nullable String sep, @Nullable String quote)
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

                if (quote != null && !quote.isEmpty() && escapedQuote.endsWith(quote))
                {
                    r.originalContentAndStyle.add(new Pair<>(escapedQuote.substring(0, escapedQuote.length() - quote.length()), "escaped-quote-escape"));
                    r.originalContentAndStyle.add(new Pair<>(quote, "escaped-quote-quote"));
                }
                else
                {
                    r.originalContentAndStyle.add(new Pair<>(escapedQuote, "escaped-quote"));
                }
            }
            else if (quote != null && !quote.isEmpty() && row.startsWith(quote, i) && (inQuoted || sb.toString().trim().isEmpty()))
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
            else if (!inQuoted && sep != null && !sep.isEmpty() && row.startsWith(sep, i))
            {
                r.columnContents.add(sb.toString());
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
        r.columnContents.add(sb.toString());
        return r;
    }

    private static String replaceTab(String s)
    {
        return s.replace('\t', '\u27FE');
    }

    // Note that the trim choice should not already have been applied.  But values should be rectangular
    private static ImmutableList<ColumnInfo> guessBodyFormat(UnitManager mgr, TrimChoice trimChoice, @NonNull List<@NonNull ? extends List<@NonNull String>> untrimmed, @Nullable BiFunction<TrimChoice, Integer, ColumnId> getColumnName) throws GuessException
    {
        // true should be the first item in each sub-list:
        final ImmutableList<ImmutableList<String>> BOOLEAN_SETS = ImmutableList.<ImmutableList<String>>of(ImmutableList.<String>of("t", "f"), ImmutableList.<String>of("true", "false"), ImmutableList.<String>of("y", "n"), ImmutableList.<String>of("yes", "no"));
        List<List<String>> initialVals = trimChoice.trim(untrimmed);
        int columnCount = initialVals.isEmpty() ? 0 : initialVals.get(0).size();
        List<ColumnType> columnTypes = new ArrayList<>();
        List<Integer> blankRows = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++)
        {
            // Have a guess at column columntype:
            boolean allNumeric = true;
            boolean anyNumeric = false;
            // The "blank", which may be empty string, or might be another value (e.g. "NA")
            String numericBlank = null;
            // Only false if we find content which is not parseable as a number:
            boolean allNumericOrBlank = true;
            boolean allBlank = true;
            ArrayList<DateFormat> possibleDateFormats = new ArrayList<>(
                new DateTimeInfo(DateTimeType.YEARMONTHDAY).getFlexibleFormatters().stream().<DateTimeFormatter>flatMap(l -> l.stream())
                    .map(formatter -> new DateFormat(DateTimeType.YEARMONTHDAY, true, formatter, LocalDate::from)).collect(Collectors.<DateFormat>toList())
            );
            possibleDateFormats.addAll(
                    new DateTimeInfo(DateTimeType.TIMEOFDAY).getFlexibleFormatters().stream().flatMap(l -> l.stream())
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
                        possibleBooleanSets.removeIf(l -> !l.contains(originalVal.trim().toLowerCase()));

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
                            anyNumeric = true;
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
                            String valTrimmed = val.trim();
                            String valPreprocessed = Utility.preprocessDate(valTrimmed);
                            // Seems expensive but most will be knocked out immediately:
                            for (Iterator<DateFormat> dateFormatIt = possibleDateFormats.iterator(); dateFormatIt.hasNext(); )
                            {
                                DateFormat dateFormat = dateFormatIt.next();
                                try
                                {
                                    dateFormat.formatter.parse(dateFormat.preprocessDate ? valPreprocessed : valTrimmed, dateFormat.destQuery);
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
                        if (allNumericOrBlank && numericBlank == null)
                            numericBlank = "";
                        possibleBooleanSets.clear();
                        possibleDateFormats.clear();
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
                columnTypes.add(new BoolColumnType(possibleBooleanSets.get(0).get(0), possibleBooleanSets.get(0).get(1)));
            }
            else if (allNumeric)
            {
                columnTypes.add(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix, commonSuffix));
            }
            else if (allNumericOrBlank && anyNumeric && numericBlank != null)
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
            ColumnId columnName;
            if (getColumnName != null)
            {
                columnName = getColumnName.apply(trimChoice, columnIndex);
            }
            else
            {
                String original = trimChoice.trimFromTop == 0 ? "" : untrimmed.get(trimChoice.trimFromTop - 1).get(columnIndex + trimChoice.trimFromLeft).trim();
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
                columnName = findName(usedNames, stringBuilder);
            }
            columns.add(new ColumnInfo(columnTypes.get(columnIndex), columnName));
            usedNames.add(columnName);
        }
        return columns.build();
    }

    private static ColumnId findName(HashSet<ColumnId> usedNames, StringBuilder stringBuilder)
    {
        ColumnId columnName;
        String fromFile = stringBuilder.toString().trim();
        @ExpressionIdentifier String validated = IdentifierUtility.fixExpressionIdentifier(fromFile, "C");
        @ExpressionIdentifier String prospectiveName = 
validated.equals("C") && !fromFile.equals("C") ? IdentifierUtility.identNum(validated, 1) : validated;
        // Now check if it is taken:

        int appendNum = 1;
        while (usedNames.contains(new ColumnId(prospectiveName)))
        {
            prospectiveName = IdentifierUtility.identNum(validated, appendNum);
            appendNum += 1;
        }
        columnName = new ColumnId(prospectiveName);
        return columnName;
    }

    public static class ImportInfo<FORMAT>
    {
        private final TableId suggestedTableId;
        private final FORMAT format; 
        //public final boolean linkFile;

        public ImportInfo(@ExpressionIdentifier String suggestedName/*, boolean linkFile*/, FORMAT format)
        {
            this.suggestedTableId = new TableId(suggestedName);
            this.format = format;
            //this.linkFile = linkFile;
        }
        
        public InitialLoadDetails getInitialLoadDetails(CellPosition destination)
        {
            return new InitialLoadDetails(suggestedTableId, null, destination, null);
        }
        
        public FORMAT getFormat()
        {
            return format;
        }
    }

    @OnThread(Tag.Simulation)
    public static void guessTextFormatGUI_Then(@Nullable Window parentWindow, TableManager mgr, File file, String suggestedName, Map<Charset, List<String>> initial, SimulationConsumer<ImportInfo<FinalTextFormat>> then)
    {
        Import<InitialTextFormat, FinalTextFormat> imp = guessTextFormat(mgr.getTypeManager(), mgr.getUnitManager(), initial, null, null);
        Platform.runLater(() -> {
            new ImportChoicesDialog<InitialTextFormat, FinalTextFormat>(parentWindow, suggestedName, imp).showAndWait().ifPresent(importInfo -> {
                Workers.onWorkerThread("Importing", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.import"), () -> then.consume(importInfo)));
            });
        });
    }
}
