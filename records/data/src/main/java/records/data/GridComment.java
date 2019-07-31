package records.data;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table.Saver;
import records.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GridComment
{
    // No escapes
    private final String content;
    private CellPosition position;
    private int widthInColumns;
    private int heightInColumns;
    
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
        saver.saveComment("COMMENT CONTENT @BEGIN\n" + GrammarUtility.escapeChars(content) + "\n@END CONTENT\nDISPLAY @BEGIN\nPOSITION " + IntStream.of(position.columnIndex, position.rowIndex, widthInColumns, heightInColumns).mapToObj(Integer::toString).collect(Collectors.joining(" ")) + "\n@END DISPLAY\n@END COMMENT");
    }

    /**
     * Slightly ham-fisted way to break the data->gui module dependency
     * while still letting Table store a link to its display.
     */
    public static interface GridCommentDisplayBase
    {
        @OnThread(Tag.FXPlatform)
        public void loadPosition(CellPosition position, int widthInCells, int heightInCells);

        @OnThread(Tag.Any)
        public CellPosition getMostRecentPosition();

        @OnThread(Tag.FXPlatform)
        public CellPosition getBottomRightIncl();
    }

    @Override
    public boolean equals(@Nullable Object o)
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
    public int hashCode()
    {
        return Objects.hash(content, position, widthInColumns, heightInColumns);
    }
}
