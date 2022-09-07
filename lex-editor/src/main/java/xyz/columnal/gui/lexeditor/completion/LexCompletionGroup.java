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

package xyz.columnal.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Utility;

import java.util.OptionalInt;

public class LexCompletionGroup
{
    final ImmutableList<LexCompletion> completions;
    final @Nullable StyledString header;
    // How many items to show as a minimum if the group is collapsed
    final int minCollapsed;

    public LexCompletionGroup(ImmutableList<LexCompletion> completions, @Nullable StyledString header, int minCollapsed)
    {
        this.completions = completions;
        this.header = header;
        this.minCollapsed = minCollapsed;
    }
    
    public @Nullable LexCompletionGroup filterForPos(@CanonicalLocation int caretPos)
    {
        ImmutableList<LexCompletion> filtered = completions.stream().filter(c -> c.showFor(caretPos)).collect(ImmutableList.<LexCompletion>toImmutableList());
        if (filtered.isEmpty())
            return null;
        return new LexCompletionGroup(filtered, header, minCollapsed);
    }

    public LexCompletionGroup offsetBy(@CanonicalLocation int offsetBy)
    {
        ImmutableList<LexCompletion> offset = Utility.mapListI(completions, c -> c.offsetBy(offsetBy));

        return new LexCompletionGroup(offset, header, minCollapsed);
    }
}
