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

package xyz.columnal.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import xyz.columnal.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.utility.Utility;

public class CodeCompletionContext
{
    protected final ImmutableList<LexCompletionGroup> completions;

    public CodeCompletionContext(ImmutableList<LexCompletionGroup> completions)
    {
        this.completions = completions;
    }

    public CodeCompletionContext(CodeCompletionContext nestedCompletions, @CanonicalLocation int offsetBy)
    {
        this(Utility.mapListI(nestedCompletions.completions, cc -> cc.offsetBy(offsetBy)));
    }

    public ImmutableList<LexCompletionGroup> getCompletionsFor(@CanonicalLocation int caretPos)
    {
        return completions.stream().flatMap(g -> Utility.streamNullable(g.filterForPos(caretPos))).collect(ImmutableList.<LexCompletionGroup>toImmutableList());
    }
}
