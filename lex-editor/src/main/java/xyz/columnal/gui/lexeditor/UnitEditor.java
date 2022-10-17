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

package xyz.columnal.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;

public class UnitEditor extends TopLevelEditor<UnitExpression, UnitLexer, CodeCompletionContext>
{
    public UnitEditor(TypeManager typeManager, @Nullable UnitExpression originalContent, FXPlatformConsumer<@NonNull @Recorded UnitExpression> onChange)
    {
        super(originalContent == null ? null : originalContent.save(SaveDestination.toUnitEditor(typeManager.getUnitManager()), true), new UnitLexer(typeManager, false), typeManager, onChange, "unit-editor");
    }
}
