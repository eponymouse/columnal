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

package test.gen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.functions.TFunctionUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.*;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.ColumnId;
import xyz.columnal.transformations.Aggregate;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.Filter;
import xyz.columnal.transformations.HideColumns;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.Sort.Direction;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.TypeState;
import test.DummyManager;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;

import java.util.ArrayList;

// Most of the transformations are currently identity transform or similar,
// but that's fine for the manual edit test.
@OnThread(Tag.Simulation)
public class GenDataAndTransforms extends Generator<TableManager>
{
    public GenDataAndTransforms()
    {
        super(TableManager.class);
    }

    @Override
    public TableManager generate(SourceOfRandomness random, GenerationStatus status)
    {
        try
        {
            DataTypeMaker dataTypeMaker = new GenDataTypeMaker(true).generate(random, status);
            TableManager mgr = new DummyManager();
            
            // Must have at least one immediate and one transformation (at end):
            addImmediateTable(mgr, dataTypeMaker, random);

            // Add a mix of others:
            int other = random.nextInt(0, 10);
            for (int i = 0; i < other; i++)
            {
                if (random.nextInt(3) == 1)
                    addImmediateTable(mgr, dataTypeMaker, random);
                else
                    addTransformation(mgr, dataTypeMaker, random);
            }
            addTransformation(mgr, dataTypeMaker, random);

            // Must copy at end, as types are generated while adding tables:
            mgr.getTypeManager()._test_copyTaggedTypesFrom(dataTypeMaker.getTypeManager());
            
            return mgr;
        }
        catch (Throwable t)
        {
            throw new RuntimeException(t);
        }
    }

    private void addTransformation(TableManager mgr, DataTypeMaker dataTypeMaker, SourceOfRandomness r) throws InternalException, UserException
    {
        ImmutableList.Builder<ExSupplier<Transformation>> genTransforms = ImmutableList.builder();
        
        genTransforms.addAll(ImmutableList.<ExSupplier<Transformation>>of(
        // Sort:
        () -> {
            Table src = pickSrc(mgr, r);
            ImmutableList.Builder<Pair<ColumnId, Direction>> sortBy = ImmutableList.builder();
            ArrayList<ColumnId> possibles = new ArrayList<>(src.getData().getColumnIds());
            int numSortBy = r.nextInt(0, possibles.size());
            for (int i = 0; i < numSortBy; i++)
            {
                sortBy.add(new Pair<>(possibles.remove(r.nextInt(possibles.size())), r.nextBoolean() ? Direction.ASCENDING : Direction.DESCENDING));
            }
            return new Sort(mgr, TFunctionUtil.ILD, src.getId(), sortBy.build());
        },
        // Filter:
        () -> {
            Table src = pickSrc(mgr, r);
            return new Filter(mgr, TFunctionUtil.ILD, src.getId(), new BooleanLiteral(true));
        },
        // Join:
        /* TODO avoid issue with duplicate column names from merging with self
        () -> {
            Table srcLeft = pickSrc(mgr, r);
            Table srcRight = pickSrc(mgr, r);
            return new Join(mgr, TestUtil.ILD, srcLeft.getId(), srcRight.getId(), true, ImmutableList.of());
        },
        */
        // Hide:
        () -> {
            Table src = pickSrc(mgr, r);
            return new HideColumns(mgr, TFunctionUtil.ILD, src.getId(), ImmutableList.of());
        },
        // Calculate:
        () -> {
            Table src = pickSrc(mgr, r);
            return new Calculate(mgr, TFunctionUtil.ILD, src.getId(), ImmutableMap.of());
        },
        // Concatenate:
        /* TODO fix issues with columns having different types
        () -> {
            Table srcTop = pickSrc(mgr, r);
            Table srcBottom = pickSrc(mgr, r);
            return new Concatenate(mgr, TestUtil.ILD, ImmutableList.of(srcTop.getId(), srcBottom.getId()), IncompleteColumnHandling.DEFAULT, true);
        },
        */
        // Aggregate:
        () -> {
            Table src = pickSrc(mgr, r);
            return new Aggregate(mgr, TFunctionUtil.ILD, src.getId(), ImmutableList.of(new Pair<>(new ColumnId("Count"), IdentExpression.load(TypeState.GROUP_COUNT))), ImmutableList.of());
        }//,
        // R:
                /*
        () -> {
            Table src = pickSrc(mgr, r);
            return new RTransformation(mgr, TestUtil.ILD, ImmutableList.of(src.getId()), ImmutableList.of(), "rnorm(100)");
        }
        */
        ));


        ImmutableList<ExSupplier<Transformation>> l = genTransforms.build();
        mgr.record(l.get(r.nextInt(l.size())).get());
    }

    private Table pickSrc(TableManager mgr, SourceOfRandomness r)
    {
        ImmutableList<Table> allTables = mgr.getAllTables();
        return allTables.get(r.nextInt(allTables.size()));
    }

    private void addImmediateTable(TableManager mgr, DataTypeMaker dataTypeMaker, SourceOfRandomness r) throws UserException, InternalException
    {
        int numRows = r.nextInt(1, 20);
        int numCols = r.nextInt(1, 6);

        ImmutableList.Builder<SimulationFunction<RecordSet, EditableColumn>> cols = ImmutableList.builderWithExpectedSize(numCols);
        
        for (int i = 0; i < numCols; i++)
        {
            DataTypeAndValueMaker t = dataTypeMaker.makeType();
            cols.add(ColumnUtility.makeImmediateColumn(t.getDataType(), new ColumnId(IdentifierUtility.identNum("Col", i)), TBasicUtil.makeList(r, numRows, numRows, () -> Either.right(t.makeValue())), t.makeValue()));
        }
        
        mgr.record(new ImmediateDataSource(mgr, TFunctionUtil.ILD,new EditableRecordSet(cols.build(), () -> numRows)));
    }
}
