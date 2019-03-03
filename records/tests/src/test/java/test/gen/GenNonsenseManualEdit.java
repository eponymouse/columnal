package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.datatype.DataType;
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
            
            
            TypeAndValueGen replacingColumn = genTypeAndValueGen.generate(random, status);
            mgr.getTypeManager()._test_copyTaggedTypesFrom(replacingColumn.getTypeManager());
            @Nullable Pair<ColumnId, DataType> replacementKey = new Pair<>(TestUtil.generateColumnId(random), replacingColumn.getType());
            
            int columnsAffected = random.nextInt(0, 5);
            for (int i = 0; i < columnsAffected; i++)
            {
                ColumnId columnId = TestUtil.generateColumnId(random);

                TypeAndValueGen typeAndValueGen = genTypeAndValueGen.generate(random, status);
                mgr.getTypeManager()._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
              
                ImmutableList<Pair<@Value Object, @Value Object>> ps = TestUtil.<Pair<@Value Object, @Value Object>>makeList(random, 1, 5, () -> new Pair<>(replacingColumn.makeValue(), typeAndValueGen.makeValue()));
                
                replacements.put(columnId, new ColumnReplacementValues(typeAndValueGen.getType(), ps));
            }

            
            return new Transformation_Mgr(mgr, new ManualEdit(mgr, new InitialLoadDetails(ids.getFirst(), null, null), ids.getSecond(), replacementKey, ImmutableMap.copyOf(replacements)));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
        
    }
}
