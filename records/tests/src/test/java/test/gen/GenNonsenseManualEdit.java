package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.transformations.ManualEdit;
import records.transformations.ManualEdit.ColumnReplacementValues;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.HashMap;

public class GenNonsenseManualEdit extends Generator<Transformation_Mgr>
{
    @OnThread(Tag.Any)
    public GenNonsenseManualEdit()
    {
        super(Transformation_Mgr.class);
    }
    
    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness random, GenerationStatus status)
    {
        try
        {
            GenTypeAndValueGen genTypeAndValueGen = new GenTypeAndValueGen();
            
            Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(random);
            DummyManager mgr = TestUtil.managerWithTestTypes().getFirst();
            HashMap<ColumnId, ColumnReplacementValues> replacements = new HashMap<>();
            
            
            @Nullable TypeAndValueGen keyColumn = random.nextInt(4) == 1 ? null : genTypeAndValueGen.generate(random, status);
            if (keyColumn != null)
                mgr.getTypeManager()._test_copyTaggedTypesFrom(keyColumn.getTypeManager());
            @Nullable Pair<ColumnId, DataType> replacementKey = keyColumn == null ? null : new Pair<>(TestUtil.generateColumnId(random), keyColumn.getType());
            
            int columnsAffected = random.nextInt(0, 5);
            for (int i = 0; i < columnsAffected; i++)
            {
                ColumnId columnId = TestUtil.generateColumnId(random);

                TypeAndValueGen typeAndValueGen = genTypeAndValueGen.generate(random, status);
                try
                {
                    mgr.getTypeManager()._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
                }
                catch (IllegalStateException e)
                {
                    // Duplicate types; just skip
                    continue;
                }
              
                ImmutableList<Pair<@Value Object, Either<String, @Value Object>>> ps = DataTestUtil.<Pair<@Value Object, Either<String, @Value Object>>>makeList(random, 1, 5, () -> new Pair<>(keyColumn == null ? DataTypeUtility.value(random.nextInt()) : keyColumn.makeValue(), random.nextInt(5) == 1 ? Either.<String, @Value Object>left("#" + random.nextInt()) : Either.<String, @Value Object>right(typeAndValueGen.makeValue())));
                
                replacements.put(columnId, new ColumnReplacementValues(typeAndValueGen.getType(), ps));
            }

            
            return new Transformation_Mgr(mgr, new ManualEdit(mgr, new InitialLoadDetails(ids.getFirst(), null, null, null), ids.getSecond(), replacementKey, ImmutableMap.copyOf(replacements)));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
        
    }
}
