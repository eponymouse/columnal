package styled;

import com.google.common.collect.ImmutableList;
import javafx.scene.text.Text;
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

public class StyledString
{
    private final ImmutableList<Pair<Style, String>> members;
    
    private StyledString(String normal)
    {
        members = ImmutableList.of(new Pair<>(new Style(), normal));
    }
    
    private StyledString(ImmutableList<Pair<Style, String>> items)
    {
        members = items;
    }

    @Pure
    public static StyledString styled(String content, Style style)
    {
        return new StyledString(ImmutableList.of(new Pair<>(style, content)));
    }

    @Pure
    public static StyledString italicise(StyledString styledString)
    {
        return new StyledString(styledString.members.stream().map(p -> p.mapFirst(style -> new Style(true, style.bold, style.monospace, style.size))).collect(ImmutableList.toImmutableList()));
    }

    public static StyledString monospace(StyledString styledString)
    {
        return new StyledString(styledString.members.stream().map(p -> p.mapFirst(style -> new Style(style.italic, style.bold, true, style.size))).collect(ImmutableList.toImmutableList()));
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
        ImmutableList.Builder<Pair<Style, String>> l = ImmutableList.builder();
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
            if (p.getFirst().italic)
            {
                t.setStyle("-fx-font-style: italic;");
            }
            // TODO bold, monospace,font size
            return t;
        });
    }

    public static Collector<StyledString, ?, StyledString> joining(String s)
    {
        return Collector.<StyledString, Builder, StyledString>of(Builder::new, (l, x) -> {
            l.append(x);
        }, Builder::new, b -> b.build(StyledString.s(s)));
    }

    public static class Style
    {
        public final boolean italic;
        public final boolean bold;
        public final boolean monospace;
        public final double size; // 1.0 is normal.

        private Style(boolean italic, boolean bold, boolean monospace, double size)
        {
            this.italic = italic;
            this.bold = bold;
            this.monospace = monospace;
            this.size = size;
        }

        public Style()
        {
            this.italic = false;
            this.bold = false;
            this.monospace = false;
            this.size = 1.0;
        }

        public static Style italic()
        {
            return ITALIC;
        }
        
        private static Style ITALIC = new Style(true, false, false, 1.0);

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Style style = (Style) o;
            return italic == style.italic &&
                bold == style.bold &&
                Double.compare(style.size, size) == 0;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(italic, bold, size);
        }
    }
    
    @Pure
    public static StyledString concat(StyledString... items)
    {
        return new StyledString(Arrays.stream(items).flatMap(ss -> ss.members.stream()).collect(ImmutableList.toImmutableList()));
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
