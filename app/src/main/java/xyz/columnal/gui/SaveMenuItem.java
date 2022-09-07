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

package xyz.columnal.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.TranslationUtility;

import java.time.Instant;

final class SaveMenuItem extends MenuItem
{
    private final ObjectProperty<Object> dummyNowBinding = new SimpleObjectProperty<>(new Object());
    private final @OnThread(Tag.FXPlatform) StringBinding text;

    @OnThread(Tag.FXPlatform)
    public SaveMenuItem(View view)
    {
        text = Bindings.createStringBinding(() ->
        {
            @Nullable Instant lastSave = view.lastSaveTime().get();
            if (lastSave == null)
                return TranslationUtility.getString("menu.project.modified");
            else
                return TranslationUtility.getString("menu.project.save", "" + (Instant.now().getEpochSecond() - lastSave.getEpochSecond()));
        }, view.lastSaveTime(), dummyNowBinding);
        // Invalidating this binding on show will force re-evaluation of the time gap:
        FXUtility.onceNotNull(parentMenuProperty(), menu -> menu.addEventHandler(Menu.ON_SHOWING, e -> {
            text.invalidate();
        }));
        textProperty().bind(text);
        setOnAction(e -> view.save(true));
    }


}
