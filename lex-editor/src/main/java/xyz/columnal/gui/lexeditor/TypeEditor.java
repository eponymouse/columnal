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
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeLexer, CodeCompletionContext>
{
    public TypeEditor(TypeManager typeManager, @Nullable TypeExpression originalContent, boolean requireConcreteType, boolean emptyAllowed, FXPlatformConsumer<@NonNull @Recorded TypeExpression> onChange)
    {
        super(originalContent == null ? null : originalContent.save(SaveDestination.toTypeEditor(typeManager), new TableAndColumnRenames(ImmutableMap.of())), new TypeLexer(typeManager, requireConcreteType, emptyAllowed), typeManager, onChange, "type-editor");
    }
}
