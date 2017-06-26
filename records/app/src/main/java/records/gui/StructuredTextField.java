package records.gui;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.*;
import org.jetbrains.annotations.NotNull;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Created by neil on 18/06/2017.
 */
@OnThread(Tag.FXPlatform)
public final class StructuredTextField<T> extends StyleClassedTextArea
{
    private final List<Item> curValue = new ArrayList<>();
    private final Component<T> contentComponent;
    private @Nullable State lastValidValue;
    private @Nullable PopOver fixPopup;
    private T completedValue;

    @SuppressWarnings("initialization")
    private StructuredTextField(Component<T> content) throws InternalException
    {
        super(false);
        this.contentComponent = content;
        List<Item> initialItems = content.getInitialItems();
        curValue.addAll(initialItems);


        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            if (!focused)
            {
                endEdit().either_(this::showFixPopup, v -> {
                    completedValue = v;
                    lastValidValue = captureState();
                });
            }
        });

        // Call super to avoid our own validation:
        super.replace(0, 0, makeDoc(initialItems));
        @Nullable T val = endEdit().<@Nullable T>either(err -> null, v -> v);
        if (val == null)
        {
            throw new InternalException("Starting field off with invalid completed value: " + Utility.listToString(Utility.mapList(initialItems, item -> item.getScreenText())));
        }
        completedValue = val;
        lastValidValue = captureState();
    }

    private @Nullable State captureState(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField<T> this)
    {
        if (curValue != null && completedValue != null)
        {
            @NonNull T val = completedValue;
            return new State(curValue, ReadOnlyStyledDocument.from(getDocument()), val);
        }
        else
            return null;
    }

    private void showFixPopup(List<ErrorFix> errorFixes)
    {
        hidePopup();
        PopOver popup = new PopOver();
        popup.getStyleClass().add("invalid-data-input-popup");
        List<Node> fixNodes = new ArrayList<>();
        fixNodes.add(GUI.label("entry.error"));
        for (int i = 0; i < errorFixes.size(); i++)
        {
            ErrorFix errorFix = errorFixes.get(i);
            Label label = new Label(errorFix.label);
            label.getStyleClass().add("invalid-data-fix");
            label.getStyleClass().addAll(errorFix.styleClasses);
            label.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                {
                    errorFix.performFix();
                    hidePopup();
                    e.consume();
                }
            });
            fixNodes.add(label);
        }

        popup.setContentNode(new VBox(fixNodes.toArray(new Node[0])));
        popup.setDetachable(false);
        popup.setAnimated(false);
        popup.setArrowLocation(ArrowLocation.BOTTOM_CENTER);
        fixPopup = popup;
        popup.show(this);
    }

    private void hidePopup(@UnknownInitialization(Object.class) StructuredTextField<T> this)
    {
        if (fixPopup != null)
        {
            fixPopup.hide();
            fixPopup = null;
        }
    }

    private static ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> makeDoc(List<Item> subItems)
    {
        @MonotonicNonNull ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = null;
        for (Item subItem : subItems)
        {
            ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> nextSeg = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment(subItem.toStyledText(), Collections.emptyList(), subItem.toStyles(), StyledText.<Collection<String>>textOps());
            doc = doc == null ? nextSeg : doc.concat(nextSeg);
        }
        if (doc != null)
        {
            return doc;
        }
        else
        {
            return ReadOnlyStyledDocument.fromString("", Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps());
        }
    }

    public static StructuredTextField<? extends TemporalAccessor> dateYMD(TemporalAccessor value) throws InternalException
    {
        return new StructuredTextField<>(new YMD(value));
    }

    public static StructuredTextField<? extends TemporalAccessor> dateTime(TemporalAccessor value) throws InternalException
    {
        return new StructuredTextField<>(new Component2<LocalDateTime, LocalDate, LocalTime>(new YMD(value), " ", new TimeComponent(value), LocalDateTime::of));
    }

    public static StructuredTextField<? extends TemporalAccessor> time(TemporalAccessor value) throws InternalException
    {
        return new StructuredTextField<>(new TimeComponent(value));
    }

    public static StructuredTextField<? extends TemporalAccessor> timeZoned(TemporalAccessor value) throws InternalException
    {
        return new StructuredTextField<>(new PlusMinusOffsetComponent<>(new TimeComponent(value), value.get(ChronoField.OFFSET_SECONDS), OffsetTime::of));
    }

    protected String getItem(ItemVariant item)
    {
        return curValue.stream().filter(ss -> ss.itemVariant == item).findFirst().map(ss -> ss.content).orElse("");
    }

    protected @Localized String getPrompt(ItemVariant item)
    {
        return curValue.stream().filter(ss -> ss.itemVariant == item).findFirst().map(ss -> ss.prompt).orElse("");
    }

    protected void setItem(ItemVariant item, String content)
    {
        curValue.replaceAll(old -> old.itemVariant == item ? new Item(content, item, old.prompt) : old);
        super.replace(0, getLength(), makeDoc(curValue));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void replace(final int start, final int end, StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> replacement)
    {
        hidePopup();

        List<Item> existing = new ArrayList<>(curValue);

        List<Item> newContent = new ArrayList<>();
        int[] next = replacement.getText().codePoints().toArray();
        // Need to zip together the spans, in effect.  But should never delete the boilerplate parts
        int curExisting = 0;
        int curStart = 0;
        String cur = "";
        // We want to copy across spans that end before us, and then for the one we are in, store the content
        // before us into the cur String.
        // e.g. given "17//" and start == 3, we want to copy day and divider, and set cur to ""
        //      given "17//" and start == 2, we want to copy day and set cur to "/"
        //      given "17//" and start == 1, we want to just set cur to "1"
        while (curStart <= start)
        {
            int spanEnd = curStart + existing.get(curExisting).getLength();
            // If we're just before divider, count as being at the beginning of it, not the end:
            if (start == spanEnd && (curExisting + 1 >= existing.size() || existing.get(curExisting + 1).itemVariant == ItemVariant.DIVIDER || existing.get(curExisting + 1).itemVariant == ItemVariant.TIMEZONE_PLUS_MINUS))
                break;

            if (spanEnd <= start)
            {
                newContent.add(new Item(existing.get(curExisting).content, existing.get(curExisting).itemVariant, existing.get(curExisting).prompt));
                curExisting += 1;
                curStart = spanEnd;
            }
            else
            {
                break;
            }
        }
        cur = getText(curStart, start);
        int replacementEnd = start;
        while (curExisting < existing.size())
        {
            // Find the next chunk of new text which could go here:
            int nextChar = 0;
            Item existingItem = existing.get(curExisting);
            ItemVariant curStyle = existingItem.itemVariant;
            String after = curStyle == ItemVariant.DIVIDER ?
                    existingItem.content :
                    existingItem.content.substring(Math.min(Math.max(0, end - curStart), existingItem.content.length()));
            while (nextChar < next.length || curExisting < existing.size())
            {
                CharEntryResult result = enterChar(curStyle, cur, after, nextChar < next.length ? OptionalInt.of(next[nextChar]) : OptionalInt.empty());
                cur = result.result;
                if (nextChar < next.length && result.charConsumed)
                {
                    nextChar += 1;
                    replacementEnd = curStart + cur.length();
                }
                if (result.moveToNext)
                    break;
            }
            newContent.add(new Item(cur, curStyle, existingItem.prompt));
            next = Arrays.copyOfRange(next, nextChar, next.length);
            curStart += existingItem.getLength();
            curExisting += 1;
            cur = "";
        }
        StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = makeDoc(newContent);
        super.replace(0, getLength(), doc);
        curValue.clear();
        curValue.addAll(newContent);
        selectRange(replacementEnd, replacementEnd);
    }

    protected Optional<ErrorFix> revertEditFix(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField<T> this)
    {
        if (lastValidValue == null)
            return Optional.empty();
        // Only needed out here for null checker:
        @NonNull State prev = lastValidValue;
        return Optional.of(new ErrorFix(TranslationUtility.getString("entry.fix.revert", prev.doc.getText()), "invalid-data-revert")
        {
            @Override
            @OnThread(Tag.FXPlatform)
            @RequiresNonNull("curValue")
            public void performFix()
            {
                if (prev != null)
                {
                    setState(prev);
                }
            }
        });
    }

    private static class TimeComponent implements Component<LocalTime>
    {
        private final TemporalAccessor value;

        public TimeComponent(TemporalAccessor value)
        {
            this.value = value;
        }

        @Override
        public List<Item> getInitialItems()
        {
            return Arrays.asList(
                new Item(Integer.toString(value.get(ChronoField.HOUR_OF_DAY)), ItemVariant.EDITABLE_HOUR, TranslationUtility.getString("entry.prompt.hour")),
                new Item(":"),
                new Item(Integer.toString(value.get(ChronoField.MINUTE_OF_HOUR)), ItemVariant.EDITABLE_MINUTE, TranslationUtility.getString("entry.prompt.minute")),
                new Item(":"),
                new Item(Integer.toString(value.get(ChronoField.SECOND_OF_MINUTE)), ItemVariant.EDITABLE_SECOND, TranslationUtility.getString("entry.prompt.second")));
        }

        @Override
        public Either<List<ErrorFix>, LocalTime> endEdit(StructuredTextField<?> field)
        {
            List<ErrorFix> fixes = new ArrayList<>();
            field.revertEditFix().ifPresent(fixes::add);
            try
            {
                int hour = Integer.parseInt(field.getItem(ItemVariant.EDITABLE_HOUR));
                int minute = Integer.parseInt(field.getItem(ItemVariant.EDITABLE_MINUTE));
                int second;
                int nano;
                String secondText = field.getItem(ItemVariant.EDITABLE_SECOND);
                if (secondText.contains("."))
                {
                    second = Integer.parseInt(secondText.substring(0, secondText.indexOf('.')));
                    String nanoText = secondText.substring(secondText.indexOf('.') + 1);
                    while (nanoText.length() < 9)
                        nanoText += "0";
                    nano = Integer.parseInt(nanoText);
                }
                else
                {
                    second = Integer.parseInt(secondText);
                    nano = 0;
                }

                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 && second >= 0 && second <= 59)
                {
                    return Either.right(LocalTime.of(hour, minute, second, nano));
                }
            }
            catch (NumberFormatException e)
            {

            }
            return Either.left(fixes);
        }
    }

    // Takes a component and adds {+|-}HH:MM on the end for a timezone offset.
    private static class PlusMinusOffsetComponent<R, A> implements Component<R>
    {
        private final int seconds;
        private final Component<A> a;
        private final BiFunction<A, ZoneOffset, R> combine;

        public PlusMinusOffsetComponent(Component<A> a, int seconds, BiFunction<A, ZoneOffset, R> combine)
        {
            this.a = a;
            this.seconds = seconds;
            this.combine = combine;
        }

        @Override
        public List<Item> getInitialItems()
        {
            int hours = seconds / 3600;
            int minutes = (seconds - (hours * 3600)) / 60;
            return Utility.concat(a.getInitialItems(), Arrays.asList(
                new Item("", ItemVariant.TIMEZONE_PLUS_MINUS, "\u00B1"),
                new Item(Integer.toString(hours), ItemVariant.EDITABLE_OFFSET_HOUR, "Zone Hours"),
                new Item(":"),
                new Item(Integer.toString(minutes), ItemVariant.EDITABLE_OFFSET_MINUTE, "Zone Minutes")));
        }

        @Override
        public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field)
        {
            Either<List<ErrorFix>, A> ea = a.endEdit(field);

            int sign = field.getItem(ItemVariant.TIMEZONE_PLUS_MINUS).equals("-") ? -1 : 1;

            int hour = sign * Integer.parseInt(field.getItem(ItemVariant.EDITABLE_OFFSET_HOUR));
            int minute = sign * Integer.parseInt(field.getItem(ItemVariant.EDITABLE_OFFSET_MINUTE));

            if (hour >= -18 && hour <= 18 && minute >= -59 && minute <= 59)
            {
                return ea.either(err -> Either.left(err), ra -> Either.right(combine.apply(ra, ZoneOffset.ofHoursMinutes(hour, minute))));
            }
            return Either.left(Collections.emptyList() /*TODO*/);
        }
    }

    private class State
    {
        public final List<Item> items;
        public final ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc;
        public final T value;

        public State(List<Item> items, ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc, T value)
        {
            this.items = new ArrayList<>(items);
            this.doc = doc;
            this.value = value;
        }
    }

    private void setState(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField<T> this, State valueBeforeFocus)
    {
        curValue.clear();
        curValue.addAll(valueBeforeFocus.items);
        // Call super to avoid our own validation:
        super.replace(0, getLength(), valueBeforeFocus.doc);
        completedValue = valueBeforeFocus.value;
    }

    // Intended for use by subclasses during fixes.  Applies content and ends edit
    protected void setValue(String content)
    {
        replaceText(content);
        endEdit().either_(err -> {}, v -> {
            completedValue = v;
            lastValidValue = captureState();
        });
    }

    private static abstract class ErrorFix
    {
        public final @Localized String label;
        public final List<String> styleClasses;

        protected ErrorFix(@Localized String label, String... styleClasses)
        {
            this.label = label;
            this.styleClasses = Arrays.asList(styleClasses);
        }

        @RequiresNonNull("curValue")
        public abstract void performFix();
    }

    /**
     * If return is null, successful.  Otherwise list is of alternatives for fixing the error (may be empty)
     * @return
     */
    public Either<List<ErrorFix>, T> endEdit()
    {
        return contentComponent.endEdit(this);
    }

    public T getCompletedValue()
    {
        return completedValue;
    }

    private static class CharEntryResult
    {
        public final String result;
        public final boolean charConsumed;
        public final boolean moveToNext;

        public CharEntryResult(String result, boolean charConsumed, boolean moveToNext)
        {
            this.result = result;
            this.charConsumed = charConsumed;
            this.moveToNext = moveToNext;
        }
    }

    private CharEntryResult enterChar(ItemVariant style, String before, String after, OptionalInt c)
    {
        if (style == ItemVariant.DIVIDER)
            return new CharEntryResult(after, true, true);

        String cStr = c.isPresent() ? new String(new int[] {c.getAsInt()}, 0, 1) : "";
        if (c.isPresent())
        {
            if (validCharacterForItem(style, before, c.getAsInt()))
            {
                if (before.length() < maxLength(style))
                    return new CharEntryResult(before + cStr, true, false);
                else
                    return new CharEntryResult(before, true, true);
            }
            return new CharEntryResult(before, false, true);
        }
        else
            return new CharEntryResult(before + after.trim(), true, true);

    }

    private boolean validCharacterForItem(ItemVariant style, String before, int c)
    {
        switch (style)
        {
            case TIMEZONE_PLUS_MINUS:
                return c == '+' || c == '-';
            case EDITABLE_HOUR:
            case EDITABLE_MINUTE:
            case EDITABLE_OFFSET_HOUR:
            case EDITABLE_OFFSET_MINUTE:
                return (c >= '0' && c <= '9');
            case EDITABLE_SECOND:
                return (c >= '0' && c <= '9') || c == '.';
            // Day, Month Year allow month names in any of them:
            default:
                return (c >= '0' && c <= '9') || Character.isAlphabetic(c);
        }
    }

    private int maxLength(ItemVariant style)
    {
        switch (style)
        {
            case EDITABLE_SECOND:
                return 2 + 1 + 9;
            default:
                return 12;
        }
    }

    /*
        private static EditableStyledDocument<Void, Item, ItemVariant> makeDocument(Item[] subItems)
        {
            #error TODO need to fill all this in
            return new EditableStyledDocument<Void, Item, ItemVariant>()
            {
                private final SimpleObjectProperty<Paragraph<Void, Item, ItemVariant>> para = new SimpleObjectProperty<>(new Paragraph<Void, Item, ItemVariant>(null, segmentOps(), subItems[0], Arrays.copyOfRange(subItems, 1, subItems.length)));
                private final LiveList<Paragraph<Void, Item, ItemVariant>> paras = LiveList.wrapVal(new ReadOnlyObjectWrapper<>(para));
                private final SimpleStringProperty stringContent = new SimpleStringProperty("");
                private final Val<Integer> length;

                {
                    length = Val.create(() -> stringContent.get().length());
                }

                @Override
                public ObservableValue<String> textProperty()
                {
                    return stringContent;
                }

                @Override
                public int getLength()
                {
                    return stringContent.get().length();
                }

                @Override
                public Val<Integer> lengthProperty()
                {
                    return length;
                }

                @Override
                public LiveList<Paragraph<Void, Item, ItemVariant>> getParagraphs()
                {
                    return paras;
                }

                @Override
                public ReadOnlyStyledDocument<Void, Item, ItemVariant> snapshot()
                {
                    return ReadOnlyStyledDocument.from(this);
                }

                @Override
                public EventStream<RichTextChange<Void, Item, ItemVariant>> richChanges()
                {
                    return null;
                }

                @Override
                public SuspendableNo beingUpdatedProperty()
                {
                    return null;
                }

                @Override
                public boolean isBeingUpdated()
                {
                    return false;
                }

                @Override
                public void replace(int i, int i1, StyledDocument<Void, Item, ItemVariant> styledDocument)
                {

                }

                @Override
                public void setStyle(int i, int i1, ItemVariant itemVariant)
                {

                }

                @Override
                public void setStyle(int i, ItemVariant itemVariant)
                {

                }

                @Override
                public void setStyle(int i, int i1, int i2, ItemVariant itemVariant)
                {

                }

                @Override
                public void setStyleSpans(int i, StyleSpans<? extends ItemVariant> styleSpans)
                {

                }

                @Override
                public void setStyleSpans(int i, int i1, StyleSpans<? extends ItemVariant> styleSpans)
                {

                }

                @Override
                public void setParagraphStyle(int i, Void aVoid)
                {

                }

                @Override
                public int length()
                {
                    return 0;
                }

                @Override
                public String getText()
                {
                    return null;
                }

                @Override
                public StyledDocument<Void, Item, ItemVariant> concat(StyledDocument<Void, Item, ItemVariant> styledDocument)
                {
                    return null;
                }

                @Override
                public StyledDocument<Void, Item, ItemVariant> subSequence(int i, int i1)
                {
                    return null;
                }

                @Override
                public Position position(int i, int i1)
                {
                    return null;
                }

                @Override
                public Position offsetToPosition(int i, Bias bias)
                {
                    return null;
                }
            };
        }
    */
    public void edit(@Nullable Point2D scenePoint, FXPlatformRunnable endEdit)
    {
        //TODO store and use endEdit
        if (scenePoint != null)
        {
            Point2D local = sceneToLocal(scenePoint);
            CharacterHit hit = hit(local.getX(), local.getY());
            int characterPosition = hit.getInsertionIndex();

            moveTo(characterPosition, SelectionPolicy.CLEAR);
            requestFocus();
        }
        else
        {
            requestFocus();
            selectAll();
        }
    }

    // Each item can either be editable (with a possibly-empty starting value, and optional prompt text)
    // or boilerplate (with a fixed value).  Fields can be optional
    // E.g. time might be Hour, fixed-:, minute, optional fixed-:, optional second (we don't bother having the dot as fixed boilerplate)
    public static enum ItemVariant
    {
        EDITABLE_DAY,
        EDITABLE_MONTH,
        EDITABLE_YEAR,
        EDITABLE_HOUR,
        EDITABLE_MINUTE,
        EDITABLE_SECOND,
        EDITABLE_OFFSET_HOUR,
        EDITABLE_OFFSET_MINUTE,
        TIMEZONE_PLUS_MINUS,
        DIVIDER;
    }

    @OnThread(Tag.FXPlatform)
    public static class Item
    {
        private final String content;
        private final ItemVariant itemVariant;
        private final @Localized String prompt;

        // Divider:
        public Item(String divider)
        {
            this(divider, ItemVariant.DIVIDER, "");
        }

        public Item(String content, ItemVariant ItemVariant, @Localized String prompt)
        {
            this.content = content;
            this.itemVariant = ItemVariant;
            this.prompt = prompt;
        }

        public StyledText<Collection<String>> toStyledText()
        {
            return new StyledText<>(getScreenText(), toStyles());
        }

        public Collection<String> toStyles()
        {
            return Collections.emptyList();
        }

        public int getLength()
        {
            return content.length();
        }

        public int getScreenLength()
        {
            return content.isEmpty() ? prompt.length() : content.length();
        }

        public String getScreenText()
        {
            return content.isEmpty() ? prompt : content;
        }
    }

    @OnThread(Tag.FXPlatform)
    static interface Component<T>
    {
        public List<Item> getInitialItems();
        public Either<List<ErrorFix>, T> endEdit(StructuredTextField<?> field);
    }

    @OnThread(Tag.FXPlatform)
    public static class Component2<R, A, B> implements Component<R>
    {
        private final Component<A> a;
        private final Component<B> b;
        private final BiFunction<A, B, R> combine;
        private final String divider;

        public Component2(Component<A> a, String divider, Component<B> b, BiFunction<A, B, R> combine)
        {
            this.a = a;
            this.b = b;
            this.divider = divider;
            this.combine = combine;
        }

        @Override
        public List<Item> getInitialItems()
        {
            return Utility.concat(a.getInitialItems(), Arrays.asList(new Item(divider)), b.getInitialItems());
        }

        @Override
        public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field)
        {
            Either<List<ErrorFix>, A> ax = a.endEdit(field);
            Either<List<ErrorFix>, B> bx = b.endEdit(field);
            return ax.either(ea -> bx.either(eb -> Either.left(Utility.concat(ea, eb)), rb -> Either.left(ea)),
                ra -> bx.either(eb -> Either.left(eb), rb -> Either.right(combine.apply(ra, rb))));
        }
    }

    @OnThread(Tag.FXPlatform)
    private static class YMD implements Component<LocalDate>
    {
        private final TemporalAccessor value;

        public YMD(TemporalAccessor value) throws InternalException
        {
            this.value = value;
        }

        public List<Item> getInitialItems()
        {
            return Arrays.asList(new Item(Integer.toString(value.get(ChronoField.DAY_OF_MONTH)), ItemVariant.EDITABLE_DAY, TranslationUtility.getString("entry.prompt.day")),
                  new Item("/"),
                  new Item(Integer.toString(value.get(ChronoField.MONTH_OF_YEAR)), ItemVariant.EDITABLE_MONTH, TranslationUtility.getString("entry.prompt.month")),
                  new Item("/"),
                  new Item(Integer.toString(value.get(ChronoField.YEAR)), ItemVariant.EDITABLE_YEAR, TranslationUtility.getString("entry.prompt.year")));
        }

        private static int adjustYear2To4(int day, int month, String originalYearText, final int year)
        {
            if (year < 100 && originalYearText.length() < 4)
            {
                // Apply 80/20 rule (20 years into future, or 80 years into past):
                int fourYear = Year.now().getValue() - (Year.now().getValue() % 100) + year;
                if (fourYear - Year.now().getValue() > 20)
                    fourYear -= 100;

                try
                {
                    LocalDate.of(fourYear, month, day);
                    // Only if valid:
                    return fourYear;
                }
                catch (DateTimeException e)
                {
                    // Not valid if we change year to four digits, may be another fault, so we wait
                }
            }
            return year;
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Either<List<ErrorFix>, LocalDate> endEdit(StructuredTextField<?> field)
        {
            List<ErrorFix> fixes = new ArrayList<>();
            field.revertEditFix().ifPresent(fixes::add);
            int standardFixes = fixes.size();

            try
            {
                String dayText = field.getItem(ItemVariant.EDITABLE_DAY);
                int day, month;
                try
                {
                    day = Integer.parseInt(dayText);
                    month = parseMonth(field.getItem(ItemVariant.EDITABLE_MONTH));
                }
                catch (NumberFormatException e)
                {
                    // If this throws, we'll fall out to the outer catch block
                    // Try swapping day and month:
                    month = parseMonth(dayText);
                    day = Integer.parseInt(field.getItem(ItemVariant.EDITABLE_MONTH));
                    dayText = field.getItem(ItemVariant.EDITABLE_MONTH);
                }


                String yearText = field.getItem(ItemVariant.EDITABLE_YEAR);
                int year = Integer.parseInt(yearText);
                // For fixes, we always use fourYear.  If they really want a two digit year, they should enter the leading zeroes
                if (day <= 0)
                {
                    clampFix(field, fixes,1, month, yearText, year);
                }
                try
                {
                    int adjYear = adjustYear2To4(1, month, yearText, year);
                    int monthLength = YearMonth.of(adjYear, month).lengthOfMonth();
                    if (day > monthLength)
                    {
                        clampFix(field, fixes, monthLength, month, yearText, year);
                        // If it's like 31st September, suggest 1st October:
                        if (day <= 31 && day - 1 == monthLength)
                        {
                            // Can't happen for December, so don't need to worry about year roll-over:
                            fix(field, fixes, "entry.fix.adjustByDay", 1, month + 1, yearText, year);
                        }

                        if (day >= 32 && year <= monthLength)
                        {
                            // They may have entered year, month, day, so swap day and year:
                            clampFix(field, fixes, year, month, dayText, day);
                        }
                    }
                }
                catch (DateTimeException e)
                {
                    // Not a valid year-month anyway, so don't suggest
                }

                if (month <= 0)
                {
                    clampFix(field, fixes, day, 1, yearText, year);
                }
                else if (month >= 13)
                {
                    clampFix(field, fixes, day, 12, yearText, year);
                    if (1 <= day && day <= 12)
                    {
                        // Possible day-month transposition:
                        fix(field, fixes, "entry.fix.dayMonthSwap", month, day, yearText, year);
                    }
                }

                if (fixes.size() == standardFixes)
                {
                    int adjYear = adjustYear2To4(day, month, yearText, year);
                    field.setItem(ItemVariant.EDITABLE_DAY, Integer.toString(day));
                    field.setItem(ItemVariant.EDITABLE_MONTH, Integer.toString(month));
                    field.setItem(ItemVariant.EDITABLE_YEAR, Integer.toString(adjYear));
                    return Either.right(LocalDate.of(adjYear, month, day));
                }
            }
            catch (NumberFormatException | DateTimeException e)
            {
            }
            return Either.left(fixes);
        }

        private static int parseMonth(String item) throws NumberFormatException
        {
            try
            {
                return Integer.parseInt(item);
            }
            catch (NumberFormatException e)
            {
                // Try as month name...
            }

            Set<Integer> possibles = new HashSet<>();
            for (int i = 1; i <= 12; i++)
            {
                for (TextStyle textStyle : TextStyle.values())
                {
                    if (Month.of(i).getDisplayName(textStyle, Locale.getDefault()).toLowerCase().startsWith(item.toLowerCase()))
                        possibles.add(i);
                }

            }
            if (possibles.size() == 1)
                return possibles.iterator().next();

            throw new NumberFormatException();
        }

        public void clampFix(StructuredTextField<?> field, List<ErrorFix> fixes, int day, int month, String yearText, int year)
        {
            fix(field, fixes, "entry.fix.clamp", day, month, yearText, year);
        }

        public void fix(StructuredTextField<?> field, List<ErrorFix> fixes, @LocalizableKey String labelKey, int day, int month, String yearText, int year)
        {
            // For fixes, we always use fourYear.  If they really want a two digit year, they should enter the leading zeroes
            year = adjustYear2To4(day, month, yearText, year);
            try
            {
                LocalDate.of(year, month, day);
            }
            catch (DateTimeException e)
            {
                // Don't add a fix if the destination date isn't valid either!
                return;
            }

            final String value = day + "/" + month + "/" + year;
            fixes.add(new ErrorFix(TranslationUtility.getString(labelKey, value))
            {
                @Override
                @OnThread(Tag.FXPlatform)
                public void performFix()
                {
                    field.setValue(value);
                }
            });
        }

    }

    // Field, index within field
    private Pair<Integer, Integer> plainToStructured(int position)
    {
        for (int i = 0; i < curValue.size(); i++)
        {
            Item item = curValue.get(i);
            if (item.itemVariant != ItemVariant.DIVIDER && position <= item.getScreenLength())
                return new Pair<>(i, position);
            position -= item.getScreenLength();
        }
        // Return end as a fallback:
        return new Pair<>(curValue.size() - 1, curValue.get(curValue.size() - 1).getScreenLength());
    }

    private int structuredToPlain(Pair<Integer, Integer> structured)
    {
        int pos = 0;
        for (int i = 0; i < structured.getFirst(); i++)
        {
            pos += curValue.get(i).getScreenLength();
        }
        return pos + structured.getSecond();
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void previousChar(SelectionPolicy selectionPolicy)
    {
        Pair<Integer, Integer> cur = plainToStructured(getCaretPosition());
        cur = calculatePreviousChar(cur);
        moveTo(structuredToPlain(cur), selectionPolicy);
    }

    @NotNull
    private Pair<Integer, Integer> calculatePreviousChar(Pair<Integer, Integer> cur)
    {
        Item curItem = curValue.get(cur.getFirst());
        if (cur.getSecond() > 0)
        {
            // Can advance backwards within the field:
            cur = new Pair<>(cur.getFirst(), Character.offsetByCodePoints(curItem.getScreenText(), cur.getSecond(), -1));
        }
        else if (cur.getFirst() == 0)
        {
            // Can't go left; already at beginning:
            return cur;
        }
        else
        {
            // Move to previous field:
            int field = cur.getFirst() - 1;
            while (field > 0)
            {
                if (curValue.get(field).itemVariant == ItemVariant.DIVIDER)
                    field -= 1;
                else
                    break;
            }
            cur = new Pair<>(field, curValue.get(field).getLength());
        }
        return cur;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void nextChar(SelectionPolicy selectionPolicy)
    {
        Pair<Integer, Integer> cur = plainToStructured(getCaretPosition());
        cur = calculateNextChar(cur);
        moveTo(structuredToPlain(cur), selectionPolicy);
    }

    @NotNull
    private Pair<Integer, Integer> calculateNextChar(Pair<Integer, Integer> cur)
    {
        Item curItem = curValue.get(cur.getFirst());
        if (cur.getSecond() < curItem.getLength()) // Note: getLength here, not getScreenLength. Can't move within prompt
        {
            // Can advance within the field:
            cur = new Pair<>(cur.getFirst(), Character.offsetByCodePoints(curItem.getScreenText(), cur.getSecond(), 1));
        }
        else if (cur.getFirst() == curValue.size() - 1)
        {
            // Can't go right; already at end:
            return cur;
        }
        else
        {
            // Move to next field:
            int field = cur.getFirst() + 1;
            while (field < curValue.size())
            {
                if (curValue.get(field).itemVariant == ItemVariant.DIVIDER)
                    field += 1;
                else
                    break;
            }
            cur = new Pair<>(field, 0);
        }
        return cur;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void wordBreaksBackwards(int n, SelectionPolicy selectionPolicy)
    {
        // So it seems that RichTextFX goes back two if the neighbouring character is whitespace, but that's not the behaviour we want
        // So just override the count to be 1:
        n = 1;

        Pair<Integer, Integer> cur = plainToStructured(getCaretPosition());
        for (int i = 0; i < n; i++)
        {
            if (cur.getSecond() > 0)
            {
                cur = new Pair<>(cur.getFirst(), 0);
            }
            else
            {
                cur = calculatePreviousChar(cur);
            }
        }
        moveTo(structuredToPlain(cur), selectionPolicy);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void wordBreaksForwards(int n, SelectionPolicy selectionPolicy)
    {
        // So it seems that RichTextFX goes back two if the neighbouring character is whitespace, but that's not the behaviour we want
        // So just override the count to be 1:
        n = 1;

        Pair<Integer, Integer> cur = plainToStructured(getCaretPosition());
        for (int i = 0; i < n; i++)
        {
            if (cur.getSecond() < curValue.get(cur.getFirst()).getLength())
            {
                cur = new Pair<>(cur.getFirst(), curValue.get(cur.getFirst()).getLength());
            }
            else
            {
                cur = calculateNextChar(cur);
            }
        }
        moveTo(structuredToPlain(cur), selectionPolicy);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public CharacterHit hit(double x, double y)
    {
        CharacterHit hit = super.hit(x, y);
        // Character index may be missing if for example they clicked beyond extents of field.

        // We need to find the structured index, and if it's in a prompt then clamp to one side or other
        return makeHit(Utility.mapOptionalInt(hit.getCharacterIndex(), p -> structuredToPlain(clamp(plainToStructured(p)))), structuredToPlain(clamp(plainToStructured(hit.getInsertionIndex()))));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void lineEnd(SelectionPolicy policy)
    {
        moveTo(structuredToPlain(new Pair<>(curValue.size() - 1, curValue.get(curValue.size() - 1).getLength())), policy);
    }

    private CharacterHit makeHit(OptionalInt charIdx, int insertionIdx)
    {
        // TODO this isn't quite right if the positions get split over a prompt; is that an issue?
        return charIdx.isPresent() ? CharacterHit.leadingHalfOf(insertionIdx) : CharacterHit.insertionAt(insertionIdx);
    }

    // If position is in a prompt, move it to nearest side
    private Pair<Integer, Integer> clamp(Pair<Integer, Integer> pos)
    {
        if (pos.getSecond() >= curValue.get(pos.getFirst()).getLength())
            return new Pair<>(pos.getFirst(), curValue.get(pos.getFirst()).getLength());
        else
            return pos;
    }
}
