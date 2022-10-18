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

package xyz.columnal.gui.recipe;

import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.lexeditor.ExpressionEditor.ColumnPicker;
import xyz.columnal.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.TranslationUtility;

@OnThread(Tag.FXPlatform)
public abstract class ExpressionRecipe
{
    private final @Localized String title;

    public ExpressionRecipe(@LocalizableKey String titleKey)
    {
        this.title = TranslationUtility.getString(titleKey);
    }

    public @Localized String getTitle()
    {
        return title;
    }

    public abstract @Nullable Expression makeExpression(Window parentWindow, ColumnPicker columnPicker);
}
