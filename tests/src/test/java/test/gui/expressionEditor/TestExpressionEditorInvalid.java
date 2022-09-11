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

import annotation.recorded.qual.Recorded;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import test.functions.TFunctionUtil;
import xyz.columnal.data.Table;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.lexeditor.ExpressionEditor;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenInvalidExpressionSource;
import test.gui.trait.EnterTypeTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestExpressionEditorInvalid extends FXApplicationTest implements EnterTypeTrait
{
    @OnThread(Tag.Simulation)
    @Property(trials=20)
    public void testLoadSaveInvalid(@From(GenInvalidExpressionSource.class) String invalid) throws UserException, InternalException
    {
        DummyManager dummyManager = new DummyManager();
        ExpressionEditor expressionEditorA = makeExpressionEditor(dummyManager, null);
        TestUtil.fx_(() -> {
            windowToUse.setScene(new Scene(new StackPane(expressionEditorA.getContainer())));
            windowToUse.show();
        });
        clickOn(".top-level-editor");
        enterAndDeleteSmartBrackets(invalid);
        @Recorded @NonNull Expression savedInvalid = TestUtil.fx(() -> expressionEditorA.save(false));
        ExpressionEditor expressionEditorB = makeExpressionEditor(dummyManager, savedInvalid);
        assertEquals(savedInvalid.toString(), invalid.replaceAll("[ ()]", ""), TestUtil.fx(() -> expressionEditorB._test_getRawText()).replaceAll("[ ()]", ""));
    }

    @OnThread(Tag.Any)
    private ExpressionEditor makeExpressionEditor(DummyManager dummyManager, @Nullable Expression initial)
    {
        return TestUtil.fx(() -> new ExpressionEditor(initial, new ReadOnlyObjectWrapper<@Nullable Table>(null), new ReadOnlyObjectWrapper<ColumnLookup>(TFunctionUtil.dummyColumnLookup()), null, null, dummyManager.getTypeManager(), () -> TFunctionUtil.createTypeState(dummyManager.getTypeManager()), FunctionList.getFunctionLookup(dummyManager.getUnitManager()), e -> {}));
    }
}
