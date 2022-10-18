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

import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import test.DummyManager;
import test.TTableUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.gui.MainWindow;
import xyz.columnal.id.TableId;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.gui.FXUtility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class TAppUtil
{
    /**
     * IMPORTANT: we say Simulation thread to satisfy thread-checker, but don't call it from the actual
     * simultation thread or it will time out!  Just tag yours as simulation, too.
     *
     * Returns a runnable which will wait for the table to load
     *
     * @param windowToUse
     * @param mgr
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws InvocationTargetException
     */
    @OnThread(Tag.Simulation)
    public static Supplier<MainWindow.MainWindowActions> openDataAsTable(Stage windowToUse, TableManager mgr) throws Exception
    {
        File temp = File.createTempFile("srcdata", "tables");
        temp.deleteOnExit();
        String saved = TTableUtil.save(mgr);
        //System.out.println("Saving: {{{" + saved + "}}}");
        AtomicReference<MainWindow.MainWindowActions> tableManagerAtomicReference = new AtomicReference<>();
        FXUtility.runFX(() -> TBasicUtil.checkedToRuntime_(() -> {
            MainWindow.MainWindowActions mainWindowActions = MainWindow.show(windowToUse, temp, new Pair<>(temp, saved), null);
            tableManagerAtomicReference.set(mainWindowActions);
        }));
        // Wait until individual tables are actually loaded:
        return () -> {
            int count = 0;
            do
            {
                //System.err.println("Waiting for main window");
                TFXUtil.sleep(1000);
                count += 1;
            }
            while (TFXUtil.fx(() -> windowToUse.getScene().lookup(".virt-grid-line")) == null && count < 30);
            if (count >= 30)
                throw new RuntimeException("Could not load table data");
            return tableManagerAtomicReference.get();
        };
    }

    @OnThread(Tag.Simulation)
    public static MainWindow.MainWindowActions openDataAsTable(Stage windowToUse, @Nullable TypeManager typeManager, RecordSet data) throws Exception
    {
        return openDataAsTable(windowToUse, typeManager, data, new TableId("Table1"));
    }

    @OnThread(Tag.Simulation)
    public static MainWindow.MainWindowActions openDataAsTable(Stage windowToUse, @Nullable TypeManager typeManager, RecordSet data, TableId tableId) throws Exception
    {
        TableManager manager = new DummyManager();
        Table t = new ImmediateDataSource(manager, new Table.InitialLoadDetails(tableId, null, CellPosition.ORIGIN.offsetByRowCols(1, 1), null), new EditableRecordSet(data));
        manager.record(t);
        if (typeManager != null)
        {
            manager.getTypeManager()._test_copyTaggedTypesFrom(typeManager);
        }
        return openDataAsTable(windowToUse, manager).get();
    }
}
