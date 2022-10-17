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

package xyz.columnal.transformations;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.function.simulation.SimulationSupplier;

import java.util.List;
import java.util.Optional;

/**
 * Helper class that simplifies implementing TransformationInfo for those transformations which have a single primary source.
 */
public abstract class SingleSourceTransformationInfo extends TransformationInfo
{
    public SingleSourceTransformationInfo(String canonicalName, @LocalizableKey String displayNameKey, String imageFileName, @LocalizableKey String explanationKey, List<String> keywords)
    {
        super(canonicalName, displayNameKey, imageFileName, explanationKey, keywords);
    }

    @Override
    @OnThread(Tag.Simulation)
    public final Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException
    {
        if (source.size() > 1)
            throw new UserException("Transformation " + getCanonicalName() + " cannot have multiple sources. (If source name has a space, make sure to quote it.)");
        return loadSingle(mgr, initialLoadDetails, source.get(0), detail, expressionVersion);
    }

    @OnThread(Tag.Simulation)
    protected abstract Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException;

    @Override
    @OnThread(Tag.FXPlatform)
    public final @Nullable SimulationSupplier<Transformation> make(TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable)
    {
        return askForSingleSrcTable.get().<@Nullable SimulationSupplier<Transformation>>map(srcTable -> {
            SimulationSupplier<Transformation> simulationSupplier = () -> makeWithSource(mgr, destination, srcTable);
            return simulationSupplier;
        }).orElse(null);
    }

    @OnThread(Tag.Simulation)
    protected abstract Transformation makeWithSource(TableManager mgr, CellPosition destination, Table srcTable) throws InternalException;
}
