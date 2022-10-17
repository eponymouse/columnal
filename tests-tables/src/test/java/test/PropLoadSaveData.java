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

package test;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Assert;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.GridComment;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.TableManager.Loaded;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gen.GenTableManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.SaveTag;
import xyz.columnal.id.TableId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 07/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveData
{
    @Property(trials = 20)
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public void testImmediate(
            @From(GenTableManager.class) TableManager mgr1,
            @From(GenTableManager.class) TableManager mgr2,
            @From(GenImmediateData.class) @NumTables(maxTables = 4) GenImmediateData.ImmediateData_Mgr original,
            @From(GenRandom.class) Random r)
        throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException
    {
        SourceOfRandomness sourceOfRandomness = new SourceOfRandomness(r);
        int[] next = new int[] {1};
        ImmutableList<GridComment> comments = TBasicUtil.makeList(sourceOfRandomness, 0, 5, () -> {
            String content = IntStream.range(0, r.nextInt(3)).mapToObj(_n -> TBasicUtil.generateColumnIds(sourceOfRandomness, r.nextInt(12)).stream().map(c -> c.getRaw()).collect(Collectors.joining(" "))).collect(Collectors.joining("\n"));
            
            return new GridComment(SaveTag.generateRandom(), content, new CellPosition(r.nextInt(100) * AbsRowIndex.ONE, (next[0]++ * 1000) * AbsColIndex.ONE), 1 + r.nextInt(20), 1 + r.nextInt(20));
        });
        for (GridComment comment : comments)
        {
            original.mgr.addComment(comment);
        }
        String saved = TTableUtil.save(original.mgr);
        try
        {
            //Assume users destroy leading whitespace:
            String savedMangled = saved.replaceAll("\n +", "\n");
            Pair<Map<TableId, Table>, List<GridComment>> loaded = toMap(mgr1.loadAll(savedMangled, w -> {}));
            String savedAgain = TTableUtil.save(mgr1);
            Pair<Map<TableId, Table>, List<GridComment>> loadedAgain = toMap(mgr2.loadAll(savedAgain, w -> {}));


            assertEquals(saved, savedAgain);
            assertEquals(toMap(new Loaded(ImmutableList.of(), original.data, comments)), loaded);
            assertEquals(loaded, loadedAgain);
            Assert.assertEquals(original.mgr.getTypeManager().getKnownTaggedTypes(), mgr1.getTypeManager().getKnownTaggedTypes());
            Assert.assertEquals(original.mgr.getTypeManager().getKnownTaggedTypes(), mgr2.getTypeManager().getKnownTaggedTypes());
            Assert.assertEquals(original.mgr.getUnitManager().getAllDeclared(), mgr1.getUnitManager().getAllDeclared());
            Assert.assertEquals(original.mgr.getUnitManager().getAllDeclared(), mgr2.getUnitManager().getAllDeclared());
        }
        catch (Throwable t)
        {
            System.err.println("Original:\n" + saved);
            System.err.flush();
            throw t;
        }
    }

    private static <T extends  Table> Pair<Map<TableId, Table>, List<GridComment>> toMap(Loaded tablesAndComments)
    {
        return new Pair<>(tablesAndComments.loadedTables.stream().collect(Collectors.<Table, TableId, Table>toMap(Table::getId, Function.identity())), tablesAndComments.gridComments);
    }

    @Property(trials = 20)
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public void testImmediateInclError(
            //@When(seed=4184268094197695469L)
            @From(GenTableManager.class) TableManager mgr1,
            //@When(seed=6457512938358589961L)
            @From(GenTableManager.class) TableManager mgr2,
            //@When(seed=1860968919937845412L)
            @From(GenImmediateData.class) @NumTables(maxTables = 4) GenImmediateData.ImmediateData_Mgr original,
            //@When(seed=2075546916866037623L)
            @From(GenRandom.class) Random r)
            throws Exception
    {
        TBasicUtil.printSeedOnFail(() -> {
            // Introduce some errors:
            for (int i = 0; i < 20; i++)
            {
                ImmediateDataSource table = original.data.get(r.nextInt(original.data.size()));
                if (table.getData().getLength() > 0)
                {
                    int row = r.nextInt(table.getData().getLength());
                    int colIndex = r.nextInt(table.getData().getColumns().size());
                    setInvalid(table.getData().getColumns().get(colIndex), row, r);
                }
            }

            testImmediate(mgr1, mgr2, original, r);
        });
    }

    @OnThread(Tag.Simulation)
    private void setInvalid(Column column, int row, Random r) throws UserException, InternalException
    {
        column.getType().setCollapsed(row, Either.left(r.nextInt(10) == 1 ? "@END" : ("@" + r.nextInt())));
    }

}
