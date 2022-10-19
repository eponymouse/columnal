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

package xyz.columnal.importers;

import com.google.common.collect.ImmutableList;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.TableManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.simulation.SimulationConsumerNoError;

import java.io.File;
import java.net.URL;

public interface Importer
{
    /**
     * Get the list of supported file types.  Each pair is the localized label for the file type,
     * and a list of file extensions (like "*.txt").
     */
    @OnThread(Tag.Any)
    public ImmutableList<String> getSupportedFileTypes();

    /**
     * Attempt to load the given file
     *
     * @param parent The window parent, in case you need to show any dialogs
     * @param tableManager The destination table manager
     * @param src The file to read from
     * @param origin The path that the file comes from (in case of resolving relative links from file)
     * @param recordLoadedTable The callback to call if the load is successful.  If you
     *               have multiple tables to import, calling this multiple times
     *               is safe.
     */
    @OnThread(Tag.FXPlatform)
    public void importFile(Window parent, TableManager tableManager, CellPosition destPosition, File src, URL origin, SimulationConsumerNoError<DataSource> recordLoadedTable);

    /**
     * The name of the importer to display to the user when picking an importer
     */
    @Localized String getName();
}
