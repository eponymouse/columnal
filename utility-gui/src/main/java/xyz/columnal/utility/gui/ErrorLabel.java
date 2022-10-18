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

package xyz.columnal.utility.gui;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.i18n.qual.Localized;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 11/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class ErrorLabel extends TextFlow
{
    private final Text text = new Text();

    public ErrorLabel()
    {
        getChildren().setAll(text);
        text.getStyleClass().add("error-label");
    }

    public void setText(@Localized String content)
    {
        text.setText(content);
    }
}
