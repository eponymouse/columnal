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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import javafx.scene.text.Text;
import xyz.columnal.styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;

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
