package records.gui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import org.fxmisc.undo.UndoManagerFactory;
import records.data.Column;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.NumberDisplayInfo;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.RunOrError;
import utility.Workers;
import utility.gui.FXUtility;
import utility.gui.stable.StableView.ValueFetcher;

import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 01/05/2017.
 */
public class TableDisplayUtility
{
    private static class ValidationResult
    {
        private final String newReplacement;
        private final @Nullable @Localized String error;
        private final RunOrError storer;

        private ValidationResult(String newReplacement, @Nullable @Localized String error, RunOrError storer)
        {
            this.newReplacement = newReplacement;
            this.error = error;
            this.storer = storer;
        }
    }

    private static interface StringInputValidator
    {
        /**
         *
         * @param rowIndex The row index of the item (mainly needed for storing it)
         * @param before The untouched part of the String before the altered part
         * @param oldPart The old value of the altered part of the String
         * @param newPart The new value of the altered part of the String
         * @param end The untouched part of the String after the altered part
         * @return The value of oldPart/newPart to use.  Return oldPart if you want no change.
         */
        @OnThread(Tag.FXPlatform)
        public ValidationResult validate(int rowIndex, String before, String oldPart, String newPart, String end); // Or should this work on change?
    }

    private static ValidationResult result(String newReplacement, @Nullable @Localized  String error, RunOrError storer)
    {
        return new ValidationResult(newReplacement, error, storer);
    }

    public static List<Pair<String, ValueFetcher>> makeStableViewColumns(RecordSet recordSet)
    {
        return Utility.mapList(recordSet.getColumns(), col -> {
            try
            {
                return getDisplay(col);
            }
            catch (InternalException | UserException e)
            {
                return new Pair<>(col.getName().getRaw(), (rowIndex, receiver, first, last) -> {
                    receiver.setValue(rowIndex, new Label("Error: " + e.getLocalizedMessage()));
                });
            }
        });
    }

    private static Pair<String, ValueFetcher> getDisplay(@NonNull Column column) throws UserException, InternalException
    {
        return new Pair<>(column.getName().getRaw(), column.getType().applyGet(new DataTypeVisitorGet<ValueFetcher>()
        {
            @Override
            public ValueFetcher number(GetValue<Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                class NumberDisplay
                {
                    private final StyleClassedTextArea textArea;

                    @OnThread(Tag.FXPlatform)
                    public NumberDisplay(int rowIndex, Number n)
                    {
                        StringInputValidator validator = getNumericValidator(column, g);
                        textArea = new StyleClassedTextArea(false) // plain undo manager
                        {
                            private String valueBeforeFocus = "";
                            private Utility.@Nullable RunOrError storeAction = null;

                            {
                                FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused ->
                                {
                                    if (focused)
                                    {
                                        valueBeforeFocus = getText();
                                    } else
                                    {
                                        if (storeAction != null)
                                        {
                                            Utility.@Initialized @NonNull RunOrError storeActionFinal = storeAction;
                                            Workers.onWorkerThread("Storing value " + getText(), Workers.Priority.SAVE_ENTRY, () -> Utility.alertOnError_(storeActionFinal));
                                        }
                                    }
                                });
                            }

                            @Override
                            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                            public void replaceText(int start, int end, String text)
                            {
                                String old = getText();
                                TableDisplayUtility.ValidationResult result = validator.validate(rowIndex, old.substring(0, start), old.substring(start, end), text, old.substring(end));
                                this.storeAction = result.storer;
                                super.replaceText(start, end, result.newReplacement);
                                //TODO sort out any restyling needed
                                // TODO show error
                            }
                        };
                        textArea.setEditable(column.isEditable());
                        textArea.setUseInitialStyleForInsertion(false);
                        textArea.setUndoManager(UndoManagerFactory.fixedSizeHistoryFactory(3));

                        @Nullable NumberDisplayInfo ndi = displayInfo.getDisplayInfo();
                        if (ndi == null)
                            ndi = NumberDisplayInfo.SYSTEMWIDE_DEFAULT; // TODO use file-wide default
                        String fracPart = Utility.getFracPartAsString(n, ndi.getMinimumDP(), ndi.getMaximumDP());
                        fracPart = fracPart.isEmpty() ? "" : "." + fracPart;
                        textArea.replace(docFromSegments(
                            new StyledText<>(Utility.getIntegerPart(n).toString(), Arrays.asList("number-display-int")),
                            new StyledText<>(fracPart, Arrays.asList("number-display-frac"))
                        ));
                        textArea.getStyleClass().add("number-display");
                    }
                }

                return new DisplayCache<Number, NumberDisplay>(g, null, p -> new NumberDisplay(p.getFirst(), p.getSecond()), n -> n.textArea);
            }

            @Override
            public ValueFetcher text(GetValue<String> g) throws InternalException, UserException
            {
                class StringDisplay extends StackPane
                {
                    private final Label label;

                    public StringDisplay(String value)
                    {
                        Label beginQuote = new Label("\u201C");
                        Label endQuote = new Label("\u201D");
                        beginQuote.getStyleClass().add("string-display-quote");
                        endQuote.getStyleClass().add("string-display-quote");
                        StackPane.setAlignment(beginQuote, Pos.TOP_LEFT);
                        StackPane.setAlignment(endQuote, Pos.TOP_RIGHT);
                        //StackPane.setMargin(beginQuote, new Insets(0, 0, 0, 3));
                        //StackPane.setMargin(endQuote, new Insets(0, 3, 0, 0));
                        label = new Label(value);
                        label.setTextOverrun(OverrunStyle.CLIP);
                        getChildren().addAll(beginQuote, label); //endQuote, label);
                        // TODO allow editing, and call column.modified when it happens
                    }
                }

                return new DisplayCache<String, StringDisplay>(g, null, p -> new StringDisplay(p.getSecond()), s -> s);
            }

            @Override
            public ValueFetcher bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ValueFetcher date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ValueFetcher tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ValueFetcher tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ValueFetcher array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }
        }));
    }

    @OnThread(Tag.Any)
    private static StringInputValidator getNumericValidator(Column column, GetValue<Number> g)
    {

        return (rowIndex, before, oldPart, newPart, end) -> {
            String altered = newPart.replaceAll("[^0-9.+-]", "");
            // We also disallow + and - except at start, and only allow one dot:
            if (before.contains(".") || end.contains("."))
                altered = altered.replace(".", "");
            if (before.isEmpty())
            {
                // + or - would be allowed at the start
            }
            else
            {
                altered = altered.replace("[+-]","");
            }
            // Check it is actually valid as a number:
            @Nullable Number n;
            @Nullable @Localized String error = null;
            try
            {
                n = Utility.parseNumber(before + altered + end);
            }
            catch (UserException e)
            {
                error = e.getLocalizedMessage();
                n = null;
            }
            @Nullable Number nFinal = n;
            return result(altered, error, () -> {
                if (nFinal != null)
                {
                    g.set(rowIndex, nFinal);
                    column.modified();
                }
            });
        };
    }
