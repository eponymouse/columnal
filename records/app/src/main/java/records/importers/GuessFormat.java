package records.importers;

import com.google.common.base.Objects;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.importers.ChoicePoint.Choice;
import records.importers.ChoicePoint.ChoiceType;
import records.importers.ChoicePoint.Quality;
import records.transformations.function.ToDate;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                    if (format.columnTypes.stream().allMatch(c -> c.type instanceof TextColumnType || c.type instanceof BlankColumnType))
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
            Format fmt = new Format(0, Collections.singletonList(new ColumnInfo(new TextColumnType(), new ColumnId("Content"))));
            String msg = e.getLocalizedMessage();
            fmt.recordProblem(msg == null ? "Unknown" : msg);
            return fmt;
        }
    }

    public static class GuessException extends UserException
    {
        public GuessException(String message)
        {
            super(message);
        }
    }

    public static class CharsetChoice extends Choice
    {
        private final Charset charset;
        private final String charsetName;

        public CharsetChoice(String charsetName)
        {
            this.charsetName = charsetName;
            charset = Charset.forName(charsetName);
        }

        public CharsetChoice(Charset charset)
        {
            this.charsetName = charset.displayName();
            this.charset = charset;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CharsetChoice that = (CharsetChoice) o;

            return charset.equals(that.charset);
        }

        @Override
        public int hashCode()
        {
            return charset.hashCode();
        }

        @Override
        public String toString()
        {
            return charsetName;
        }

        public static ChoiceType<CharsetChoice> getType()
        {
            return new ChoiceType<>(CharsetChoice.class,"guess.charset");
        }
    }

    // public for testing
    public static class HeaderRowChoice extends Choice
    {
        private final int numHeaderRows;

        // public for testing
        public HeaderRowChoice(int numHeaderRows)
        {
            this.numHeaderRows = numHeaderRows;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HeaderRowChoice that = (HeaderRowChoice) o;

            return numHeaderRows == that.numHeaderRows;
        }

        @Override
        public int hashCode()
        {
            return numHeaderRows;
        }

        @Override
        public String toString()
        {
            return Integer.toString(numHeaderRows);
        }

        public static ChoiceType<HeaderRowChoice> getType()
        {
            return new ChoiceType<>(HeaderRowChoice.class, "guess.headerRow");
        }
    }

    // public for testing
    public static class SeparatorChoice extends Choice
    {
        private final @Nullable String separator;

        // public for testing
        public SeparatorChoice(@Nullable String separator)
        {
            this.separator = separator;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SeparatorChoice that = (SeparatorChoice) o;

            return Objects.equal(separator, that.separator);
        }

        @Override
        public int hashCode()
        {
            return separator == null ? 0 : separator.hashCode();
        }

        @Override
        public String toString()
        {
            if (separator == null)
                return "<None>";
            if (separator.equals(" "))
                return "<Space>";
            if (separator.equals("\t"))
                return "<Tab>";
            return separator;
        }

        public static ChoiceType<SeparatorChoice> getType()
        {
            return new ChoiceType<>(SeparatorChoice.class, "guess.separator");
        }
    }

    public static class QuoteChoice extends Choice
    {
        private final @Nullable String quote;
        private final @Nullable String escapedQuote;

        public QuoteChoice(@Nullable String quote)
        {
            this.quote = quote;
            // Only option at the moment is doubled quote:
            this.escapedQuote = quote == null ? null : (quote + quote);
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QuoteChoice that = (QuoteChoice) o;

            return quote != null ? quote.equals(that.quote) : that.quote == null;
        }

        @Override
        public int hashCode()
        {
            return quote != null ? quote.hashCode() : 0;
        }

        @Override
        public String toString()
        {
            return quote == null ? "<None>" : quote;
        }

        public static ChoiceType<QuoteChoice> getType()
        {
            return new ChoiceType<>(QuoteChoice.class, "guess.quote");
        }
    }

    // public for testing
    public static class ColumnCountChoice extends Choice
    {
        private final int columnCount;

        // public for testing
        public ColumnCountChoice(int columnCount)
        {
            this.columnCount = columnCount;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ColumnCountChoice that = (ColumnCountChoice) o;

            return columnCount == that.columnCount;
        }

        @Override
        public int hashCode()
        {
            return columnCount;
        }

        @Override
        public String toString()
        {
            return Integer.toString(columnCount);
        }

        public static ChoiceType<ColumnCountChoice> getType()
        {
            return new ChoiceType<>(ColumnCountChoice.class, "guess.column");
        }
    }

    private static SeparatorChoice sep(String separator)
    {
        return new SeparatorChoice(separator);
    }

    private static QuoteChoice quot(@Nullable String quoteChar)
    {
        return new QuoteChoice(quoteChar);
    }

    public static ChoicePoint<?, TextFormat> guessTextFormat(UnitManager mgr, Map<Charset, List<String>> initialByCharset)
    {
        return ChoicePoint.choose(Quality.PROMISING, 0, CharsetChoice.getType(), (CharsetChoice chc) ->
        {
            List<String> initialCheck = initialByCharset.get(chc.charset);
            if (initialCheck == null)
                throw new InternalException("initialByCharset key lookup returned null");

            @NonNull List<String> initial = initialCheck;

            List<Choice> headerRowChoices = new ArrayList<>();
            for (int headerRows = 0; headerRows < Math.min(MAX_HEADER_ROWS, initial.size() - 1); headerRows++)
            {
                headerRowChoices.add(new HeaderRowChoice(headerRows));
            }

            return ChoicePoint.choose(Quality.PROMISING, 0, HeaderRowChoice.getType(), (HeaderRowChoice hrc) ->
                ChoicePoint.choose(Quality.PROMISING, 0, SeparatorChoice.getType(), (SeparatorChoice sep) ->
                ChoicePoint.choose(Quality.PROMISING, 0, QuoteChoice.getType(), (QuoteChoice quot) ->
                {
                    Multiset<Integer> counts = HashMultiset.create();
                    for (int i = hrc.numHeaderRows; i < initial.size(); i++)
                    {
                        if (!initial.get(i).isEmpty())
                        {
                            counts.add(splitIntoColumns(initial.get(i), sep, quot).size());
                        }
                    }

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

                    return ChoicePoint.choose(quality, score, ColumnCountChoice.getType(), (ColumnCountChoice cc) ->
                    {
                        List<@NonNull List<@NonNull String>> initialVals = Utility.<@NonNull String, @NonNull List<@NonNull String>>mapList(initial, s -> splitIntoColumns(s, sep, quot));
                        Format format = guessBodyFormat(mgr, cc.columnCount, hrc.numHeaderRows, initialVals);
                        TextFormat textFormat = new TextFormat(format, sep.separator, quot.quote, chc.charset);
                        double proportionNonText = (double)textFormat.columnTypes.stream().filter(c -> !(c.type instanceof TextColumnType)).count() / (double)textFormat.columnTypes.size();
                        return ChoicePoint.<TextFormat>success(proportionNonText > 0 ? Quality.PROMISING : Quality.FALLBACK, proportionNonText, textFormat);
                    }, viableColumnCounts.toArray(new ColumnCountChoice[0]));
                }, quot(null), quot("\""), quot("\'"))
                , sep(";"), sep(","), sep("\t"), sep(":"), sep(" "))
                , headerRowChoices.toArray(new HeaderRowChoice[0]));
        }, initialByCharset.keySet().stream().<@NonNull CharsetChoice>map(CharsetChoice::new).collect(Collectors.<@NonNull CharsetChoice>toList()).<@NonNull CharsetChoice>toArray(new @NonNull CharsetChoice[0]));
    }

    // Split a row of text into columns, given a separator and a quote character
    private static List<String> splitIntoColumns(String row, SeparatorChoice sep, QuoteChoice quot)
    {
        boolean inQuoted = false;
        StringBuilder sb = new StringBuilder();
        List<String> r = new ArrayList<>();
        for (int i = 0; i < row.length();)
        {
            // First check for escaped quote (which may otherwise look like a quote):
            if (inQuoted && quot.escapedQuote != null && row.startsWith(quot.escapedQuote, i))
            {
                // Skip it:
                sb.append(quot.quote);
                i += quot.escapedQuote.length();
                continue;
            }
            else if (quot.quote != null && row.startsWith(quot.quote, i))
            {
                inQuoted = !inQuoted;
                i += quot.quote.length();
            }
            else if (!inQuoted && sep.separator != null && row.startsWith(sep.separator, i))
            {
                r.add(sb.toString());
                sb = new StringBuilder();
                i += sep.separator.length();
            }
            else
            {
                // Nothing special:
                sb.append(row.charAt(i));
                i += 1;
            }
        }
        r.add(sb.toString());
        return r;
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
            List<DateFormat> possibleDateFormats = new ArrayList<>(ToDate.FORMATS.stream().<DateTimeFormatter>flatMap(List::stream).map(formatter -> new DateFormat(formatter, LocalDate::from)).collect(Collectors.<DateFormat>toList()));
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
                    String val = columnIndex < row.size() ? row.get(columnIndex).trim() : "";
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
                            //break;
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
                columnTypes.add(new CleanDateColumnType(possibleDateFormats.get(0).formatter, possibleDateFormats.get(0).destQuery));
            else if (allNumeric)
            {
                columnTypes.add(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix));
            }
            else if (allNumericOrBlank)
                columnTypes.add(new OrBlankColumnType(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix)));
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
        int nonBlankColumnCount = (int)columnTypes.stream().filter(c -> !(c instanceof BlankColumnType)).count();
        // All must think it's viable, and then pick last one:
        Optional<List<String>> headerRow = viableColumnNameRows.entrySet().stream().filter(e -> e.getValue() == nonBlankColumnCount || e.getValue() == columnTypes.size()).max(Entry.comparingByKey()).map(e -> initialVals.get(e.getKey()));

        List<ColumnInfo> columns = new ArrayList<>(columnCount);
        for (int columnIndex = 0; columnIndex < columnTypes.size(); columnIndex++)
            columns.add(new ColumnInfo(columnTypes.get(columnIndex), new ColumnId(headerRow.isPresent() && columnIndex < headerRow.get().size() ? headerRow.get().get(columnIndex) : ("C" + (columnIndex + 1)))));
        return new Format(headerRows, columns);
    }

    @OnThread(Tag.Simulation)
    public static void guessTextFormatGUI_Then(UnitManager mgr, Map<Charset, List<String>> initial, Consumer<TextFormat> then)
    {
        ChoicePoint<?, TextFormat> choicePoints = guessTextFormat(mgr, initial);
        Platform.runLater(() ->
        {
            Stage s = new Stage();
            TextArea textView = new TextArea();
            textView.setEditable(false);
            TableView<List<String>> tableView = new TableView<>();
            Node choices;
            try
            {
                @Nullable Stream<Choice> bestGuess = findBestGuess(choicePoints);
                choices = makeGUI(choicePoints, bestGuess == null ? Collections.emptyList() : bestGuess.collect(Collectors.<@NonNull Choice>toList()), initial, textView, tableView);
            }
            catch (InternalException e)
            {
                Utility.log(e);
                choices = new Label("Internal error: " + e.getLocalizedMessage());
            }

            s.setScene(new Scene(new VBox(choices, new SplitPane(textView, tableView))));
            s.show();
        });
        System.err.println(choicePoints);
        // TODO show GUI, apply them
        // TODO include choice of link or copy.
    }

    // Null means not viable, empty stream means no more choices to make
    private static <C extends Choice> @Nullable Stream<Choice> findBestGuess(ChoicePoint<C, TextFormat> choicePoints)
    {
        if (choicePoints.getOptions().isEmpty())
            return Stream.empty();
        else
        {
            // At the moment, we just try first and if that gives promising all the way, we take it:
            for (C choice : choicePoints.getOptions())
            {
                try
                {
                    ChoicePoint<?, TextFormat> next = choicePoints.select(choice);
                    if (next.getQuality() == Quality.PROMISING)
                    {
                        // Keep going!
                        Stream<Choice> inner = findBestGuess(next);
                        if (inner != null)
                        {
                            return Stream.concat(Stream.of(choice), inner);
                        }
                        // Otherwise try next choice
                    }
                }
                catch (InternalException e)
                {
                    // Go to next
                }
            }
            // No options found:
            return null;
        }
    }

    @OnThread(Tag.FXPlatform)
    private static <C extends Choice> Node makeGUI(ChoicePoint<C, TextFormat> rawChoicePoint, List<Choice> mostRecentPick, Map<Charset, List<String>> initial, TextArea textView, TableView<List<String>> tableView) throws InternalException
    {
        final @Nullable ChoiceType<C> choiceType = rawChoicePoint.getChoiceType();
        // TODO drill into consecutive choices afterwards
        // TODO also show a preview of what it will import!
        if (choiceType == null)
        {
            try
            {
                TextFormat t = rawChoicePoint.get();
                List<String> initialLines = initial.get(t.charset);
                if (initialLines == null)
                    throw new InternalException("Charset pick gives no initial lines");
                previewFormat(t, initialLines, textView, tableView);
            }
            catch (UserException e)
            {
                tableView.setPlaceholder(new Label("Problem: " + e.getLocalizedMessage()));
            }
            return new Label("END");
        }
        // Default handling:
        Node choiceNode;
        ObjectExpression<C> choiceExpression;

        List<C> options = rawChoicePoint.getOptions();
        if (options.size() == 1)
        {
            choiceNode = new Label(options.get(0).toString());
            choiceExpression = new ReadOnlyObjectWrapper<>(options.get(0));
        }
        else
        {
            ComboBox<C> combo = new ComboBox<>(FXCollections.observableArrayList(options));
            @Nullable C choice = findByClass(mostRecentPick, choiceType.getChoiceClass());
            if (choice == null || !combo.getItems().contains(choice))
                combo.getSelectionModel().selectFirst();
            else
                combo.getSelectionModel().select(choice);
            choiceNode = combo;
            choiceExpression = combo.getSelectionModel().selectedItemProperty();
        }
        VBox vbox = new VBox(GUI.labelled(choiceType.getLabelKey(), choiceType.getHelpId(), choiceNode));
        FXPlatformConsumer<C> pick = item -> {
            try
            {
                ChoicePoint<?, TextFormat> next = rawChoicePoint.select(item);
                Node gui = makeGUI(next, mostRecentPick, initial, textView, tableView);
                if (vbox.getChildren().size() == 1)
                {
                    vbox.getChildren().add(gui);
                }
                else
                {
                    vbox.getChildren().set(1, gui);
                }
            }
            catch (InternalException e)
            {
                Utility.log(e);
                tableView.getColumns().clear();
                tableView.setPlaceholder(new Label("Error: " + e.getLocalizedMessage()));
            }
        };
        pick.consume(choiceExpression.get());
        FXUtility.addChangeListenerPlatformNN(choiceExpression, pick);
        return vbox;
    }

    // There should only be one item per class in the list
    private static <C extends Choice> @Nullable C findByClass(List<Choice> choiceItems, Class<? extends C> targetClass)
    {
        for (Choice c : choiceItems)
        {
            if (targetClass.isInstance(c))
            {
                return (C) c;
            }
        }
        return null;
    }

    @OnThread(Tag.FXPlatform)
    private static void previewFormat(TextFormat t, List<String> initial, TextArea textArea, TableView<List<String>> tableView)
    {
        textArea.setText(initial.stream().collect(Collectors.joining("\n")));

        tableView.getItems().clear();
        tableView.getColumns().clear();

        List<ColumnInfo> columnTypes = t.columnTypes;
        for (int column = 0; column < columnTypes.size(); column++)
        {
            ColumnInfo columnType = columnTypes.get(column);
            TableColumn<List<String>, String> col = new TableColumn<>(columnType.title + ":" + columnType.type);
            int columnFinal = column;
            col.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().size() <= columnFinal ? "<Missing>" : c.getValue().get(columnFinal)));
            tableView.getColumns().add(col);
        }

        for (int row = t.headerRows; row < initial.size(); row++)
        {
            tableView.getItems().add(t.separator == null ? Collections.singletonList(initial.get(row)) : splitIntoColumns(initial.get(row), new SeparatorChoice(t.separator), new QuoteChoice(t.quote)));
        }
    }
}
