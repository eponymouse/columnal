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

package xyz.columnal.rinterop;

import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;

/**
 * This is really just a single int read from R, but it has all the helper methods for interrogating the different parts.
 */
final class TypeHeader
{
    private final int headerBits;
    
    public TypeHeader(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, InternalException, UserException
    {
        headerBits = d.readInt();
    }

    @Nullable RValue readAttributes(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
    {
        final @Nullable RValue attr;
        if (hasAttributes())
        {
            // Also read trailing attributes:
            attr = RRead.readItem(d, atoms);
        }
        else
        {
            attr = null;
        }
        return attr;
    }

    @Nullable RValue readTag(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
    {
        final @Nullable RValue tag;
        if (hasTag())
        {
            // Also read trailing attributes:
            tag = RRead.readItem(d, atoms);
        }
        else
        {
            tag = null;
        }
        return tag;
    }

    public int getType()
    {
        return headerBits & 0xFF;
    }
    
    public boolean hasAttributes()
    {
        return (headerBits & 0x200) != 0;
    }

    public boolean hasTag()
    {
        return (headerBits & 0x400) != 0;
    }

    public boolean isObject()
    {
        return (headerBits & 0x100) != 0;
    }
    
    public int getReference(DataInputStream d) throws IOException
    {
        int ref = headerBits >>> 8;
        if (ref == 0)
            return d.readInt();
        else
            return ref;
    }
}
