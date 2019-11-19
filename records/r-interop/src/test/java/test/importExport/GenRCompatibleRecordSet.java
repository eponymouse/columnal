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
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import test.importExport.GenRCompatibleRecordSet.RCompatibleRecordSet;
import utility.Either;
import utility.SimulationFunction;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class GenRCompatibleRecordSet extends Generator<RCompatibleRecordSet>
{
    public static class RCompatibleRecordSet
    {
        public final KnownLengthRecordSet recordSet;
        public final TypeManager typeManager;

        public RCompatibleRecordSet(KnownLengthRecordSet recordSet, TypeManager typeManager)
        {
            this.recordSet = recordSet;
            this.typeManager = typeManager;
        }
    }
    
    public GenRCompatibleRecordSet()
    {
        super(RCompatibleRecordSet.class);
    }

    @Override
    public RCompatibleRecordSet generate(SourceOfRandomness random, GenerationStatus status)
    {
        GenDataTypeMaker gen = new GenDataTypeMaker(ImmutableSet.of(TypeKinds.NUM_TEXT_TEMPORAL, TypeKinds.MAYBE_UNNESTED, TypeKinds.NEW_TAGGED_NO_INNER, TypeKinds.BOOLEAN), true);

        int numColumns = 1 + random.nextInt(10);
        int numRows = random.nextInt(20);

        int nextCol[] = new int[] {0};
        try
        {
            DataTypeMaker dataTypeMaker = gen.generate(random, status);
            return new RCompatibleRecordSet(new KnownLengthRecordSet(DataTestUtil.<SimulationFunction<RecordSet, EditableColumn>>makeList(random, numColumns, numColumns, () -> {
                DataTypeAndValueMaker type = dataTypeMaker.makeType();
                @SuppressWarnings("identifier")
                @ExpressionIdentifier String columnId = "Col " + nextCol[0]++;
                return type.getDataType().makeImmediateColumn(new ColumnId(columnId), DataTestUtil.<Either<String, @Value Object>>makeList(random, numRows, numRows, () -> Either.<String, @Value Object>right(type.makeValue())), type.makeValue());
            }), numRows), dataTypeMaker.getTypeManager());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
