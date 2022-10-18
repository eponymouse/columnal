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

package test.gui.trait;

import com.google.common.primitives.Ints;
import javafx.scene.input.KeyCode;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
import test.gui.TFXUtil;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.grid.VirtualGrid;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public interface CheckCSVTrait extends FxRobotInterface, ScrollToTrait, ClickOnTableHeaderTrait, FocusOwnerTrait
{
    @OnThread(Tag.Any)
    default void exportToCSVAndCheck(VirtualGrid virtualGrid, TableManager tableManager, String prefix, List<Pair<String, List<String>>> expected, TableId tableId) throws IOException, UserException, InternalException
    {
        triggerTableHeaderContextMenu(virtualGrid, tableManager, tableId);
        clickOn(".id-tableDisplay-menu-exportData");
        WaitForAsyncUtils.waitForFxEvents();
        
        // Pick CSV:
        sleep(200);
        clickOn(".ok-button");
        sleep(200);

        File destCSV = File.createTempFile("dest", "csv");
        destCSV.deleteOnExit();

        // Enter file name into first dialog:
        correctTargetWindow().write(destCSV.getAbsolutePath());
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
        TFXUtil.sleep(2000);
        // Wait for work queue to be empty:
        TFXUtil.sim_(() -> {});

        // Now load CSV and check it:
        String actualCSV = FileUtils.readFileToString(destCSV, Charset.forName("UTF-8"));
        TBasicUtil.assertEqualsText(prefix, toCSV(expected), actualCSV);
    }

    @OnThread(Tag.Any)
    static String toCSV(List<Pair<String, List<String>>> csvColumns)
    {
        Set<Integer> columnLengths = csvColumns.stream().map(p -> p.getSecond().size()).collect(Collectors.toSet());
        Assert.assertEquals("Column lengths differ (column lengths: " + Utility.listToString(new ArrayList<>(columnLengths)) + ")", 1, columnLengths.size());

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
                r.add(DataTypeUtility.valueToString(type.getCollapsed(i)));
        }
        return r;
    }
}
