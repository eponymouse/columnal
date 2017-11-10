package test.gui;

import com.google.common.primitives.Ints;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.NodeQuery;
import org.testfx.util.WaitForAsyncUtils;
import records.data.Table;
import records.data.TableId;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.TableDisplay;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Workers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public interface CheckCSVTrait extends FxRobotInterface, ScrollToTrait
{
    @OnThread(Tag.Simulation)
    default void exportToCSVAndCheck(String prefix, List<Pair<String, List<String>>> expected, TableId tableId) throws IOException, UserException, InternalException
    {
        // Bring table to front:
        clickOn("#id-menu-view").clickOn(".id-menu-view-find");
        write(tableId.getRaw());
        push(KeyCode.ENTER);

        NodeQuery tableMenuButton = lookup(".tableDisplay").match(t -> t instanceof TableDisplay && ((TableDisplay)t).getTable().getId().equals(tableId)).lookup(".id-tableDisplay-menu-button");
        scrollTo(tableMenuButton);
        @SuppressWarnings("nullness") // Will throw if null and fail test, which is fine
        @NonNull Node button = tableMenuButton.<Node>query();
        clickOn(button).clickOn(".id-tableDisplay-menu-exportToCSV");
        WaitForAsyncUtils.waitForFxEvents();

        File destCSV = File.createTempFile("dest", "csv");
        destCSV.deleteOnExit();

        // Enter file name into first dialog:
        write(destCSV.getAbsolutePath());
        push(KeyCode.ENTER);

        /* TODO add and handle options dialog
        // Press ENTER on second dialog:
        TestUtil.sleep(200);
        push(KeyCode.ENTER);

        // Dialog vanishes when export complete:
        while(lookup(".export-options-window") != null)
        {
            TestUtil.sleep(500);
        }
        */
        if (Workers._test_isOnWorkerThread())
        {
            WaitForAsyncUtils.waitForFxEvents();
            Workers._test_yield();
        }
        else
            TestUtil.sleep(2000);

        // Now load CSV and check it:
        String actualCSV = FileUtils.readFileToString(destCSV, Charset.forName("UTF-8"));
        TestUtil.assertEqualsText(prefix, toCSV(expected), actualCSV);
    }

    @OnThread(Tag.Any)
    static String toCSV(List<Pair<String, List<String>>> csvColumns)
    {
        Set<Integer> columnLengths = csvColumns.stream().map(p -> p.getSecond().size()).collect(Collectors.toSet());
        assertEquals("Column lengths differ (column lengths: " + Utility.listToString(new ArrayList<>(columnLengths)) + ")", 1, columnLengths.size());

        int length = columnLengths.iterator().next();

        StringBuilder s = new StringBuilder();
        for (int i = 0; i < csvColumns.size(); i++)
        {
            Pair<String, List<String>> csvColumn = csvColumns.get(i);
            s.append(quoteCSV(csvColumn.getFirst()));
            if (i < csvColumns.size() - 1)
                s.append(",");
        }
        s.append("\n");

        for (int row = 0; row < length; row++)
        {
            for (int i = 0; i < csvColumns.size(); i++)
            {
                Pair<String, List<String>> csvColumn = csvColumns.get(i);
                s.append(quoteCSV(csvColumn.getSecond().get(row)));
                if (i < csvColumns.size() - 1)
                    s.append(",");
            }
            s.append("\n");
        }

        return s.toString();
    }

    @OnThread(Tag.Any)
    static String quoteCSV(String original)
    {
        return "\"" + original.replace("\"", "\"\"\"") + "\"";
    }


    @OnThread(Tag.Simulation)
    static List<String> collapse(int length, DataTypeValue type, int... excluding) throws UserException, InternalException
    {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < length; i++)
        {
            if (!Ints.contains(excluding, i))
                r.add(DataTypeUtility.valueToString(type, type.getCollapsed(i), null));
        }
        return r;
    }
}
