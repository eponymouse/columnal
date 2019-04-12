package styled;

import com.google.common.collect.ImmutableList.Builder;
import com.google.common.reflect.ImmutableTypeToInstanceMap;
import com.google.common.collect.ImmutableList;
import javafx.scene.text.Text;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class StyledString
{
    public static Builder builder()
    {
        return new Builder();
    }

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
        
        // The first applied style is this; the next applied style is the parameter.
        // To keep first, return this, to keep last, return the parameter.
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
    
    // I got VerifyError items (14th March 2018) when using Guava's ImmutableClassToInstanceMap, so this is
    // my own simple equivalent, specialised to Style.  To be honest, it's a lot simpler by being specialised anyway:
    private static class ImmutableStyleMap
    {
        public static final ImmutableStyleMap EMPTY = new ImmutableStyleMap(ImmutableList.of());
        // Only one style per class type in this list:
        private final ImmutableList<Style<?>> styleMembers;

        // Warning: do not pass a list which has more than one entry per Style subclass.
        private ImmutableStyleMap(ImmutableList<Style<?>> styleMembers)
        {
            this.styleMembers = styleMembers;
        }
    }
    
    private final ImmutableList<Pair<ImmutableStyleMap, String>> members;
    
    private StyledString(String normal)
    {
        members = ImmutableList.of(new Pair<>(ImmutableStyleMap.EMPTY, normal));
    }
    
    private StyledString(ImmutableList<Pair<ImmutableStyleMap, String>> items)
    {
        members = items;
    }

    @Pure
    public static <S extends Style<S>> StyledString styled(String content, S style)
    {
        return new StyledString(ImmutableList.of(new Pair<>(new ImmutableStyleMap(ImmutableList.of(style)), content)));
    }

    /**
     * Adds the given style to all our members.  If a style of the same class exists already,
     * they will get merged using the style's combine method.
     */
    @Pure public <S extends Style<S>> StyledString withStyle(S newStyle)
    {
        return new StyledString(Utility.mapListI(members, p -> p.mapFirst(prevStyles -> {
            ImmutableList.Builder<Style<?>> newStyles = ImmutableList.builder();
            boolean added = false;
            for (Style<?> prevStyleMember : prevStyles.styleMembers)
            {
                @Nullable S prevStyleAsS = prevStyleMember.as(newStyle.thisClass);
                if (prevStyleAsS != null)
                {
                    newStyles.add(prevStyleAsS.combine(newStyle));
                    added = true;
                }
                else
                {
                    newStyles.add(prevStyleMember);
                }
            }
            if (!added)
            {
                newStyles.add(newStyle);
            }
            return new ImmutableStyleMap(newStyles.build());
        })));
    }

    @SuppressWarnings("i18n")
    public @Localized String toPlain()
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
        ImmutableList.Builder<Pair<ImmutableStyleMap, String>> l = ImmutableList.builder();
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
    public ImmutableList<Text> toGUI()
    {
        return Utility.mapListI(members, p -> {
            Text t = new Text(p.getSecond());
            t.getStyleClass().add("styled-text");
            p.getFirst().styleMembers.forEach(style -> style.style(t));
            return t;
        });
    }

    public static Collector<StyledString, ?, StyledString> joining(String s)
    {
        return Collector.<StyledString, Builder, StyledString>of(Builder::new, (l, x) -> {
            l.append(x);
        }, Builder::new, b -> s.isEmpty() ? b.build() : b.build(StyledString.s(s)));
    }

    @Pure
    public static StyledString concat(StyledString... items)
    {
        return new StyledString(
            Arrays.stream(items)
                .flatMap(ss -> ss.members.stream())
                .filter(ss -> !ss.getSecond().isEmpty())
                .collect(ImmutableList.<Pair<ImmutableStyleMap, String>>toImmutableList())
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

    public static StyledString squareBracket(StyledString inner)
    {
        return StyledString.concat(StyledString.s("["), inner, StyledString.s("]"));
    }
    
    public int getLength()
    {
        return members.stream().mapToInt(p -> p.getSecond().length()).sum();
    }
    
    public StyledString substring(int startIndexIncl, int endIndexExcl)
    {
        ImmutableList.Builder<Pair<ImmutableStyleMap, String>> r = ImmutableList.builder();
        int curPos = 0;
        int curIndex = 0;
        while (curIndex < members.size() && curPos < startIndexIncl)
        {
            curPos += members.get(curIndex).getSecond().length();
            
            if (curPos > startIndexIncl)
            {
                // Add on the relevant part of the last segment:
                int curPosFinal = curPos;
                r.add(members.get(curIndex).mapSecond(s -> s.substring(s.length() - (curPosFinal - startIndexIncl))));
            }
            curIndex += 1;
        }
        // Now in the middle of the segment:
        while (curIndex < members.size() && curPos < endIndexExcl)
        {
            int maxLen = endIndexExcl - curPos;
            r.add(members.get(curIndex).mapSecond(s -> s.substring(0, Math.min(maxLen, s.length()))));
            curPos += members.get(curIndex).getSecond().length();
            curIndex += 1;
        }
        
        return new StyledString(r.build());
    }

    public ImmutableList<Pair<ImmutableList<Style<?>>, String>> getMembers()
    {
        return Utility.mapListI(members, m -> m.mapFirst(sm -> sm.styleMembers));
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
        
        private Builder(Builder lhs, Builder rhs)
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
