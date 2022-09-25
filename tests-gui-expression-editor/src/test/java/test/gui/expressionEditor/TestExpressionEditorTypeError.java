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

package test.gui.expressionEditor;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Test;
import org.junit.runner.RunWith;
import threadchecker.OnThread;
import threadchecker.Tag;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditorTypeError extends BaseTestExpressionEditorError
{
    @Test
    public void testEmptyList()
    {
        // Should be no error:
        testError("[]", e(0, 2, "ambig"));
    }

    @Test
    public void testLengthEmptyList()
    {
        // Should be no error:
        testError("list length([])");
    }
    
    @Test
    public void testTypeError1()
    {
        testError("2+true", e(2,6, 4,8, "number"));
    }

    @Test
    public void testTypeError1b()
    {
        testError("2+true+3", e(2,6, 4,8, "number"));
    }

    @Test
    public void testTypeError2()
    {
        testError("[2,true]", e(0,8, 0,9,  "type"));
    }

    @Test
    public void testTypeError3()
    {
        testError("@if0@then1@else2@endif", e(3,4, 4,5,  "boolean"));
    }
    
    @Test
    public void testTypeErrorThenElse()
    {
        testError("@iftrue@then100{m}@else13@endif", e(23,25, 32,34,  "mismatch"));
    }

    @Test
    public void testFunctionError1()
    {
        testError("element([1],false)", e(12,17, 13,18,  "type"));
    }

    @Test
    public void testFunctionError2()
    {
        testError("date from ymd(2016{year},1,1{day})", e(25, 26, 26,27,  "type"));
    }

    @Test
    public void testFunctionError3()
    {
        testError("element(false)", e(0,14, "parameters"));
    }

    @Test
    public void testFunctionError3b()
    {
        testError("element()", e(0,9, "parameters"));
    }

    @Test
    public void testFunctionError3c()
    {
        testError("element(false,false,false)", e(0,26, 0,28, "parameters"));
    }

    @Test
    public void testUnitError1()
    {
        testError("2{s}+3{m}", e(5, 9, 7,11, "number"));
    }

    @Test
    public void testUnitError1b()
    {
        testError("2{s}+3{m}+4{s}", e(5, 9, 7,11, "number"));
    }

    @Test
    public void testAmbiguousTypeError1()
    {
        testError("from text(\"\")", e(0, 13, "type"));
    }

    @Test
    public void testAmbiguousTypeError1b()
    {
        testError("[from text(\"\")]", e(0, 15, "type"));
    }

    @Test
    public void testAmbiguousTypeError1c()
    {
        testError("@iftrue@thenfrom text(\"\")@else[]@endif", e(0, 38, 0,49, "type"));
    }

    @Test
    public void testAmbiguousTypeError2()
    {
        testError("element([],1)", e(0, 13, 0,14, "type"));
    }
    
    @Test
    public void testVarNoError()
    {
        testError("@ifOptional\\None=~Optional\\Is(x)@thenget single(x)@else@ifOptional\\None=~Optional\\Is(x)@thenx+1{m/s}@else0{m/s}@endif@endif");
    }

    @Test
    public void testUnknownVar()
    {
        testError("1+y", e(2, 3, 4, 5, "unknown"));
    }

    @Test
    public void testUnknownVar2()
    {
        testError("1+Is(y)", e(5, 6, 7, 8, "unknown"));
    }
}
