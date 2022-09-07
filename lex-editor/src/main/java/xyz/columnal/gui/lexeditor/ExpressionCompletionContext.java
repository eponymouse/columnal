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
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.lexeditor.TopLevelEditor.DisplayType;
import xyz.columnal.gui.lexeditor.completion.LexCompletion;
import xyz.columnal.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.FXPlatformBiFunction;
import xyz.columnal.utility.FXPlatformFunction;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.EnumSet;

public class ExpressionCompletionContext extends CodeCompletionContext
{
    public ExpressionCompletionContext(ImmutableList<LexCompletionGroup> completions)
    {
        super(completions);
    }

    public ExpressionCompletionContext(CodeCompletionContext nestedCompletions, @CanonicalLocation int offsetBy)
    {
        super(nestedCompletions, offsetBy);
    }
}
