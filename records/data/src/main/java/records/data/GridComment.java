package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table.Saver;
import records.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GridComment
{
    // No escapes
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private String content;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private CellPosition position;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private int widthInColumns;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private int heightInColumns;
    
    @OnThread(Tag.FXPlatform)
    private @MonotonicNonNull GridCommentDisplayBase display;
    
    //@OnThread(value = Tag.Any, requireSynchronized = true)
    //private @MonotonicNonNull GridCommentDisplayBase display;


    public GridComment(String content, CellPosition position, int widthInColumns, int heightInColumns)
    {
        this.content = content;
        this.position = position;
        this.widthInColumns = widthInColumns;
        this.heightInColumns = heightInColumns;
    }

    public void save(Saver saver)
    {
        String commentSrc;
        synchronized (this)
        {
            commentSrc = "COMMENT CONTENT @BEGIN\n" + GrammarUtility.escapeChars(content) + "\n@END CONTENT\nDISPLAY @BEGIN\nPOSITION " + IntStream.of(position.columnIndex, position.rowIndex, widthInColumns, heightInColumns).mapToObj(Integer::toString).collect(Collectors.joining(" ")) + "\n@END DISPLAY\n@END COMMENT";
        }
        saver.saveComment(commentSrc);
    }

    @OnThread(Tag.Any)
    public synchronized String getContent()
    {
        return content;
    }

    @OnThread(Tag.Any)
    public synchronized void setContent(String content)
    {
        this.content = content;
    }

    @OnThread(Tag.FXPlatform)
    public @Nullable GridCommentDisplayBase getDisplay()
    {
        return display;
    }
    
    @OnThread(Tag.FXPlatform)
    public void setDisplay(GridCommentDisplayBase display)
    {
        this.display = display;
    }

    @OnThread(Tag.Any)
    public synchronized CellPosition getPosition()
    {
        return position;
    }

    @OnThread(Tag.Any)
    public synchronized CellPosition getBottomRight()
    {
        return position.offsetByRowCols(heightInColumns - 1, widthInColumns - 1);
    }

    @OnThread(Tag.Any)
    public synchronized void setBottomRight(CellPosition bottomRight)
    {
        this.widthInColumns = Math.max(1, bottomRight.columnIndex - getPosition().columnIndex + 1);
        this.heightInColumns = Math.max(1, bottomRight.rowIndex - getPosition().rowIndex + 1);
    }

    /**
     * Slightly ham-fisted way to break the data->gui module dependency
     * while still letting Table store a link to its display.
     */
    public static interface GridCommentDisplayBase
    {
        
    }

    @Override
    public synchronized boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GridComment that = (GridComment) o;
        return widthInColumns == that.widthInColumns &&
                heightInColumns == that.heightInColumns &&
                content.equals(that.content) &&
                position.equals(that.position);
    }

    @Override
    public synchronized int hashCode()
    {
        return Objects.hash(content, position, widthInColumns, heightInColumns);
    }
}
