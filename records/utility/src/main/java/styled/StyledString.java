package styled;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap.Builder;
import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class StyledString
{
    // There will only be one style of a given class in any list of styles.
    public static abstract class Style<S extends Style<S>>
    {
        protected final Class<S> thisClass;

        protected Style(Class<S> thisClass)
        {
            this.thisClass = thisClass;
        }

        @OnThread(Tag.FXPlatform)
        protected abstract void style(Text t);
        
        protected abstract S combine(S with);
                
        protected final <T> @Nullable T as(Class<T> otherClass)
        {
            if (otherClass.isInstance(this))
                return otherClass.cast(this);
            else
                return null;
        }
        
        public final boolean equals(@Nullable Object x)
        {
            if (thisClass.isInstance(x))
                return equalsStyle(thisClass.cast(x));
            else
                return false; // Styles of another class cannot equal this one
        }

        protected abstract boolean equalsStyle(S item);

        // We don't generally use styles in hash collections, and overriding in subclasses is a pain
        // when we only really want one implementation (equalsStyle), so we just return a hash of the class here
        // which obeys the contract for equals and hashCode just fine (they can only be equal if they have the same class).
        public int hashCode()
        {
            return thisClass.hashCode();
        }
    }
    
    private final ImmutableList<Pair<ImmutableClassToInstanceMap<Style<?>>, String>> members;
    
    private StyledString(String normal)
    {
        members = ImmutableList.of(new Pair<>(ImmutableClassToInstanceMap.of(), normal));
    }
    
    private StyledString(ImmutableList<Pair<ImmutableClassToInstanceMap<Style<?>>, String>> items)
    {
        members = items;
    }

    @Pure
    public static <S extends Style<S>> StyledString styled(String content, S style)
    {
        return new StyledString(ImmutableList.of(new Pair<>(ImmutableClassToInstanceMap.of(style.thisClass, style), content)));
    }
/*
    @Pure
    public static StyledString italicise(StyledString styledString)
    {
        return new StyledString(styledString.members.stream().map(p -> p.mapFirst(style -> new Style(true, style.bold, style.monospace, style.size, style.onClick))).collect(ImmutableList.toImmutableList()));
    }

    public static StyledString monospace(StyledString styledString)
    {
        return new StyledString(styledString.members.stream().map(p -> p.mapFirst(style -> new Style(style.italic, style.bold, true, style.size, style.onClick))).collect(ImmutableList.toImmutableList()));
    }
    
    @Pure public StyledString clickable(FXPlatformConsumer<Point2D> onClick)
    {
        return new StyledString(members.stream().map(p -> p.mapFirst(style -> style.withClick(onClick))).collect(ImmutableList.toImmutableList()));
    }
    */

    /**
     * Adds the given style to all our members.  If a style of the same class exists already,
     * they will get merged using the style's combine method.
     */
    //@SuppressWarnings("all") // Due to recursive type.  TODO report and fix this
    @Pure public <S extends Style<S>> StyledString withStyle(S style)
    {
        return new StyledString(Utility.mapListI(members, p -> p.mapFirst(prevMap -> {
            // We can't put the same key twice into the builder even, so we must proceed carefully:
            ImmutableClassToInstanceMap.Builder<Style<?>> builder = ImmutableClassToInstanceMap.builder();
            
            class DealWithStyle
            {
                public <K extends Style<K>> void mergeHelper(Style<K> prevValue)
                {
                    @Nullable K prevStyle = prevMap.getInstance(prevValue.thisClass);
                    // prevStyle should always be null given we are going through the values.  But to satisfy
                    // null check we must guard:
                    if (prevStyle != null)
                    {
                        @Nullable K styleAsK = style.as(prevValue.thisClass);
                        if (styleAsK != null)
                            builder.put(prevValue.thisClass, styleAsK.combine(prevStyle));
                        else
                            builder.put(prevValue.thisClass, prevStyle);
                    }
                }
            }
            
            // This is a bit weird, but knowing that each value is pointed to by its
            prevMap.values().forEach(s -> new DealWithStyle().mergeHelper(s));
            return builder.build();
        })));
    }

    public String toPlain()
    {
        return members.stream().map(p -> p.getSecond()).collect(Collectors.joining());
    }

    @Override
    public String toString()
    {
        return toPlain();
    }

    /**
     * Concats the items together, inserting divider between each pair
     */
    public static StyledString intercalate(StyledString divider, List<StyledString> items)
    {
        ImmutableList.Builder<Pair<ImmutableClassToInstanceMap<Style<?>>, String>> l = ImmutableList.builder();
        boolean addDivider = false;
        for (StyledString item : items)
        {
            if (addDivider)
                l.addAll(divider.members);
            l.addAll(item.members);
            addDivider = true;
        }
        return new StyledString(l.build());
    }

    @OnThread(Tag.FXPlatform)
    public List<Text> toGUI()
    {
        return Utility.mapList(members, p -> {
            Text t = new Text(p.getSecond());
            t.getStyleClass().add("styled-text");
            p.getFirst().values().forEach(style -> style.style(t));
            return t;
        });
    }

    public static Collector<StyledString, ?, StyledString> joining(String s)
    {
        return Collector.<StyledString, Builder, StyledString>of(Builder::new, (l, x) -> {
            l.append(x);
        }, Builder::new, b -> b.build(StyledString.s(s)));
    }

    @Pure
    public static StyledString concat(StyledString... items)
    {
        return new StyledString(
            Arrays.stream(items)
                .flatMap(ss -> ss.members.stream())
                .filter(ss -> !ss.getSecond().isEmpty())
                .collect(ImmutableList.toImmutableList())
        );
    }
    
    @Pure
    public static StyledString s(String content)
    {
        return new StyledString(content);
    }

    public static StyledString roundBracket(StyledString inner)
    {
        return StyledString.concat(StyledString.s("("), inner, StyledString.s(")"));
    }
    
    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StyledString that = (StyledString) o;
        return Objects.equals(members, that.members);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(members);
    }

    public static class Builder
    {
        private final ArrayList<StyledString> contents = new ArrayList<>();

        public Builder()
        {
        }
        
        public Builder(Builder lhs, Builder rhs)
        {
            contents.addAll(lhs.contents);
            contents.addAll(rhs.contents);
        }

        public Builder append(StyledString styledString)
        {
            contents.add(styledString);
            return this;
        }
        
        public Builder append(String unstyled)
        {
            return append(StyledString.s(unstyled));
        }
        
        public StyledString build()
        {
            return StyledString.concat(contents.toArray(new StyledString[0]));
        }

        public StyledString build(StyledString divider)
        {
            return StyledString.intercalate(divider, contents);
        }
    }
}
