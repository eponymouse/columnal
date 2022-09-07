package test.importExport;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.DataTestUtil;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.TypeManager;
import records.rinterop.ConvertFromR.TableType;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import test.importExport.GenRCompatibleRecordSet.RCompatibleRecordSet;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;

import static org.junit.Assert.assertEquals;

public class GenRCompatibleRecordSet extends Generator<RCompatibleRecordSet>
{
    public static class RCompatibleRecordSet
    {
        public final KnownLengthRecordSet recordSet;
        public final TypeManager typeManager;
        public final ImmutableSet<TableType> supportedTableTypes;

        public RCompatibleRecordSet(KnownLengthRecordSet recordSet, TypeManager typeManager, ImmutableSet<TableType> supportedTableTypes)
        {
            this.recordSet = recordSet;
            this.typeManager = typeManager;
            this.supportedTableTypes = supportedTableTypes;
        }
    }
    
    public GenRCompatibleRecordSet()
    {
        super(RCompatibleRecordSet.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public RCompatibleRecordSet generate(SourceOfRandomness random, GenerationStatus status)
    {
        boolean tibbleOnly = random.nextBoolean();
        ImmutableSet<TypeKinds> core = ImmutableSet.of(TypeKinds.NUM_TEXT_TEMPORAL, TypeKinds.MAYBE_UNNESTED, TypeKinds.NEW_TAGGED_NO_INNER, TypeKinds.BOOLEAN);
        GenDataTypeMaker gen = new GenDataTypeMaker(
                tibbleOnly ? Utility.<TypeKinds>appendToSet(Utility.<TypeKinds>appendToSet(core, TypeKinds.LIST), TypeKinds.RECORD) : core, 
                true);

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
            }), numRows), dataTypeMaker.getTypeManager(), tibbleOnly ? ImmutableSet.of(TableType.TIBBLE) : ImmutableSet.of(TableType.DATA_FRAME, TableType.TIBBLE));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
