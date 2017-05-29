package records.importers;

import com.google.common.base.Objects;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyledText;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.TableManager;
import records.data.TextFileColumn.TextFileColumnListener;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.TableDisplayUtility;
import records.gui.TableNameTextField;
import records.importers.ChoicePoint.Choice;
import records.importers.ChoicePoint.ChoiceType;
import records.importers.ChoicePoint.Quality;
import records.transformations.function.ToDate;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.Utility.IndexRange;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LabelledGrid.Row;
import utility.gui.SegmentedButtonValue;
import records.gui.stable.StableView;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 20/10/2016.
 */
public class GuessFormat
{
    public static final int MAX_HEADER_ROWS = 5;
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
            return new ChoiceType<>(CharsetChoice.class,"guess.charset", "guess-format/charset");
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
            return new ChoiceType<>(HeaderRowChoice.class, "guess.headerRow", "guess-format/headerRow");
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
                return "<Tab (\u27FE)>";
            return separator;
        }

        public static ChoiceType<SeparatorChoice> getType()
        {
            return new ChoiceType<>(SeparatorChoice.class, "guess.separator", "guess-format/separator");
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
            return new ChoiceType<>(QuoteChoice.class, "guess.quote", "guess-format/quote");
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
            return new ChoiceType<>(ColumnCountChoice.class, "guess.columnCount", "guess-format/columnCount");
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
                            counts.add(splitIntoColumns(initial.get(i), sep, quot).columnContents.size());
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
                        List<@NonNull List<@NonNull String>> initialVals = Utility.<@NonNull String, @NonNull List<@NonNull String>>mapList(initial, s -> splitIntoColumns(s, sep, quot).columnContents);
                        Format format = guessBodyFormat(mgr, cc.columnCount, hrc.numHeaderRows, initialVals);
                        TextFormat textFormat = new TextFormat(format, sep.separator, quot.quote, chc.charset);
                        double proportionNonText = (double)textFormat.columnTypes.stream().filter(c -> !(c.type instanceof TextColumnType) && !(c.type instanceof BlankColumnType)).count() / (double)textFormat.columnTypes.size();
                        return ChoicePoint.<TextFormat>success(proportionNonText > 0 ? Quality.PROMISING : Quality.FALLBACK, proportionNonText, textFormat);
                    }, viableColumnCounts.toArray(new ColumnCountChoice[0]));
                }, quot(null), quot("\""), quot("\'"))
                , sep(";"), sep(","), sep("\t"), sep(":"), sep(" "))
                , headerRowChoices.toArray(new HeaderRowChoice[0]));
        }, initialByCharset.keySet().stream().<@NonNull CharsetChoice>map(CharsetChoice::new).collect(Collectors.<@NonNull CharsetChoice>toList()).<@NonNull CharsetChoice>toArray(new @NonNull CharsetChoice[0]));
    }

    private static class RowInfo
    {
        // Each item is one column's content on this row
        private final List<String> columnContents = new ArrayList<>();
        // Each pair is (content, style)
        private final List<Pair<String, String>> originalContentAndStyle = new ArrayList<>();

    }

    // Split a row of text into columns, given a separator and a quote character
    private static RowInfo splitIntoColumns(String row, SeparatorChoice sep, QuoteChoice quot)
    {
        boolean inQuoted = false;
        StringBuilder sb = new StringBuilder();
        RowInfo r = new RowInfo();
        for (int i = 0; i < row.length();)
        {
            // First check for escaped quote (which may otherwise look like a quote):
            if (inQuoted && quot.escapedQuote != null && row.startsWith(quot.escapedQuote, i))
            {
                // Skip it:
                sb.append(quot.quote);
                i += quot.escapedQuote.length();

                if (quot.quote != null && quot.escapedQuote.endsWith(quot.quote))
                {
                    r.originalContentAndStyle.add(new Pair<>(quot.escapedQuote.substring(0, quot.escapedQuote.length() - quot.quote.length()), "escaped-quote-escape"));
                    r.originalContentAndStyle.add(new Pair<>(quot.quote, "escaped-quote-quote"));
                }
                else
                {
                    r.originalContentAndStyle.add(new Pair<>(quot.escapedQuote, "escaped-quote"));
                }
            }
            else if (quot.quote != null && row.startsWith(quot.quote, i) && (inQuoted || sb.toString().trim().isEmpty()))
            {
                if (!inQuoted)
                {
                    // Ignore the spaces beforehand:
                    sb = new StringBuilder();
                }
                inQuoted = !inQuoted;
                i += quot.quote.length();
                r.originalContentAndStyle.add(new Pair<>(quot.quote, inQuoted ? "quote-begin" : "quote-end"));
            }
            else if (!inQuoted && sep.separator != null && row.startsWith(sep.separator, i))
            {
                r.columnContents.add(sb.toString());
                r.originalContentAndStyle.add(new Pair<>(replaceTab(sep.separator), "separator"));
                sb = new StringBuilder();
                i += sep.separator.length();
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
        return s.replace("\t", "\u27FE");
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
            // The "blank", which may be empty string, or might be another value (e.g. "NA")
            String numericBlank = null;
            // Only false if we find content which is not parseable as a number:
            boolean allNumericOrBlank = true;
            boolean allBlank = true;
            List<DateFormat> possibleDateFormats = new ArrayList<>(ToDate.FORMATS.stream().<DateTimeFormatter>flatMap(List::stream).map(formatter -> new DateFormat(formatter, LocalDate::from)).collect(Collectors.<DateFormat>toList()));
            @Nullable String commonPrefix = null;
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

                        if (commonPrefix == null)
                        {
                            // Look for a prefix of currency symbol:
                            for (int i = 0; i < val.length(); i = val.offsetByCodePoints(i, 1))
                            {
                                if (Character.getType(val.codePointAt(i)) == Character.CURRENCY_SYMBOL)
                                {
                                    commonPrefix = (commonPrefix == null ? "" : commonPrefix) + val.substring(i, val.offsetByCodePoints(i, 1));
                                }
                                else
                                    break;
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
            else if (allNumericOrBlank && numericBlank != null)
            {
                columnTypes.add(new OrBlankColumnType(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix), numericBlank));
            }
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
        Optional<List<String>> headerRow = viableColumnNameRows.entrySet().stream()
            //.filter(e -> e.getValue() == nonBlankColumnCount || e.getValue() == columnTypes.size())
            .max(Entry.comparingByKey()).map(e -> initialVals.get(e.getKey()));

        List<ColumnInfo> columns = new ArrayList<>(columnCount);
        for (int columnIndex = 0; columnIndex < columnTypes.size(); columnIndex++)
            columns.add(new ColumnInfo(columnTypes.get(columnIndex), new ColumnId(headerRow.isPresent() && columnIndex < headerRow.get().size() ? headerRow.get().get(columnIndex) : ("C" + (columnIndex + 1)))));
        return new Format(headerRows, columns);
    }

    public static class ImportInfo
    {
        public final TableId tableName;
        public final boolean linkFile;

        public ImportInfo(TableId tableName, boolean linkFile)
        {
            this.tableName = tableName;
            this.linkFile = linkFile;
        }
    }

    @OnThread(Tag.Simulation)
    public static void guessTextFormatGUI_Then(TableManager mgr, File file, String suggestedName, Map<Charset, List<String>> initial, Consumer<Pair<ImportInfo, TextFormat>> then)
    {
        ChoicePoint<?, TextFormat> choicePoints = guessTextFormat(mgr.getUnitManager(), initial);
        Platform.runLater(() ->
        {
            Dialog<Pair<ImportInfo, TextFormat>> dialog = new Dialog<>();
            StyleClassedTextArea sourceFileView = new StyleClassedTextArea();
            sourceFileView.getStyleClass().addAll("source", "stable-view-matched");
            sourceFileView.setEditable(false);
            StableView tableView = new StableView();
            AtomicInteger updateCounter = new AtomicInteger(0);
            FXUtility.addChangeListenerPlatformNN(tableView.topShowingCell(), topShowing -> {
                System.err.println("Top: " + topShowing);
                sourceFileView.showParagraphAtTop(topShowing.getFirst());
                // We want to adjust offset after layout pass, but not if we've since
                // scrolled again, so we keep track in order to match offset adjustment
                // to the original scroll request:
                int updateCount = updateCounter.incrementAndGet();
                FXUtility.runAfter(() -> FXUtility.runAfter(() -> {
                    if (updateCounter.get() == updateCount)
                        sourceFileView.scrollBy(new Point2D(0, 0 - topShowing.getSecond() - tableView.topHeightProperty().get()));
                }));
            });


            LabelledGrid choices = new LabelledGrid();
            choices.getStyleClass().add("choice-grid");
            TableNameTextField nameField = new TableNameTextField(mgr);
            nameField.setText(suggestedName);
            SegmentedButtonValue<Boolean> linkCopyButtons = new SegmentedButtonValue<>(new Pair<@LocalizableKey String, Boolean>("table.copy", false), new Pair<@LocalizableKey String, Boolean>("table.link", true));
            choices.addRow(GUI.labelledGridRow("table.name", "guess-format/tableName", nameField.getNode()));
            choices.addRow(GUI.labelledGridRow("table.linkCopy", "guess-format/linkCopy", linkCopyButtons));

            SimpleObjectProperty<@Nullable TextFormat> formatProperty = new SimpleObjectProperty<>(null);
            try
            {
                @Nullable Stream<Choice> bestGuess = findBestGuess(choicePoints);

                makeGUI(mgr.getTypeManager(), choicePoints, bestGuess == null ? Collections.emptyList() : bestGuess.collect(Collectors.<@NonNull Choice>toList()), file, initial, choices, sourceFileView, tableView, formatProperty);
            }
            catch (InternalException e)
            {
                Utility.log(e);
                choices.addRow(new Row(new Label("Internal error: "), null, new TextFlow(new Text(e.getLocalizedMessage()))));
            }


            SplitPane splitPane = new SplitPane(sourceFileView, tableView.getNode());
            Pane content = new BorderPane(splitPane, choices, null, null, null);
            content.getStyleClass().add("guess-format-content");
            dialog.getDialogPane().getStylesheets().addAll(FXUtility.getSceneStylesheets("guess-format"));
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
            // Prevent enter/escape activating buttons:
            ((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setCancelButton(false);
            ((Button)dialog.getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
            //TODO disable ok button if name isn't valid
            dialog.setResultConverter(bt -> {
                @Nullable TableId tableId = nameField.valueProperty().get();
                @Nullable TextFormat textFormat = formatProperty.get();
                if (bt == ButtonType.OK && tableId != null && textFormat != null)
                {
                    return new Pair<>(new ImportInfo(tableId, linkCopyButtons.valueProperty().get()), textFormat);
                }
                return null;
            });
            dialog.setResizable(true);

            //dialog.initModality(Modality.NONE); // For scenic view
            dialog.setOnShown(e -> {
                //org.scenicview.ScenicView.show(dialog.getDialogPane().getScene());

                // Have to use runAfter because the OK button gets focused
                // so we have to wait, then steal the focus back:
                FXUtility.runAfter(() -> nameField.requestFocusWhenInScene());
            });
            dialog.showAndWait().ifPresent(then);

        });
        System.err.println(choicePoints);

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
    private static <C extends Choice> void makeGUI(TypeManager typeManager, ChoicePoint<C, TextFormat> rawChoicePoint, List<Choice> mostRecentPick, File file, Map<Charset, List<String>> initial, LabelledGrid controlGrid, StyleClassedTextArea textArea, StableView tableView, ObjectProperty<@Nullable TextFormat> destProperty) throws InternalException
    {
        final @Nullable ChoiceType<C> choiceType = rawChoicePoint.getChoiceType();
        if (choiceType == null)
        {
            try
            {
                TextFormat t = rawChoicePoint.get();
                List<String> initialLines = initial.get(t.charset);
                if (initialLines == null)
                    throw new InternalException("Charset pick gives no initial lines");
                destProperty.set(t);
                previewFormat(typeManager, file, initialLines, t, new GUI_Items(textArea, tableView, t.headerRows));
            }
            catch (UserException e)
            {
                tableView.setPlaceholderText(e.getLocalizedMessage());
            }
            return;
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
            ComboBox<C> combo = GUI.comboBoxStyled(FXCollections.observableArrayList(options));
            @Nullable C choice = findByClass(mostRecentPick, choiceType.getChoiceClass());
            if (choice == null || !combo.getItems().contains(choice))
                combo.getSelectionModel().selectFirst();
            else
                combo.getSelectionModel().select(choice);
            choiceNode = combo;
            choiceExpression = combo.getSelectionModel().selectedItemProperty();
        }
        int rowNumber = controlGrid.addRow(GUI.labelledGridRow(choiceType.getLabelKey(), choiceType.getHelpId(), choiceNode));
        FXPlatformConsumer<C> pick = item -> {
            try
            {
                ChoicePoint<?, TextFormat> next = rawChoicePoint.select(item);
                controlGrid.clearRowsAfter(rowNumber);
                makeGUI(typeManager, next, mostRecentPick, file, initial, controlGrid, textArea, tableView, destProperty);
            }
            catch (InternalException e)
            {
                Utility.log(e);
                tableView.clear(null);
                tableView.setPlaceholderText(e.getLocalizedMessage());
            }
        };
        pick.consume(choiceExpression.get());
        FXUtility.addChangeListenerPlatformNN(choiceExpression, pick);
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

    private static class GUI_Items implements ChangeListener<Pair<Integer, Integer>>
    {
        public final StyleClassedTextArea textArea;
        public final StableView tableView;
        public final int numHeaderRows;
        // This structure needs to store data proportional to the size of the text file, so we
        // use a large flat array.  It's size is columns*rows*2, where
        // rowIndex*columns+columnIndex*2 pinpoints two integers: beginning and end of used range
        // we store rows together to plan for case where number of rows may not be known upfront.
        private int[] usedRanges;

        @OnThread(Tag.FXPlatform)
        @SuppressWarnings("initialization") // For passing this as change listener
        private GUI_Items(StyleClassedTextArea textArea, StableView tableView, int numHeaderRows)
        {
            this.textArea = textArea;
            this.tableView = tableView;
            this.numHeaderRows = numHeaderRows;
            this.usedRanges = new int[100000];
            Arrays.fill(usedRanges, -1);
            tableView.focusedCellProperty().addListener(this);
        }


        @OnThread(Tag.FXPlatform)
        public void setUsedRange(int columnIndex, int rowIndex, int start, int end)
        {
            int usedIndex = (rowIndex*tableView.getColumnCount() + columnIndex) * 2;
            // Poor man's array list:
            if (usedRanges.length <= usedIndex)
            {
                int oldLength = usedRanges.length;
                usedRanges = Arrays.copyOf(usedRanges, usedIndex + 2 + (usedRanges.length / 4));
                Arrays.fill(usedRanges, oldLength, usedRanges.length, -1);
            }
            usedRanges[usedIndex + 0] = start;
            usedRanges[usedIndex + 1] = end;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends Pair<Integer, Integer>> prop, Pair<Integer, Integer> oldFocus, Pair<Integer, Integer> newFocus)
        {
            if (oldFocus != null)
            {
                int usedIndex = (oldFocus.getSecond()*tableView.getColumnCount() + oldFocus.getFirst()) * 2;
                if (usedRanges[usedIndex] != -1)
                    overlayStyle(textArea, numHeaderRows, oldFocus.getSecond(), new IndexRange(usedRanges[usedIndex], usedRanges[usedIndex+1]), "selected-cell", Sets::difference);
            }
            if (newFocus != null)
            {
                int usedIndex = (newFocus.getSecond()*tableView.getColumnCount() + newFocus.getFirst()) * 2;
                if (usedRanges[usedIndex] != -1)
                    overlayStyle(textArea, numHeaderRows, newFocus.getSecond(), new IndexRange(usedRanges[usedIndex], usedRanges[usedIndex+1]), "selected-cell", Sets::union);
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    private static void previewFormat(TypeManager typeManager, File file, List<String> initialLines, TextFormat t, GUI_Items gui)
    {
        gui.textArea.replaceText(initialLines.stream().collect(Collectors.joining("\n")));
        gui.tableView.clear(null);
        TextAreaFiller textAreaFiller = new TextAreaFiller(gui);

        gui.textArea.setParagraphGraphicFactory(sourceLine -> {
            Label label = new Label(Integer.toString(sourceLine - t.headerRows));
            label.getStyleClass().add("line-number");
            return label;
        });

        Workers.onWorkerThread("Loading" + file.getName(), Priority.LOAD_FROM_DISK, () -> {
            try
            {
                @OnThread(Tag.Simulation) RecordSet recordSet = TextImport.makeRecordSet(typeManager, file, t, textAreaFiller);
                Platform.runLater(() -> {
                    gui.tableView.setColumns(TableDisplayUtility.makeStableViewColumns(recordSet), null);
                    gui.tableView.setRows(recordSet::indexValid);
                });
            }
            catch (IOException | InternalException | UserException e)
            {
                Utility.log(e);
                Platform.runLater(() -> gui.tableView.setPlaceholderText(e.getLocalizedMessage()));

            }
        });

        // TODO put preview back by adding listeners to import process
        /*
        for (int row = t.headerRows; row < initial.size(); row++)
        {
            if (t.separator == null)
            {
                // TODO: is quoting still valid if there's only one column?
                tableView.getItems().add(Collections.singletonList(initial.get(row)));
                textArea.appendText(initial.get(row));
            }
            else
            {
                RowInfo split = splitIntoColumns(initial.get(row), new SeparatorChoice(t.separator), new QuoteChoice(t.quote));
                tableView.getItems().add(split.columnContents);
                ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = ReadOnlyStyledDocument.fromString("", Collections.<@NonNull String>emptyList(), Collections.<@NonNull String>emptyList(), StyledText.<Collection<@NonNull String>>textOps());
                for (int i = 0; i < split.originalContentAndStyle.size(); i++)
                {
                    doc = doc.concat(ReadOnlyStyledDocument.fromString(split.originalContentAndStyle.get(i).getFirst(), Collections.emptyList(), Collections.singletonList(split.originalContentAndStyle.get(i).getSecond()), StyledText.<Collection<@NonNull String>>textOps()));
                }
                textArea.append(doc);
                textArea.appendText("\n");
            }
        }*/
    }

    private static class TextAreaFiller implements TextFileColumnListener
    {
        private final GUI_Items gui;

        public TextAreaFiller(GUI_Items guiItems)
        {
            this.gui = guiItems;
        }

        @Override
        @OnThread(Tag.Simulation)
        public void usedLine(int rowIndex, int columnIndex, String line, IndexRange usedPortion)
        {
            Platform.runLater(() ->
            {
                while (gui.textArea.getParagraphs().size() <= rowIndex + gui.numHeaderRows)
                    gui.textArea.appendText("\n");
                Paragraph<Collection<String>, StyledText<Collection<String>>, Collection<String>> para = gui.textArea.getParagraphs().get(rowIndex + gui.numHeaderRows);
                if (para.getText().isEmpty())
                {
                    int pos = gui.textArea.getDocument().getAbsolutePosition(rowIndex + gui.numHeaderRows, 0);
                    gui.textArea.replaceText(pos, pos, replaceTab(line));
                }
                overlayStyle(gui.textArea, gui.numHeaderRows, rowIndex, usedPortion, "used", Sets::union);
                gui.setUsedRange(columnIndex, rowIndex, usedPortion.start, usedPortion.end);
            });
        }
    }


    @OnThread(Tag.FXPlatform)
    private static void overlayStyle(StyleClassedTextArea textArea, int numHeaderRows, int rowIndex, IndexRange usedPortion, String style, BiFunction<Set<String>, Set<String>, SetView<String>> setOp)
    {
        textArea.setStyleSpans(numHeaderRows + rowIndex, 0, textArea.getStyleSpans(numHeaderRows + rowIndex).overlay(
            StyleSpans.<Collection<String>>singleton(Collections.emptyList(), usedPortion.start).concat(StyleSpans.<Collection<String>>singleton(Collections.singletonList(style), usedPortion.getLength())),
            (a, b) -> setOp.apply(new HashSet<String>(a), new HashSet<String>(b)).immutableCopy())
        );
    }
}
