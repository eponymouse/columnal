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

package test;

import com.google.common.collect.ImmutableMap;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.GridComment;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.TransformationManager;

/**
 * A TableManager implementation useful for testing
 */
public final class DummyManager extends TableManager
{
    public DummyManager() throws InternalException, UserException
    {
        super(TransformationManager.getInstance(), onError -> ImmutableMap.of());
        addListener(new TableManagerListener()
        {
            @Override
            public void removeTable(Table t, int remainingCount)
            {
            }

            @Override
            public void addSource(DataSource dataSource)
            {
            }

            @Override
            public void addTransformation(Transformation transformation)
            {
            }

            @Override
            public void addComment(GridComment gridComment)
            {
            }

            @Override
            public void removeComment(GridComment gridComment)
            {
            }
        });
    };
    
    public static DummyManager make()
    {
        try
        {
            return new DummyManager();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
