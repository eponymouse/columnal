package test.importExport;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Column;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.EditableColumn;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.rinterop.RData;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import utility.Either;
import utility.SimulationFunction;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class GenRCompatibleRecordSet extends Generator<KnownLengthRecordSet>
{
    public GenRCompatibleRecordSet()
    {
        super(KnownLengthRecordSet.class);
    }

    @Override
    public KnownLengthRecordSet generate(SourceOfRandomness random, GenerationStatus status)
    {
        GenDataTypeMaker gen = new GenDataTypeMaker(ImmutableSet.of(TypeKinds.NUM_TEXT_TEMPORAL, TypeKinds.MAYBE, TypeKinds.BOOLEAN), true);

        int numColumns = 1 + random.nextInt(10);
        int numRows = random.nextInt(20);

        int nextCol[] = new int[] {0};
        try
        {
            return new KnownLengthRecordSet(DataTestUtil.<SimulationFunction<RecordSet, EditableColumn>>makeList(random, numColumns, numColumns, () -> {
                DataTypeAndValueMaker type = gen.generate(random, status).makeType();
                @SuppressWarnings("identifier")
                @ExpressionIdentifier String columnId = "Col " + nextCol[0]++;
                return type.getDataType().makeImmediateColumn(new ColumnId(columnId), DataTestUtil.<Either<String, @Value Object>>makeList(random, numRows, numRows, () -> Either.<String, @Value Object>right(type.makeValue())), type.makeValue());
            }), numRows);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
