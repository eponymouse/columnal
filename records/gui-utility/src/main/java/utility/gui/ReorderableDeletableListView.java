package utility.gui;

import javafx.collections.ObservableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Collections;

@OnThread(Tag.FXPlatform)
public class ReorderableDeletableListView<T>
{
    /*
    private int curDragTargetIndex = -1;
    
    public ReorderableDeletableListView(ObservableList<T> items)
    {
        super(items);
    }

    public ReorderableDeletableListView()
    {
    }

    @Override
    protected DeletableListCell makeCell()
    {
        return new RDListCell();
    }

    protected class RDListCell extends DeletableListCell
    {
        public RDListCell()
        {
            contentPane.setOnMouseDragged(e -> {
                // Find nearest cell boundary:
                Pair<@Nullable DeletableListCell, @Nullable DeletableListCell> nearest = getNearestGap(e.getSceneX(), e.getSceneY());
                @Nullable DeletableListCell first = nearest.getFirst();
                if (first != null && validTargetPosition(first.getIndex() + 1))
                {
                    curDragTargetIndex = first.getIndex() + 1;
                    setTargetBelow(first, true);
                }
                @Nullable DeletableListCell second = nearest.getSecond();
                if (second != null && validTargetPosition(second.getIndex()))
                {
                    curDragTargetIndex = second.getIndex();
                    setTargetAbove(second, true);
                }
            });
            contentPane.setOnMouseReleased(e -> {
                forAllCells(c -> {setTargetBelow(c, false); setTargetAbove(c, false);});
                if (curDragTargetIndex != -1)
                {
                    T t = getItem();
                    if (t != null)
                    {
                        int curIndex = getIndex();
                        getItems().remove(curIndex);
                        // Add before remove, because remove will upset index:
                        getItems().add(curDragTargetIndex + (curIndex <= curDragTargetIndex ? -1 : 0), t);
                    }
                }
                curDragTargetIndex = -1;
            });
        }
    }

    // Can be overridden by subclasses
    protected boolean validTargetPosition(int index)
    {
        return true;
    }

    @OnThread(Tag.FXPlatform)
    private static <T> void setTargetBelow(DeletableListView<T>.DeletableListCell cell, boolean on)
    {
        FXUtility.setPseudoclass(cell.contentPane, "target-below", true);
    }

    @OnThread(Tag.FXPlatform)
    private static <T> void setTargetAbove(DeletableListView<T>.DeletableListCell cell, boolean on)
    {
        FXUtility.setPseudoclass(cell.contentPane, "target-above", true);
    }
    */
}
