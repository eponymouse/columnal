package styled;

import com.google.common.collect.ImmutableList;
import javafx.scene.text.Text;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        return new StyledString(styledString.members.stream().map(p -> p.mapFirst(style -> new Style(true, style.bold, style.size))).collect(ImmutableList.toImmutableList()));
    }

    public String toPlain()
    {
        return members.stream().map(p -> p.getSecond()).collect(Collectors.joining());
    }

    /**
     * Concats the items together, inserting divider between each pair
     */
    public static StyledString intercalate(StyledString divider, ImmutableList<StyledString> items)
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
            // TODO bold, font size
            return t;
        });
    }

    public static class Style
    {
        public final boolean italic;
        public final boolean bold;
        public final double size; // 1.0 is normal.

        private Style(boolean italic, boolean bold, double size)
        {
            this.italic = italic;
            this.bold = bold;
            this.size = size;
        }

        public Style()
        {
            this.italic = false;
            this.bold = false;
            this.size = 1.0;
        }

        public static Style italic()
        {
            return ITALIC;
        }
        
        private static Style ITALIC = new Style(true, false, 1.0);
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
}