/*
    @OnThread(Tag.FXPlatform)
    private static Region getNode(DisplayValue item, @Nullable StringInputValidator validator)
    {
        if (item.getNumber() != null)
        {
            @NonNull Number n = item.getNumber();
            StyleClassedTextArea textArea = new StyleClassedTextArea(false) // plain undo manager
            {
                private String valueBeforeFocus = "";
                private @Nullable RunOrError storeAction = null;

                {
                    FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
                        if (focused)
                        {
                            valueBeforeFocus = getText();
                        }
                        else
                        {
                            if (storeAction != null)
                            {
                                @Initialized @NonNull RunOrError storeActionFinal = storeAction;
                                Workers.onWorkerThread("Storing value " + getText(), Workers.Priority.SAVE_ENTRY, () -> Utility.alertOnError_(storeActionFinal));
                            }
                        }
                    });
                }

                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public void replaceText(int start, int end, String text)
                {
                    String old = getText();
                    if (validator != null)
                    {
                        @OnThread(Tag.FXPlatform) ValidationResult result = validator.validate(item.getRowIndex(), old.substring(0, start), old.substring(start, end), text, old.substring(end));
                        this.storeAction = result.storer;
                        super.replaceText(start, end, result.newReplacement);
                        //TODO sort out any restyling needed
                        // TODO show error
                    }

                }
            };
            textArea.setEditable(validator != null);
            textArea.setUseInitialStyleForInsertion(false);
            textArea.setUndoManager(UndoManagerFactory.fixedSizeHistoryFactory(3));

            @Nullable NumberDisplayInfo ndi = item.getNumberDisplayInfo();
            if (ndi == null)
                ndi = NumberDisplayInfo.SYSTEMWIDE_DEFAULT; // TODO use file-wide default
            String fracPart = Utility.getFracPartAsString(n, ndi.getMinimumDP(), ndi.getMaximumDP());
            fracPart = fracPart.isEmpty() ? "" : "." + fracPart;
            textArea.replace(docFromSegments(
                new StyledText<>(Utility.getIntegerPart(n).toString(), Arrays.asList("number-display-int")),
                new StyledText<>(fracPart, Arrays.asList("number-display-frac"))
            ));
            textArea.getStyleClass().add("number-display");
            return textArea;
        }
        else
        {
            StackPane stringWrapper = new StackPane();
            Label beginQuote = new Label("\u201C");
            Label endQuote = new Label("\u201D");
            beginQuote.getStyleClass().add("string-display-quote");
            endQuote.getStyleClass().add("string-display-quote");
            StackPane.setAlignment(beginQuote, Pos.TOP_LEFT);
            StackPane.setAlignment(endQuote, Pos.TOP_RIGHT);
            //StackPane.setMargin(beginQuote, new Insets(0, 0, 0, 3));
            //StackPane.setMargin(endQuote, new Insets(0, 3, 0, 0));
            Label label = new Label(item.toString());
            label.setTextOverrun(OverrunStyle.CLIP);
            stringWrapper.getChildren().addAll(beginQuote, label); //endQuote, label);
            return stringWrapper;
        }
    }
    */

    private static StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> docFromSegments(StyledText<Collection<String>>... segments)
    {
        ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment((StyledText<Collection<String>>)segments[0], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps());
        for (int i = 1; i < segments.length; i++)
        {
            doc = doc.concat(ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment(segments[i], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps()));
        }
        return doc;
    }
}
