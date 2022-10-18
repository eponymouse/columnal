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

package xyz.columnal.styled;

import com.google.common.collect.ImmutableList.Builder;
import com.google.common.reflect.ImmutableTypeToInstanceMap;
import com.google.common.collect.ImmutableList;
import javafx.scene.text.Text;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final record StyledString(ImmutableList<StyledSegment> members)
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
    // Only one style per class type in the styleMembers list:
    private static record ImmutableStyleMap(ImmutableList<Style<?>> styleMembers)
    {
        public static final ImmutableStyleMap EMPTY = new ImmutableStyleMap(ImmutableList.of());
    }
    
    // We don't use Pair because we don't want the dependency:
    private static record StyledSegment(ImmutableStyleMap styleMap, String text)
    {
        public StyledSegment mapFirst(Function<ImmutableStyleMap, ImmutableStyleMap> f)
        {
            return new StyledSegment(f.apply(styleMap), text);
        }

        public StyledSegment mapSecond(Function<String, String> f)
        {
            return new StyledSegment(styleMap, f.apply(text));
        }
    }
    
    private StyledString(String normal)
    {
        this(ImmutableList.of(new StyledSegment(ImmutableStyleMap.EMPTY, normal)));
    }

    @Pure
    public static <S extends Style<S>> StyledString styled(String content, S style)
    {
        return new StyledString(ImmutableList.of(new StyledSegment(new ImmutableStyleMap(ImmutableList.of(style)), content)));
    }

    /**
     * Adds the given style to all our members.  If a style of the same class exists already,
     * they will get merged using the style's combine method.
     */
    @Pure public <S extends Style<S>> StyledString withStyle(S newStyle)
    {
        return new StyledString(members.stream().map(p -> p.mapFirst(prevStyles -> {
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
        })).collect(ImmutableList.toImmutableList()));
    }

    @SuppressWarnings("i18n")
    public @Localized String toPlain()
    {
        return members.stream().map(StyledSegment::text).collect(Collectors.joining());
    }

    @Override
    public String toString()
    {
        return toPlain();
    }

    // Truncates the string if it is longer than the given length
    public StyledString limit(int length)
    {
        if (getLength() > length)
        {
            return StyledString.concat(substring(0, length - 1), StyledString.s("\u2026"));
        }
        else
            return this;
    }


    /**
     * Concats the items together, inserting divider between each pair
     */
    public static StyledString intercalate(StyledString divider, List<StyledString> items)
    {
        ImmutableList.Builder<StyledSegment> l = ImmutableList.builder();
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
        return members.stream().map(p -> {
            Text t = new Text(p.text());
            t.getStyleClass().add("styled-text");
            p.styleMap().styleMembers.forEach(style -> style.style(t));
            return t;
        }).collect(ImmutableList.toImmutableList());
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
            Arrays.<StyledString>stream(items)
                .<StyledSegment>flatMap(ss -> ss.members.stream())
                .filter(ss -> !ss.text().isEmpty())
                .collect(ImmutableList.<StyledSegment>toImmutableList())
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
    
    public static StyledString fancyQuote(StyledString inner)
    {
        return StyledString.concat(StyledString.s("\u201c"), inner, StyledString.s("\u201d"));
    }
    
    public int getLength()
    {
        return members.stream().mapToInt(p -> p.text().length()).sum();
    }
    
    public StyledString substring(int startIndexIncl, int endIndexExcl)
    {
        ImmutableList.Builder<StyledSegment> r = ImmutableList.builder();
        int curPos = 0;
        int curIndex = 0;
        while (curIndex < members.size() && curPos < startIndexIncl)
        {
            curPos += members.get(curIndex).text().length();
            
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
            curPos += members.get(curIndex).text().length();
            curIndex += 1;
        }
        
        return new StyledString(r.build());
    }

    public void forEach(BiConsumer<ImmutableList<Style<?>>, String> doForEach)
    {
        for (StyledSegment member : members())
        {
            doForEach.accept(member.styleMap().styleMembers(), member.text());
        }
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
