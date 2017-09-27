package records.importers;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import records.gui.stable.ReadOnlyStringColumnHandler;
import records.gui.stable.StableView.ColumnHandler;
import records.importers.gui.ImportChoicesDialog.SourceInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;

import java.util.List;

public class ImporterUtility
{
    //package-visible
    static SourceInfo makeSourceInfo(List<List<String>> vals)
    {
        ImmutableList.Builder<Pair<String, ColumnHandler>> columnHandlers = ImmutableList.builder();
        if (!vals.isEmpty())
        {
            int widest = vals.stream().mapToInt(l -> l.size()).max().orElse(0);
            for (int columnIndex = 0; columnIndex < widest; columnIndex++)
            {
                int columnIndexFinal = columnIndex;
                columnHandlers.add(new Pair<>("Column " + (columnIndex + 1), new ReadOnlyStringColumnHandler()
                {
                    @Override
                    @OnThread(Tag.FXPlatform)
                    public void fetchValueForRow(int rowIndex, FXPlatformConsumer<String> withValue)
                    {
                        String s;
                        try
                        {
                            s = vals.get(rowIndex).get(columnIndexFinal);
                        }
                        catch (IndexOutOfBoundsException e)
                        {
                            s = "<Missing>";
                        }
                        withValue.consume(s);
                    }
                }));
            }
        }
        return new SourceInfo(columnHandlers.build(), vals.size());
    }

    // Pads each row with extra blanks so that all rows have the same length
    // Modifies list (and inner lists) in-place.
    public static void rectangularise(List<List<String>> vals)
    {
        int maxRowLength = vals.stream().mapToInt(l -> l.size()).max().orElse(0);
        for (List<String> row : vals)
        {
            while (row.size() < maxRowLength)
                row.add("");
        }
    }
}
