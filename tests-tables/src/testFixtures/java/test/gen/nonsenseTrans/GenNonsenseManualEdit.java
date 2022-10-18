/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gen.nonsenseTrans;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import test.functions.TFunctionUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.transformations.ManualEdit;
import xyz.columnal.transformations.ManualEdit.ColumnReplacementValues;
import test.DummyManager;
import test.Transformation_Mgr;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;

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
            
            Pair<TableId, TableId> ids = TBasicUtil.generateTableIdPair(random);
            DummyManager mgr = TFunctionUtil.managerWithTestTypes().getFirst();
            HashMap<ColumnId, ColumnReplacementValues> replacements = new HashMap<>();
            
            
            @Nullable TypeAndValueGen keyColumn = random.nextInt(4) == 1 ? null : genTypeAndValueGen.generate(random, status);
            if (keyColumn != null)
                mgr.getTypeManager()._test_copyTaggedTypesFrom(keyColumn.getTypeManager());
            @Nullable Pair<ColumnId, DataType> replacementKey = keyColumn == null ? null : new Pair<>(TBasicUtil.generateColumnId(random), keyColumn.getType());
            
            int columnsAffected = random.nextInt(0, 5);
            for (int i = 0; i < columnsAffected; i++)
            {
                ColumnId columnId = TBasicUtil.generateColumnId(random);

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
              
                ImmutableList<Pair<@Value Object, Either<String, @Value Object>>> ps = TBasicUtil.<Pair<@Value Object, Either<String, @Value Object>>>makeList(random, 1, 5, () -> new Pair<>(keyColumn == null ? DataTypeUtility.value(random.nextInt()) : keyColumn.makeValue(), random.nextInt(5) == 1 ? Either.<String, @Value Object>left("#" + random.nextInt()) : Either.<String, @Value Object>right(typeAndValueGen.makeValue())));
                
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
