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

package test.gui.expressionEditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import xyz.columnal.gui.lexeditor.completion.LexAutoCompleteWindow;
import xyz.columnal.gui.lexeditor.completion.LexCompletion;
import test.TestUtil;
import test.gui.util.FXApplicationTest;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.Objects;

import static org.junit.Assert.fail;

public abstract class BaseTestEditorCompletion extends FXApplicationTest
{
    protected CompletionCheck c(String content, int... startEndInclPairs)
    {
        ImmutableList.Builder<Pair<@CanonicalLocation Integer, @CanonicalLocation Integer>> pairs = ImmutableList.builder();
        for (int i = 0; i < startEndInclPairs.length; i += 2)
        {
            @SuppressWarnings("units")
            Pair<@CanonicalLocation Integer, @CanonicalLocation Integer> pos = new Pair<>(startEndInclPairs[i], startEndInclPairs[i + 1]);
            pairs.add(pos);
        }
        return new CompletionCheck(content, pairs.build());
    }

    protected void checkCompletions(CompletionCheck... checks)
    {
        EditorDisplay editorDisplay = lookup(".editor-display").query();
        // We go from end in case there is a trailing space,
        // which will be removed once we go backwards:
        push(KeyCode.END);
        int prevPos = -1;
        int curPos;
        while (prevPos != (curPos = TestUtil.fx(() -> editorDisplay.getCaretPosition())))
        {
            // Check completions here
            @Nullable LexAutoCompleteWindow window = Utility.filterClass(listWindows().stream(), LexAutoCompleteWindow.class).findFirst().orElse(null);
            List<LexCompletion> showing = TestUtil.fx(() -> window == null ? ImmutableList.<LexCompletion>of() : window._test_getShowing());
            for (CompletionCheck check : checks)
            {
                ImmutableList<LexCompletion> matching = showing.stream().filter(l -> Objects.equals(l.content, check.content)).collect(ImmutableList.toImmutableList());
                
                boolean wasChecked = false;
                for (Pair<Integer, Integer> startInclToEndIncl : check.startInclToEndIncl)
                {
                    if (startInclToEndIncl.getFirst() <= curPos && curPos <= startInclToEndIncl.getSecond())
                    {
                        wasChecked = true;
                        if (matching.isEmpty())
                        {
                            fail("Did not find completion {{{" + check.content + "}}} at caret position " + curPos);
                        }
                        else if (matching.size() > 1)
                        {
                            fail("Found duplicate completions {{{" + check.content + "}}} at caret position " + curPos);
                        }
                        //assertEquals("Start pos for {{{" + check.content + "}}}", startInclToEndIncl.getFirst().intValue(), matching.get(0).startPos);
                    }
                }
                if (!wasChecked)
                {
                    if (matching.size() > 0)
                    {
                        fail("Found completion {{{" + check.content + "}}} which should not be present at " + curPos);
                    }
                }
            }
            prevPos = curPos;
            push(KeyCode.LEFT);
        }
    }

    protected class CompletionCheck
    {
        private final String content;
        private final ImmutableList<Pair<@CanonicalLocation Integer, @CanonicalLocation Integer>> startInclToEndIncl;

        public CompletionCheck(String content, ImmutableList<Pair<@CanonicalLocation Integer, @CanonicalLocation Integer>> startInclToEndIncl)
        {
            this.content = content;
            this.startInclToEndIncl = startInclToEndIncl;
        }
    }
}
