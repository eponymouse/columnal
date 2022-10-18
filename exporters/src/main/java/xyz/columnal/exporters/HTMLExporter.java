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

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import xyz.columnal.data.Column;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.TranslationUtility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class HTMLExporter implements Exporter
{
    @Override
    public @OnThread(Tag.Simulation) void exportData(File destination, Table data) throws UserException, InternalException
    {
        String header = "<!DOCTYPE html>\n<html>\n<head><title>" + e(data.getId().getRaw()) + "</title></head>\n<body>\n<table>\n<caption>" + e(data.getId().getRaw()) + "</caption>\n";
        RecordSet rs = data.getData();
        String tableHeader = "<thead>\n<tr>\n" + rs.getColumnIds().stream().map(c -> "<td><b>" + e(c.getRaw()) + "</b></td>\n").collect(Collectors.joining()) + "</tr>\n</thead>\n";
        StringBuilder tableBody = new StringBuilder("<tbody>\n");
        int length = rs.getLength();
        for (int i = 0; i < length; i++)
        {
            tableBody.append("<tr>\n");
            for (Column column : rs.getColumns())
            {
                tableBody.append("<td>");
                @Value Object collapsed = column.getType().getCollapsed(i);
                // Don't quote Text type unless part of compound type:
                if (collapsed instanceof String)
                    tableBody.append(e((String)collapsed));
                else
                    tableBody.append(e(DataTypeUtility.valueToString(collapsed)));
                tableBody.append("</td>\n");
            }
            tableBody.append("</tr>\n");
        }
        tableBody.append("</tbody>\n</table>\n");
        String fullContent = header + tableHeader + tableBody.toString() + "\n<p>Exported on " +  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()) + "</p></body>\n</html>\n";
        try
        {
            FileUtils.write(destination, fullContent, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new UserException("Error writing to " + destination.getAbsolutePath(), e);
        }
    }
    
    private String e(String raw)
    {
        return StringEscapeUtils.escapeHtml4(raw);
    }

    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.html.files");
    }

    @OnThread(Tag.Any)
    @Override
    public ImmutableList<String> getSupportedFileTypes()
    {
        return ImmutableList.of("*.html", "*.htm");
    }
}
