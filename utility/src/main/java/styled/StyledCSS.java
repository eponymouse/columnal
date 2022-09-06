package styled;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import javafx.scene.text.Text;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

/**
 * A Style for StyledString that applies a set of CSS classes.
 */
public class StyledCSS extends Style<StyledCSS>
{
    private final ImmutableSet<String> styles;

    public StyledCSS(ImmutableSet<String> styles)
    {
        super(StyledCSS.class);
        this.styles = styles;
    }

    public StyledCSS(String... styleClasses)
    {
        this(ImmutableSet.copyOf(styleClasses));
    }

    @Override
    protected @OnThread(Tag.FXPlatform) void style(Text t)
    {
        t.getStyleClass().addAll(styles);
    }

    @Override
    protected StyledCSS combine(StyledCSS with)
    {
        return new StyledCSS(Sets.union(styles, with.styles).immutableCopy());
    }

    @Override
    protected boolean equalsStyle(StyledCSS item)
    {
        return styles.equals(item.styles);
    }
}
