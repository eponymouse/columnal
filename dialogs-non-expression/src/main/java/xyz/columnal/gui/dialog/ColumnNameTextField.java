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

package xyz.columnal.gui.dialog;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver.ArrowLocation;
import xyz.columnal.id.ColumnId;
import xyz.columnal.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.gui.ErrorableTextField;

/**
 * Created by neil on 30/04/2017.
 */
@OnThread(Tag.FXPlatform)
public class ColumnNameTextField extends ErrorableTextField<ColumnId>
{
    @OnThread(Tag.FXPlatform)
    @SuppressWarnings("identifier")
    public ColumnNameTextField(@Nullable ColumnId initial)
    {
        // We automatically remove leading/trailing whitespace, rather than complaining about it.
        // We also convert any whitespace (including multiple chars) into a single space
        super(s -> {
            s = GrammarUtility.collapseSpaces(s);
            if (s.isEmpty())
                return ConversionResult.<@NonNull ColumnId>error(TranslationUtility.getStyledString("column.name.error.missing"));
            return checkAlphabet(s, ColumnId::validCharacter, ColumnId::new);
        });
        getStyleClass().add("column-name-text-field");
        if (initial != null)
            setText(initial.getRaw());
    }

    // Overridden to change return type to be more specific:
    @Override
    public ColumnNameTextField withArrowLocation(ArrowLocation location)
    {
        super.withArrowLocation(location);
        return this;
    }
}
