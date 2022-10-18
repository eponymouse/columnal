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

package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.embed.swing.JFXPanel;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.GenEditableColumn;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 30/05/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropInsertRemoveRows
{
    @BeforeClass
    public static void initFX() throws InvocationTargetException, InterruptedException
    {
        // Initialise JavaFX:
        SwingUtilities.invokeAndWait(() -> new JFXPanel());
    }

    @Property(trials = 100)
    @OnThread(Tag.Simulation)
    public void insertRows(@From(GenEditableColumn.class) EditableColumn column, @From(GenRandom.class) Random r) throws InternalException, UserException
    {
        List<@Value Object> prevValues = new ArrayList<>();
        for (int i = 0; column.indexValid(i); i++)
        {
            prevValues.add(column.getType().getCollapsed(i));
        }

        int insertAtIndex = r.nextInt(column.getLength() + 1);
        int insertCount = r.nextInt(1000);

        // Do it via RecordSet to get the validIndex function updated:
        @Nullable SimulationRunnable revert = ((EditableRecordSet) column.getRecordSet()).insertRows(insertAtIndex, insertCount);

        for (int i = 0; i < insertAtIndex; i++)
        {
            assertTrue(column.indexValid(i));
            assertEquals("Comparing values at index " + i + "\n  " + DataTypeUtility._test_valueToString(prevValues.get(i)) + " vs\n  " + DataTypeUtility._test_valueToString(column.getType().getCollapsed(i)), 0, Utility.compareValues(prevValues.get(i), column.getType().getCollapsed(i)));
        }
        for (int i = insertAtIndex; i < insertAtIndex + insertCount; i++)
        {
            assertTrue("Valid index: " + i + " insertAt: " + insertAtIndex + " count " + insertCount, column.indexValid(i));
            // Don't check here what they are, just that they are all same:
            assertEquals("Comparing " + column.getType() + ": " + i + " insertAt: " + insertAtIndex + " count " + insertCount + "\n  " + DataTypeUtility._test_valueToString(column.getType().getCollapsed(insertAtIndex)) + " vs\n  " + DataTypeUtility._test_valueToString(column.getType().getCollapsed(i)), 0, Utility.compareValues(column.getType().getCollapsed(insertAtIndex), column.getType().getCollapsed(i)));
        }
        for (int i = insertAtIndex; i < prevValues.size(); i++)
        {
            assertTrue("Valid index " + i + " with insertAtIndex " + insertAtIndex + " and count " + insertCount + " and prev " + prevValues.size(), column.indexValid(i + insertCount));
            assertEquals("Comparing " + column.getType() + " index " + i + " with insertAtIndex " + insertAtIndex + " and count " + insertCount + "\n  " + DataTypeUtility._test_valueToString(prevValues.get(i)) + " vs\n  " + DataTypeUtility._test_valueToString(column.getType().getCollapsed(i + insertCount)), 0, Utility.compareValues(prevValues.get(i), column.getType().getCollapsed(i + insertCount)));
        }
        assertFalse(column.indexValid(prevValues.size() + insertCount));

        // Test revert:
        assertNotNull(revert);
        if (revert != null) // Not really needed because above will throw if it's null.  Satisfies checker.
            revert.run();
        assertEquals("Type: " + column.getType(), prevValues.size(), column.getLength());
        assertTrue(column.indexValid(prevValues.size() - 1));
        assertFalse(column.indexValid(prevValues.size()));

        for (int i = 0; i < prevValues.size(); i++)
        {
            assertTrue(column.indexValid(i));
            assertEquals("Comparing values at index " + i + " inserted at " + insertAtIndex + "\n  " + DataTypeUtility._test_valueToString(prevValues.get(i)) + " vs\n  " + DataTypeUtility._test_valueToString(column.getType().getCollapsed(i)), 0, Utility.compareValues(prevValues.get(i), column.getType().getCollapsed(i)));
        }
    }

    @Property(trials = 100)
    @OnThread(Tag.Simulation)
    public void removeRows(@From(GenEditableColumn.class) EditableColumn column, @From(GenRandom.class) Random r) throws InternalException, UserException
    {
        if (column.getLength() == 0)
            return; // Can't remove any rows.  Happens rarely.
        
        List<@Value Object> prevValues = new ArrayList<>();
        for (int i = 0; column.indexValid(i); i++)
        {
            prevValues.add(column.getType().getCollapsed(i));
        }

        int removeAtIndex = r.nextInt(column.getLength());
        int removeCount = r.nextInt(column.getLength() - removeAtIndex);

        // Do it via RecordSet to get the validIndex function updated:
        @Nullable SimulationRunnable revert = ((EditableRecordSet) column.getRecordSet()).removeRows(removeAtIndex, removeCount);

        for (int i = 0; i < removeAtIndex; i++)
        {
            assertTrue(column.indexValid(i));
            assertEquals("Comparing values at index " + i + "\n  " + DataTypeUtility._test_valueToString(prevValues.get(i)) + " vs\n  " + DataTypeUtility._test_valueToString(column.getType().getCollapsed(i)), 0, Utility.compareValues(prevValues.get(i), column.getType().getCollapsed(i)));
        }
        for (int i = removeAtIndex; i < prevValues.size() - removeCount; i++)
        {
            assertTrue("Valid index " + i + " with removeAtIndex " + removeAtIndex + " and count " + removeCount + " and prev " + prevValues.size(), column.indexValid(i));
            assertEquals("Comparing " + column.getType() + " index " + i + " with removeAtIndex " + removeAtIndex + " and count " + removeCount + "\n  " + DataTypeUtility._test_valueToString(prevValues.get(i + removeCount)) + " vs\n  " + DataTypeUtility._test_valueToString(column.getType().getCollapsed(i)), 0, Utility.compareValues(prevValues.get(i + removeCount), column.getType().getCollapsed(i)));
        }
        assertFalse(column.indexValid(prevValues.size() - removeCount));

        // Test revert:
        assertNotNull(revert);
        if (revert != null) // Not really needed because above will throw if it's null.  Satisfies checker.
            revert.run();
        assertEquals("Type: " + column.getType(), prevValues.size(), column.getLength());
        assertTrue(column.indexValid(prevValues.size() - 1));
        assertFalse(column.indexValid(prevValues.size()));

        for (int i = 0; i < prevValues.size(); i++)
        {
            assertTrue(column.indexValid(i));
            assertEquals("Comparing values at index " + i + " removed at " + removeAtIndex + "\n  " + DataTypeUtility._test_valueToString(prevValues.get(i)) + " vs\n  " + DataTypeUtility._test_valueToString(column.getType().getCollapsed(i)), 0, Utility.compareValues(prevValues.get(i), column.getType().getCollapsed(i)));
        }
    }
}
