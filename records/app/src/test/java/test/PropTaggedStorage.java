package test;

import com.pholser.junit.quickcheck.From;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.DataTypeValue.SpecificDataTypeVisitorGet;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenDataType.GenTaggedType;
import utility.Pair;
import utility.TaggedValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 05/06/2017.
 */
public class PropTaggedStorage
{
    // TODO This should be a testSet for all types, not just one at a time.
    public void testSet(@From(GenTaggedType.class)DataType type) throws UserException, InternalException
    {
        EditableRecordSet recordSet = new EditableRecordSet(Collections.singletonList(type.makeImmediateColumn(new ColumnId("C"))), () -> 0);
        Column c = recordSet.getColumns().get(0);
        assertEquals(0, c.getLength());
        recordSet.insertRows(0, 10);
        assertEquals(10, c.getLength());

        Pair<List<TagType<DataTypeValue>>, GetValue<Integer>> details = c.getType().applyGet(new SpecificDataTypeVisitorGet<Pair<List<TagType<DataTypeValue>>, GetValue<Integer>>>(new InternalException("Wrong type!")) {
            @Override
            public Pair<List<TagType<DataTypeValue>>, GetValue<Integer>> tagged(TypeId typeName, List<TagType<DataTypeValue>> tags, GetValue<Integer> g) throws InternalException, UserException
            {
                return new Pair<>(tags, g);
            }
        });
        List<TaggedValue> valuesToSet = new ArrayList<>();
        for (int i = 0; i < 10; i++)
        {
            details.getSecond().set(i, valuesToSet.get(i).getTagIndex());
            details.getFirst().get(valuesToSet.get(i).getTagIndex());
        }
    }
}
