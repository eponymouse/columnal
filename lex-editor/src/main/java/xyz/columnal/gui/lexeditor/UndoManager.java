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
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.utility.adt.Pair;

import java.util.ArrayList;

//package-visible
class UndoManager
{
    private final ArrayList<Pair<String, @CanonicalLocation Integer>> undoList = new ArrayList<>();
    private int curIndex = 0;
    
    public UndoManager(String originalContent)
    {
        undoList.add(new Pair<>(originalContent, CanonicalLocation.ZERO));
    }
    
    public @Nullable Pair<String, @CanonicalLocation Integer> undo()
    {
        if (curIndex > 0)
        {
            curIndex -= 1;
            return undoList.get(curIndex);
        }
        return null;
    }

    public @Nullable Pair<String, @CanonicalLocation Integer> redo()
    {
        if (curIndex + 1 < undoList.size())
        {
            curIndex += 1;
            return undoList.get(curIndex);
        }
        return null;
    }
    
    public void contentChanged(String newContent, @CanonicalLocation int caretPosition)
    {
        if (curIndex == undoList.size() - 1)
        {
            curIndex += 1;
        }
        undoList.add(new Pair<>(newContent, caretPosition));
    }
}
