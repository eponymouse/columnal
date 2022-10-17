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

package xyz.columnal.gui.dtf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.dtf.Document.TrackedPosition.Bias;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReadOnlyDocument extends Document
{
    private final boolean error;
    private ImmutableList<Pair<Set<String>, String>> content;

    /*public ReadOnlyDocument(ImmutableList<Pair<Set<String>, String>> content)
    {
        this.content = content;
    }*/

    public ReadOnlyDocument(String content, boolean error)
    {
        this.content = ImmutableList.of(new Pair<>(ImmutableSet.<String>of(), content));
        this.error = error;
    }

    public ReadOnlyDocument(String content)
    {
        this(content, false);
    }

    @Override
    Stream<Pair<Set<String>, String>> getStyledSpans(boolean focused)
    {
        return content.stream();
    }

    @Override
    void replaceText(int startPosIncl, int endPosExcl, String text)
    {
        // We're read-only, so do nothing
    }

    @Override
    TrackedPosition trackPosition(int pos, Bias bias, @Nullable FXPlatformRunnable onChange)
    {
        // No need to actually track; if content can't change, neither can positions:
        return new TrackedPosition(pos, bias, onChange);
    }

    @Override
    public int getLength()
    {
        return content.stream().mapToInt(p -> p.getSecond().length()).sum();
    }

    @Override
    boolean isEditable()
    {
        return false;
    }

    @Override
    public String getText()
    {
        return content.stream().map(p -> p.getSecond()).collect(Collectors.joining());
    }

    @Override
    boolean hasError()
    {
        return error;
    }

    @Override
    void focusChanged(boolean focused)
    {
    }

    @Override
    public void setAndSave(String content)
    {
        // Not applicable
    }
}
