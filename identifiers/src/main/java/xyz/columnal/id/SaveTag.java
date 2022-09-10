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
package xyz.columnal.id;

import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.grammar.MainParser2;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

/**
 * A several-letter always-capitalised save tag, to help distinguish
 * tables in the saved files and prevent diff from seeing common lines
 * in different tables.
 */
public class SaveTag
{
    private final String tag;

    public SaveTag(String tag)
    {
        this.tag = tag;
    }
    
    public SaveTag(MainParser2.DetailContext detailContext)
    {
        this(detailContext.DETAIL_BEGIN().getText().substring("@BEGIN".length()).trim());
    }

    public static SaveTag generateRandom()
    {
        int index = new Random().nextInt(26 * 26 * 26);
        char[] cs = new char[3];
        for (int i = 0; i < cs.length; i++)
        {
            cs[i] = (char)((index % 26) + 'A');
            index = index / 26;
        }
        return new SaveTag(new String(cs));
    }

    @OnThread(Tag.Any)
    @Pure
    public final String getTag()
    {
        return tag;
    }
}
