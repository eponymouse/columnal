package records.gui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.text.Text;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.*;
import org.reactfx.EventStream;
import org.reactfx.SuspendableNo;
import org.reactfx.collection.LiveList;
import org.reactfx.value.SuspendableVal;
import org.reactfx.value.Val;
import records.error.InternalException;
import records.gui.StructuredTextField.Item;
import records.gui.StructuredTextField.ItemVariant;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Created by neil on 18/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class StructuredTextField extends GenericStyledArea<@Nullable Void, Item, ItemVariant>
{
    public StructuredTextField(Item... subItems) throws InternalException
    {
        super(null, (t, s) -> {}, ItemVariant.DIVIDER, segmentOps(), false, Item::makeNode);
        // Call super to avoid our own validation:
        super.replace(0, 0, makeInitialDoc(subItems));
    }

    private static ReadOnlyStyledDocument<Void, Item, ItemVariant> makeInitialDoc(Item[] subItems) throws InternalException
    {
        @MonotonicNonNull ReadOnlyStyledDocument<Void, Item, ItemVariant> doc = null;
        for (Item subItem : subItems)
        {
            ReadOnlyStyledDocument<Void, Item, ItemVariant> nextSeg = ReadOnlyStyledDocument.<@Nullable Void, Item, ItemVariant>fromSegment(subItem, null, subItem.itemVariant, segmentOps());
            doc = doc == null ? nextSeg : doc.concat(nextSeg);
        }
        if (doc != null)
        {
            return doc;
        }
        else
        {
            throw new InternalException("Empty document based on items " + subItems);
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void replace(int start, int end, StyledDocument<@Nullable Void, Item, ItemVariant> replacement)
    {
        StyleSpans<ItemVariant> existing = getStyleSpans(0);

        List<Pair<String, ItemVariant>> newContent = new ArrayList<>();
        int[] next = replacement.getText().codePoints().toArray();
        // Need to zip together the spans, in effect.  But should never delete the boilerplate parts
        int curExisting = 0;
        int curStart = 0;
        String cur = "";
        while (curStart < start)
        {
            int spanEnd = curStart + existing.getStyleSpan(curExisting).getLength();
            if (spanEnd < start)
            {
                newContent.add(new Pair<>(getText(curStart, spanEnd), existing.getStyleSpan(curExisting).getStyle()));
                curExisting += 1;
                curStart = spanEnd;
            }
            else
            {
                cur = getText(curStart, start);
                break;
            }
        }
        int replacementEnd = start;
        while (curExisting < existing.getSpanCount())
        {
            // Find the next chunk of new text which could go here:
            int nextChar = 0;
            ItemVariant curStyle = existing.getStyleSpan(curExisting).getStyle();
            String after = getText(curStart, curStart + existing.getStyleSpan(curExisting).getLength()); // TODO doesn't work if you edit partway through style
            while (nextChar < next.length || curExisting < existing.getSpanCount())
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
            newContent.add(new Pair<>(cur.isEmpty() ? " " : cur, curStyle));
            next = Arrays.copyOfRange(next, nextChar, next.length);
            curStart += existing.getStyleSpan(curExisting).getLength();
            curExisting += 1;
            cur = "";
        }
        @Nullable StyledDocument<Void, Item, ItemVariant> doc = makeDoc(newContent);
        if (doc != null)
        {
            super.replace(0, getLength(), doc);
            selectRange(replacementEnd, replacementEnd);
        }
    }

    private @Nullable StyledDocument<Void, Item, ItemVariant> makeDoc(List<Pair<String, ItemVariant>> content)
    {
        @MonotonicNonNull StyledDocument<Void, Item, ItemVariant> doc = null;
        for (Pair<String, ItemVariant> c : content)
        {
            StyledDocument<Void, Item, ItemVariant> next = ReadOnlyStyledDocument.fromString(c.getFirst(), null, c.getSecond(), segmentOps());
            doc = doc == null ? next : doc.concat(next);
        }
        return doc;
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
        String cStr = c.isPresent() ? new String(new int[] {c.getAsInt()}, 0, 1) : "";
        switch (style)
        {
            case EDITABLE_DAY:
                if (c.isPresent())
                {
                    if (c.getAsInt() >= '0' && c.getAsInt() <= '9' && before.length() <= 1)
                    {
                        return new CharEntryResult(before + cStr, true, false);
                    }
                    return new CharEntryResult(before, false, true);
                }
                else
                    return new CharEntryResult(before, true, true);
            case EDITABLE_MONTH:
                // TODO allow month names
                if (c.isPresent())
                    return c.getAsInt() >= '0' && c.getAsInt() <= '9' ? new CharEntryResult(before + cStr, true, false) : new CharEntryResult(before, false, true);
                else
                    return new CharEntryResult(before, true, true);
            case EDITABLE_YEAR:
                if (c.isPresent())
                    return c.getAsInt() >= '0' && c.getAsInt() <= '9' ? new CharEntryResult(before + cStr, true, false) : new CharEntryResult(before, false, true);
                else
                    return new CharEntryResult(before, true, true);
            case DIVIDER:
                break;
        }
        return new CharEntryResult(after, true, true);
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
}
