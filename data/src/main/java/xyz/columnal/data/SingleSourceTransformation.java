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

import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.TableId;

public interface SingleSourceTransformation
{
    @OnThread(Tag.Any)
    @Pure
    TableId getSrcTableId();
    
    @OnThread(Tag.Simulation)
    Transformation withNewSource(TableId newSrcTableId) throws InternalException;
}
