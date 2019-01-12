package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.initialization.qual.Initialized;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.SimulationFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 30/05/2017.
 */
public class GenEditableColumn extends GenValueBase<EditableColumn>
{
    public GenEditableColumn()
    {
        super(EditableColumn.class);
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public EditableColumn generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        GenDataType genDataType = new GenDataType(true);
        DataType type = genDataType.generate(sourceOfRandomness, generationStatus).dataType;
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        try
        {
            final @Initialized int length = sourceOfRandomness.nextInt(5000);
            List<Either<String, @Value Object>> values = new ArrayList<>();
            for (int i = 0; i < length; i++)
            {
                values.add(Either.right(makeValue(type)));
            }
            final SimulationFunction<RecordSet, EditableColumn> create = type.makeImmediateColumn(new ColumnId("C"), values, makeValue(type));
            @SuppressWarnings({"keyfor", "units"})
            RecordSet recordSet = new EditableRecordSet(Collections.singletonList(create), () -> length);
            return (EditableColumn)recordSet.getColumns().get(0);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
