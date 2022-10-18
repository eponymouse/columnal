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

package xyz.columnal.exporters;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import xyz.columnal.data.Table;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;

public interface Exporter
{
    /**
     * Do the actual export to the given file.
     */
    @OnThread(Tag.Simulation)
    public void exportData(File destination, Table data) throws UserException, InternalException;

    /**
     * The name of the exporter to display to the user when picking an exporter
     */
    @Localized String getName();

    /**
     * Get the list of supported file types.  Each item is a file extension (like "*.txt").
     */
    @OnThread(Tag.Any)
    public ImmutableList<String> getSupportedFileTypes();
}
