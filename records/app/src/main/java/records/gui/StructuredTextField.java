package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.text.Text;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
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

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Created by neil on 18/06/2017.
 */
@OnThread(Tag.FXPlatform)
public abstract class StructuredTextField<T> extends StyleClassedTextArea
{
    private final List<Item> curValue = new ArrayList<>();
    private T completedValue;

    private StructuredTextField(Item... subItems) throws InternalException
    {
        super(false);
        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            if (!focused)
            {
                endEdit().either_(err -> {/*TODO show dialog */}, v -> {completedValue = v;});
            }
        });

        curValue.addAll(Arrays.asList(subItems));
        @Nullable T val = endEdit().<@Nullable T>either(err -> null, v -> v);
        if (val == null)
        {
            throw new InternalException("Starting field off with invalid completed value: " + Utility.listToString(Arrays.asList(subItems)));
        }
        completedValue = val;
        // Call super to avoid our own validation:
        super.replace(0, 0, makeDoc(subItems));
    }

    private static ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> makeDoc(Item[] subItems)
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

    @NotNull
    static StructuredTextField<? extends TemporalAccessor> dateYMD(TemporalAccessor value) throws InternalException
    {
        return new YMD(value);
    }

    @RequiresNonNull("curValue")
    protected String getItem(@UnknownInitialization(Object.class) StructuredTextField<T> this, ItemVariant item)
    {
        return curValue.stream().filter(ss -> ss.itemVariant == item).findFirst().map(ss -> ss.content).orElse("");
    }

    @RequiresNonNull("curValue")
    protected void setItem(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField<T> this, ItemVariant item, String content)
    {
        curValue.replaceAll(old -> old.itemVariant == item ? new Item(content, item) : old);
        super.replace(0, getLength(), makeDoc(curValue.toArray(new Item[0])));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void replace(final int start, final int end, StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> replacement)
    {
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
            if (start == spanEnd && (curExisting + 1 >= existing.size() || existing.get(curExisting + 1).itemVariant == ItemVariant.DIVIDER))
                break;

            if (spanEnd <= start)
            {
                newContent.add(new Item(existing.get(curExisting).content, existing.get(curExisting).itemVariant));
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
            ItemVariant curStyle = existing.get(curExisting).itemVariant;
            String after = curStyle == ItemVariant.DIVIDER ?
                    getText(curStart, curStart + existing.get(curExisting).getLength()) :
                    getText(Math.max(curStart, end), Math.max(end, curStart + existing.get(curExisting).getLength()));
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
            newContent.add(new Item(cur, curStyle));
            next = Arrays.copyOfRange(next, nextChar, next.length);
            curStart += existing.get(curExisting).getLength();
            curExisting += 1;
            cur = "";
        }
        StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = makeDoc(newContent.toArray(new Item[0]));
        super.replace(0, getLength(), doc);
        curValue.clear();
        curValue.addAll(newContent);
        selectRange(replacementEnd, replacementEnd);
    }

    /**
     * If return is null, successful.  Otherwise list is of alternatives for fixing the error (may be empty)
     * @return
     */
    @RequiresNonNull("curValue")
    public abstract Either<List<Pair<String, FXPlatformRunnable>>, T> endEdit(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField<T> this);

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
            // TODO allow month names
            if (c.getAsInt() >= '0' && c.getAsInt() <= '9')
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

    private int maxLength(ItemVariant style)
    {
        switch (style)
        {
            case EDITABLE_YEAR:
                return 4;
            default:
                return 2;
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
        DIVIDER;
    }

    @OnThread(Tag.FXPlatform)
    public static class Item
    {
        private final String content;
        private final ItemVariant itemVariant;

        public Item(String content, ItemVariant ItemVariant)
        {
            this.content = content;
            this.itemVariant = ItemVariant;
        }

        public Node makeNode()
        {
            return new Text(content);
        }

        public StyledText<Collection<String>> toStyledText()
        {
            return new StyledText<>(content, toStyles());
        }

        public Collection<String> toStyles()
        {
            return Collections.emptyList();
        }

        public int getLength()
        {
            return content.length();
        }
    }

    @OnThread(Tag.FXPlatform)
    private static TextOps<Item, ItemVariant> segmentOps()
    {
        return new TextOps<Item, ItemVariant>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public Item create(String content, ItemVariant ItemVariant)
            {
                return new Item(content, ItemVariant);
            }

            @Override
            public int length(Item item)
            {
                return item.content.length();
            }

            @Override
            public char charAt(Item item, int i)
            {
                return item.content.charAt(i);
            }

            @Override
            public String getText(Item item)
            {
                return item.content;
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Item subSequence(Item item, int begin, int end)
            {
                return new Item(item.content.substring(begin, end), item.itemVariant);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Item subSequence(Item item, int begin)
            {
                return new Item(item.content.substring(begin), item.itemVariant);
            }

            @Override
            public ItemVariant getStyle(Item item)
            {
                return item.itemVariant;
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Item setStyle(Item item, ItemVariant itemVariant)
            {
                return new Item(item.content, itemVariant);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Optional<Item> join(Item left, Item right)
            {
                return Objects.equals(left.itemVariant, right.itemVariant) && left.itemVariant != ItemVariant.DIVIDER ? Optional.of(new Item(left.content + right.content, left.itemVariant)) : Optional.empty();
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Item createEmpty()
            {
                return new Item("", ItemVariant.DIVIDER);
            }
        };
    }

    @OnThread(Tag.FXPlatform)
    private static class YMD extends StructuredTextField<TemporalAccessor>
    {
        public YMD(TemporalAccessor value) throws InternalException
        {
            super(new Item(Integer.toString(value.get(ChronoField.DAY_OF_MONTH)), ItemVariant.EDITABLE_DAY), new Item("/", ItemVariant.DIVIDER), new Item(Integer.toString(value.get(ChronoField.MONTH_OF_YEAR)), ItemVariant.EDITABLE_MONTH), new Item("/", ItemVariant.DIVIDER), new Item(Integer.toString(value.get(ChronoField.YEAR)), ItemVariant.EDITABLE_YEAR));
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Either<List<Pair<String, FXPlatformRunnable>>, TemporalAccessor> endEdit(@UnknownInitialization(StyleClassedTextArea.class) YMD this)
        {
            // TODO check for misordering (e.g. year first), or dates like 31st November

            try
            {
                int day = Integer.parseInt(getItem(ItemVariant.EDITABLE_DAY));
                // TODO allow month names
                int month = Integer.parseInt(getItem(ItemVariant.EDITABLE_MONTH));
                int year = Integer.parseInt(getItem(ItemVariant.EDITABLE_YEAR));

                if (year < 100)
                {
                    // Apply 80/20 rule (20 years into future, or 80 years into past):
                    int fourYear = Year.now().getValue() - (Year.now().getValue() % 100) + year;
                    if (fourYear - Year.now().getValue() > 20)
                        fourYear -= 100;
                    setItem(ItemVariant.EDITABLE_YEAR, Integer.toString(fourYear));
                    year = fourYear;
                }

                //TODO check date is valid
                return Either.right(LocalDate.of(year, month, day));
            }
            catch (NumberFormatException e)
            {
                return Either.left(Collections.emptyList());
            }
        }
    }
}
