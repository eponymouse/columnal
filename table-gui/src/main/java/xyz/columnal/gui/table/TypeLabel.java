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

package xyz.columnal.gui.table;

import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.FXUtility;

/**
 * Displays the type of the given expression, dynamically
 */
@OnThread(Tag.FXPlatform)
public class TypeLabel extends Label
{
    public TypeLabel(ObjectExpression<@Nullable DataType> typeProperty)
    {
        getStyleClass().add("type-label");
        FXUtility.addChangeListenerPlatform(typeProperty, this::updateType);
        updateType(typeProperty.getValue());
    }

    private void updateType(@UnknownInitialization(Label.class) TypeLabel this, @Nullable DataType type)
    {
        if (type != null)
        {
            try
            {
                setText(type.toDisplay(false));
            }
            catch(UserException | InternalException e)
            {
                setText("Error: " + e.getLocalizedMessage());
            }
        }
        else
            setText("Invalid expression");
    }
}
