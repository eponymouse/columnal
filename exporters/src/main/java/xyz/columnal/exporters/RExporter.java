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
import xyz.columnal.rinterop.ConvertToR;
import xyz.columnal.rinterop.ConvertFromR.TableType;
import xyz.columnal.rinterop.RWrite;
import xyz.columnal.utility.TranslationUtility;

import java.io.File;
import java.io.IOException;

public class RExporter implements Exporter
{
    @Override
    public void exportData(File destination, Table data) throws UserException, InternalException
    {
        try
        {
            RWrite.writeRData(destination, ConvertToR.convertTableToR(data.getData(), TableType.TIBBLE));
        }
        catch (IOException e)
        {
            throw new UserException("Problem writing to file", e);
        }
    }

    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.r.files");
    }

    @Override
    public ImmutableList<String> getSupportedFileTypes()
    {
        return ImmutableList.of("*.rds", "*.Rdata");
    }
}
