package xyz.columnal.gui.lexeditor;

import annotation.units.CanonicalLocation;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.utility.Pair;

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
