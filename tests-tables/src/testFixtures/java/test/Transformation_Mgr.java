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

import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;

public class Transformation_Mgr
{
    public final TableManager mgr;
    public final Transformation transformation;

    @OnThread(Tag.Simulation)
    public Transformation_Mgr(TableManager mgr, Transformation transformation)
    {
        this.mgr = mgr;
        this.transformation = transformation;
        mgr.record(transformation);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true) // Only for testing anyway
    public String toString()
    {
        return transformation.toString();
    }
}
