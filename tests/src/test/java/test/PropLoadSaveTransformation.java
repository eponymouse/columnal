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

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.FullSaver;
import xyz.columnal.data.TableManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.nonsenseTrans.GenNonsenseTransformation;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 16/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveTransformation
{
    @Property(trials = 200)
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public void testLoadSaveTransformation(@From(GenNonsenseTransformation.class) TestUtil.Transformation_Mgr original)
        throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException
    {
        TableManager mgr1 = new DummyManager();
        TableManager mgr2 = new DummyManager();
        String saved = save(original.mgr);
        try
        {
            //Assume users destroy leading whitespace:
            String savedMangled = saved.replaceAll("\n +", "\n");
            Table loaded = mgr1.loadAll(savedMangled, w -> {}).loadedTables.get(0);
            String savedAgain = save(mgr1);
            Table loadedAgain = mgr2.loadAll(savedAgain, w -> {}).loadedTables.get(0);


            assertEquals(saved, savedAgain);
            assertEquals(original.transformation, loaded);
            assertEquals(loaded, loadedAgain);
        }
        catch (Throwable t)
        {
            System.err.println("Original:\n" + saved);
            System.err.flush();
            throw t;
        }
    }

    @OnThread(Tag.Simulation)
    private static String save(TableManager original) throws ExecutionException, InterruptedException, InvocationTargetException
    {
        // This whole bit is single-threaded:
        String[] r = new String[] {""};
        try
        {
            original.save(null, new FullSaver(null) {
                @Override
                public @OnThread(Tag.Simulation) void saveTable(String tableSrc)
                {
                    super.saveTable(tableSrc);
                    // May be called multiple times, but that's fine, we just need last one:
                    r[0] = getCompleteFile();
                }
            });
        }
        catch (Throwable t)
        {
        }
        return r[0];
    }
}
