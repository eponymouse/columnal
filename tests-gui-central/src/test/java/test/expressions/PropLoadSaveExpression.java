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

package test.expressions;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.functions.TFunctionUtil;
import test.gui.TFXUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.lexeditor.ExpressionEditor;
import xyz.columnal.transformations.expression.BracketedStatus;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.nonsenseTrans.GenNonsenseExpression;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by neil on 30/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveExpression extends FXApplicationTest
{
    @Property(trials = 200)
    public void testLoadSaveNonsense(@From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        testLoadSave(expression);
    }

    @Property(trials = 200)
    public void testEditNonsense(@When(seed=-303310519735882501L) @From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        TFXUtil.fxTest_(() -> {
            testNoOpEdit(expression);
        });
    }
    
    @Test
    @OnThread(Tag.FXPlatform)
    public void testUnit() throws InternalException, UserException
    {
        TFXUtil.fxTest_(() -> {
            try
            {
                testNoOpEdit("@define var\\\\N100m Time=@call tag\\\\Optional\\Is(0{s}),var\\\\N100yd time=@call tag\\\\Optional\\Is(0{s})@then@ifvar\\\\N100m Time=~@call tag\\\\Optional\\Is(var\\\\m)@then100{m}/var\\\\m@else@ifvar\\\\N100yd time=~@call tag\\\\Optional\\Is(var\\\\y)@then@call function\\\\core\\convert unit(unit{m/s},100{yard}/var\\\\y)@else0{m/s}@endif@endif@enddefine");
            }
            catch (UserException | InternalException e)
            {
                throw new RuntimeException(e);
            }
        });
    }
    
    @Test
    public void testInvalids()
    {
        TFXUtil.fxTest_(() -> {
            try
            {
                testNoOpEdit("@invalidops(2, @unfinished \"+\")");
                testNoOpEdit("@invalidops(2, @unfinished \"%\", 3)");
                testNoOpEdit("@invalidops(1, @unfinished \"+\", 2, @unfinished \"*\", 3)");
                testNoOpEdit("@invalidops(1, @unfinished \"+\", -2, @unfinished \"*\", 3)");
                testNoOpEdit("@invalidops(-1, @unfinished \"+\", -2, @unfinished \"*\", 3)");
                testNoOpEdit("-1");
            }
            catch (UserException | InternalException e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    public void testNoOpEdit(String src) throws UserException, InternalException
    {
        TypeManager typeManager = DummyManager.make().getTypeManager();
        testNoOpEdit(TFunctionUtil.parseExpression(src, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager())));
    }

    @OnThread(Tag.FXPlatform)
    private void testNoOpEdit(Expression expression)
    {
        TypeManager typeManager = DummyManager.make().getTypeManager();
        // To preserve column references we have to have them in the lookup:
        ColumnLookup columnLookup = new ColumnLookup()
        {
            @Override
            public @Nullable FoundColumn getColumn(Expression expression, @Nullable TableId tableId, ColumnId columnId)
            {
                return null;
            }

            @Override
            public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
            {
                return null;
            }

            @Override
            public Stream<Pair<@Nullable TableId, ColumnId>> getAvailableColumnReferences()
            {
                return Stream.of();
            }

            @Override
            public Stream<TableId> getAvailableTableReferences()
            {
                return Stream.of();
            }

            @Override
            public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
            {
                // Not used:
                return Stream.empty();
            }
        };
        
        
        Expression edited = new ExpressionEditor(expression, new ReadOnlyObjectWrapper<@Nullable Table>(null), new ReadOnlyObjectWrapper<>(columnLookup), null, null, typeManager, () -> TFunctionUtil.createTypeState(typeManager), FunctionList.getFunctionLookup(typeManager.getUnitManager()), e -> {
        }).save(false);
        assertEquals(expression, edited);
        assertEquals(expression.save(SaveDestination.TO_FILE, BracketedStatus.NEED_BRACKETS, TableAndColumnRenames.EMPTY), edited.save(SaveDestination.TO_FILE, BracketedStatus.NEED_BRACKETS, TableAndColumnRenames.EMPTY));
        assertEquals(expression.save(SaveDestination.TO_FILE, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY), edited.save(SaveDestination.TO_FILE, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY));
    }

    @Property(trials = 200)
    public void testLoadSaveReal(@From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue) throws InternalException, UserException
    {
        try
        {
            testLoadSave(expressionValue.expression);
        }
        catch (OutOfMemoryError e)
        {
            fail("Out of memory issue with expression: " + expressionValue.expression);
        }
    }

    private void testLoadSave(@From(GenNonsenseExpression.class) Expression expression) throws UserException, InternalException
    {
        String saved = expression.save(SaveDestination.TO_FILE, BracketedStatus.NEED_BRACKETS, TableAndColumnRenames.EMPTY);
        // Use same manager to load so that types are preserved:
        TypeManager typeManager = TFunctionUtil.managerWithTestTypes().getFirst().getTypeManager();
        Expression reloaded = TFunctionUtil.parseExpression(saved, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        assertEquals("Saved version: " + saved, expression, reloaded);
        String resaved = reloaded.save(SaveDestination.TO_FILE, BracketedStatus.NEED_BRACKETS, TableAndColumnRenames.EMPTY);
        assertEquals(saved, resaved);

    }
}
