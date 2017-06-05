package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.DataTypeValue.SpecificDataTypeVisitorGet;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenDataType;
import test.gen.GenDataType.GenTaggedType;
import test.gen.GenRandom;
import test.gen.GenTypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 05/06/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropStorageSet
{
    @Property
    @OnThread(Tag.Simulation)
    public void testSet(@From(GenTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen typeAndValueGen, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        EditableRecordSet recordSet = new EditableRecordSet(Collections.singletonList(typeAndValueGen.getType().makeImmediateColumn(new ColumnId("C"))::apply), () -> 0);
        Column c = recordSet.getColumns().get(0);
        assertEquals(0, c.getLength());
        recordSet.insertRows(0, 10);
        assertEquals(10, c.getLength());

        int rowIndex = r.nextInt(10);
        @Value Object value = typeAndValueGen.makeValue();
        DataTypeValue columnType = c.getType();
        setValue(rowIndex, value, columnType);
        TestUtil.assertValueEqual("Type: " + typeAndValueGen.getType() + " index " + rowIndex, value, c.getType().getCollapsed(rowIndex));

        //TODO test overwriting and reverting
    }

    private static void setValue(int rowIndex, Object value, DataTypeValue typeValue) throws InternalException, UserException
    {
        typeValue.applyGet(new DataTypeVisitorGet<Void>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public Void number(GetValue<Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                g.set(rowIndex, (Number)value);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void text(GetValue<String> g) throws InternalException, UserException
            {
                g.set(rowIndex, (String)value);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                g.set(rowIndex, (Boolean) value);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> g) throws InternalException, UserException
            {
                g.set(rowIndex, (TemporalAccessor) value);
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                TaggedValue taggedValue = (TaggedValue)value;
                g.set(rowIndex, taggedValue.getTagIndex());
                @Nullable DataTypeValue innerType = tagTypes.get(((TaggedValue) value).getTagIndex()).getInner();
                if (innerType != null)
                {
                    @Nullable @Value Object innerValue = ((TaggedValue) value).getInner();
                    if (innerValue == null)
                        throw new InternalException("Inner value present but no slot for it");
                    setValue(rowIndex, innerValue, innerType);
                }
                return null;
            }

            @Override
            public Void tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                Object[] tuple = (Object[])value;
                for (int i = 0; i < types.size(); i++)
                {
                    setValue(rowIndex, tuple[i], types.get(i));
                }
                return null;
            }

            @Override
            @OnThread(Tag.Simulation)
            public Void array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                if (inner == null)
                    throw new InternalException("Attempting to set value in empty array");
                ListEx listEx = (ListEx)value;
                g.set(rowIndex, new Pair<>(listEx.size(), DataTypeUtility.listToType(inner, listEx)));
                return null;
            }
        });
    }
}
