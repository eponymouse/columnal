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

package xyz.columnal.plugins;

import com.google.common.collect.ImmutableMap;
import xyz.columnal.id.SaveTag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumer;

public abstract class Plugin
{
    private final String name;
    
    public Plugin(String name)
    {
        this.name = name;
    }

    public final String getPluginName()
    {
        return name;
    }
    
    public abstract ImmutableMap<String, SimulationConsumer<Pair<SaveTag, String>>> getHandledContent();
}
