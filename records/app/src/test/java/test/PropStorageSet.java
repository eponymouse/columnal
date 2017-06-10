package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenRandom;
import test.gen.GenTypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 05/06/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropStorageSet
{
    @Property(trials = 1000)
    @OnThread(Tag.Simulation)
    public void testSet(@From(GenTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen typeAndValueGen, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        EditableRecordSet recordSet = new EditableRecordSet(Collections.singletonList(typeAndValueGen.getType().makeImmediateColumn(new ColumnId("C"))::apply), () -> 0);
        Column c = recordSet.getColumns().get(0);
        assertEquals(0, c.getLength());
        recordSet.insertRows(0, 10);
        assertEquals(10, c.getLength());

        Map<Integer, @Value Object> vals = new HashMap<>();

        // Do many writes:
        for (int i = 0; i < 10; i++)
        {
            int rowIndex = r.nextInt(10);
            @Value Object value = typeAndValueGen.makeValue();
            DataTypeValue columnType = c.getType();
            columnType.setCollapsed(rowIndex, value);
            TestUtil.assertValueEqual("Type: " + typeAndValueGen.getType() + " index " + rowIndex, value, c.getType().getCollapsed(rowIndex));
            vals.put(rowIndex, value);
        }
        // Test all at end, helps test post overwrites:
        for (Entry<Integer, Object> entry : vals.entrySet())
        {
            TestUtil.assertValueEqual("Type: " + typeAndValueGen.getType() + " index " + entry.getKey(), entry.getValue(), c.getType().getCollapsed(entry.getKey()));
        }
        // TODO test reverting
    }

}
