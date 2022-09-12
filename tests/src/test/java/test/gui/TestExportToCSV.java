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

package test.gui;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import test.functions.TFunctionUtil;
import xyz.columnal.data.Column;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.transformations.Calculate;
import test.DummyManager;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gui.trait.CheckCSVTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestExportToCSV extends FXApplicationTest implements ScrollToTrait, CheckCSVTrait
{
    /**
     * Generates a file with some raw data and a transform, then loads it and exports to CSV
     */
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testCalculateToCSV(@When(seed=1L)
            @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue) throws Exception
    {
        TableManager manager = new DummyManager();
        manager.getTypeManager()._test_copyTaggedTypesFrom(expressionValue.typeManager);

        Table srcData = new ImmediateDataSource(manager, new InitialLoadDetails(expressionValue.tableId, null, null, null), new EditableRecordSet(expressionValue.recordSet));
        manager.record(srcData);

        Table calculated = new Calculate(manager, TFunctionUtil.ILD, srcData.getId(), ImmutableMap.of(new ColumnId("Result"), expressionValue.expression));
        manager.record(calculated);

        MainWindowActions details = TAppUtil.openDataAsTable(windowToUse, manager).get();

        List<Pair<String, List<String>>> expectedContent = new ArrayList<>();
        for (Column column : expressionValue.recordSet.getColumns())
        {
            expectedContent.add(new Pair<>(column.getName().getRaw(), CheckCSVTrait.collapse(expressionValue.recordSet.getLength(), column.getType())));
        }
        expectedContent.add(new Pair<>("Result", Utility.mapListEx(expressionValue.value, o -> DataTypeUtility.valueToString(o))));

        exportToCSVAndCheck(details._test_getVirtualGrid(), details._test_getTableManager(),"", expectedContent, calculated.getId());
    }
}
