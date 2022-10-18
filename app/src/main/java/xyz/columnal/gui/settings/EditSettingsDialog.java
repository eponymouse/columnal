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

package xyz.columnal.gui.settings;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Settings;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.gui.ErrorableDialog;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.LabelledGrid;

import java.io.File;

@OnThread(Tag.FXPlatform)
public class EditSettingsDialog extends ErrorableDialog<Settings>
{
    private final TextField rLocation;
    private final CheckBox useRLocalLibs;

    public EditSettingsDialog(Window parent, Settings initialSettings)
    {
        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(TranslationUtility.getString("settings.title"));
        rLocation = new TextField(initialSettings.pathToRExecutable == null ? "" : initialSettings.pathToRExecutable.getAbsolutePath());
        rLocation.setPromptText(TranslationUtility.getString("settings.rexe.blank"));

        this.useRLocalLibs = new CheckBox(TranslationUtility.getString("settings.rlocallibs.checkbox"));
        this.useRLocalLibs.setSelected(initialSettings.useColumnalRLibs);
        
        getDialogPane().setContent(new LabelledGrid(
            LabelledGrid.labelledGridRow("settings.rexe", "edit-settings/rexe", GUI.borderLeftCenterRight(null, rLocation, GUI.button("settings.rexe.choose", () -> {
                FileChooser fileChooser = new FileChooser();
                File file = new File(rLocation.getText().trim());
                if (file.exists())
                {
                    if (file.isFile())
                    {
                        File parentFile = file.getParentFile();
                        if (parentFile != null)
                            fileChooser.setInitialDirectory(parentFile);
                    }
                    else if (file.isDirectory())
                        fileChooser.setInitialDirectory(file);
                }
                Scene scene = getDialogPane().getScene();
                File newFile = fileChooser.showOpenDialog(scene == null ? null : scene.getWindow());
                if (newFile != null)
                {
                    rLocation.setText(newFile.getAbsolutePath());
                }
            }))),
            LabelledGrid.labelledGridRow("settings.rlocallibs.label", "edit-settings/rlocallibs", this.useRLocalLibs)
        ));
    }

    @Override
    protected Either<@Localized String, Settings> calculateResult()
    {
        @Nullable File rexe;
        if (rLocation.getText().trim().isEmpty())
            rexe = null;
        else
        {
            rexe = new File(rLocation.getText().trim());
            if (!rexe.isFile())
            {
                return Either.left(TranslationUtility.getString("settings.error.rexe.invalid"));
            }
        }
        
        return Either.right(new Settings(rexe, useRLocalLibs.isSelected()));
    }
}
