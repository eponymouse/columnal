package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryBooleanColumn;
import records.data.RecordSet;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Created by neil on 30/05/2017.
 */
public class GenEditableColumn extends Generator<EditableColumn>
{
    public GenEditableColumn()
    {
        super(EditableColumn.class);
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public EditableColumn generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        try
        {
            final int length =sourceOfRandomness.nextInt(5000);
            final FunctionInt<RecordSet, EditableColumn> create;
            switch (sourceOfRandomness.nextInt(1))
            {
                case 0:
                    create = rs -> new MemoryBooleanColumn(rs, new ColumnId("C"), make(length, sourceOfRandomness::nextBoolean));
                    break;
                default:
                    throw new InternalException("Messed up the test count!");
            }
            RecordSet recordSet = new EditableRecordSet(Collections.singletonList(create), () -> length);
            return (EditableColumn)recordSet.getColumns().get(0);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static <T> List<T> make(int length, Supplier<T> makeOne)
    {
        List<T> r = new ArrayList<>(length);
        for (int i = 0; i < length; i++)
            r.add(makeOne.get());
        return r;
    }
}
