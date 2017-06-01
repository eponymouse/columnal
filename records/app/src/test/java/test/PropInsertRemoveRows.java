package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenEditableColumn;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 30/05/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropInsertRemoveRows
{
    @Property
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
        ((EditableRecordSet)column.getRecordSet()).insertRows(insertAtIndex, insertCount);

        for (int i = 0; i < insertAtIndex; i++)
        {
            assertTrue(column.indexValid(i));
            assertEquals("Comparing values at index " + i + "\n  " + DataTypeUtility.valueToString(prevValues.get(i)) + " vs\n  " + DataTypeUtility.valueToString(column.getType().getCollapsed(i)), 0, Utility.compareValues(prevValues.get(i), column.getType().getCollapsed(i)));
        }
        for (int i = insertAtIndex; i < insertAtIndex + insertCount; i++)
        {
            assertTrue("Valid index: " + i + " insertAt: " + insertAtIndex + " count " + insertCount, column.indexValid(i));
            // Don't check here what they are, just that they are all same:
            assertEquals("Comparing " + column.getType() + ": " + i + " insertAt: " + insertAtIndex + " count " + insertCount + "\n  " + DataTypeUtility.valueToString(column.getType().getCollapsed(insertAtIndex)) + " vs\n  " + DataTypeUtility.valueToString(column.getType().getCollapsed(i)), 0, Utility.compareValues(column.getType().getCollapsed(insertAtIndex), column.getType().getCollapsed(i)));
        }
        for (int i = insertAtIndex; i < prevValues.size(); i++)
        {
            assertTrue("Valid index " + i + " with insertAtIndex " + insertAtIndex + " and count " + insertCount + " and prev " + prevValues.size(), column.indexValid(i + insertCount));
            assertEquals("Comparing " + column.getType() + " index " + i + " with insertAtIndex " + insertAtIndex + " and count " + insertCount + "\n  " + DataTypeUtility.valueToString(prevValues.get(i)) + " vs\n  " + DataTypeUtility.valueToString(column.getType().getCollapsed(i + insertCount)), 0, Utility.compareValues(prevValues.get(i), column.getType().getCollapsed(i + insertCount)));
        }
        assertFalse(column.indexValid(prevValues.size() + insertCount + 1));
        // TODO add revert test
    }


    // TODO add removeRows test
}
