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

package xyz.columnal.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.grammar.MainLexer;
import xyz.columnal.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.nio.file.Path;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class LinkedDataSource extends DataSource
{
    private final RecordSet data;
    private final int typeToken;
    private final File path;

    public LinkedDataSource(TableManager mgr, TableId tableId, RecordSet rs, int typeToken, File path)
    {
        super(mgr, new InitialLoadDetails(tableId, null, null, null));
        this.data = rs;
        this.typeToken = typeToken;
        this.path = path;
    }

    @Override
    @OnThread(Tag.Any)
    public RecordSet getData()
    {
        return data;
    }

    @Override
    public @OnThread(Tag.Simulation) void save(@Nullable File destination, Saver then, TableAndColumnRenames renames)
    {
        //dataSourceLinkHeader : DATA tableId LINKED importType filePath NEWLINE;
        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(renames.tableId(getId())).t(MainLexer.LINKED).t(this.typeToken);
        Path path = this.path.toPath();
        if (destination != null)
        {
            try
            {
                path = destination.toPath().relativize(path);
            }
            catch (IllegalArgumentException e)
            {
                // Not near enough to use relative path
            }
        }
        b.path(path);
        b.nl();
        then.saveTable(b.toString());
    }

    @Override
    public @OnThread(Tag.Any) TableOperations getOperations()
    {
        // TODO prompt to transform to non-linked table
        return new TableOperations(null, c -> null, null, null, null);
    }
    
    @Override
    public boolean dataEquals(DataSource o)
    {
        LinkedDataSource that = (LinkedDataSource) o;

        if (typeToken != that.typeToken) return false;
        if (!data.equals(that.data)) return false;
        return path.equals(that.path);
    }

    @Override
    public int dataHashCode()
    {
        int result = data.hashCode();
        result = 31 * result + typeToken;
        result = 31 * result + path.hashCode();
        return result;
    }

    public abstract void notInTheFirstVersion();
}
