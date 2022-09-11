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

package test.data;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.runner.RunWith;
import test.gen.GenFile;
import xyz.columnal.utility.Utility;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 26/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFiles
{
    @Property
    public void testLineCount(@From(GenFile.class) GeneratedTextFile input) throws IOException
    {
        assertEqualsMsg("Counting lines for " + input.getCharset(), input.getLineCount(), Utility.countLines(input.getFile(), input.getCharset()));
    }

    public static <T> void assertEqualsMsg(String msg, @NonNull T exp, @NonNull T act)
    {
        try
        {
            assertEquals(msg, exp, act);
        }
        catch (AssertionError err)
        {
            System.err.println("Expected: " + exp + "\n  Actual: " + act);
            throw err;
        }
    }
}
