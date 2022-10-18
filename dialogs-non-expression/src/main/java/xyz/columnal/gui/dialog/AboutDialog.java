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

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

@OnThread(Tag.FXPlatform)
public class AboutDialog extends Dialog<Void>
{
    public AboutDialog(DimmableParent owner)
    {
        initOwner(owner.dimWhileShowing(this));
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

        ImageView title = FXUtility.makeImageView("columnal.png", null, 90);
        ImageView logo = FXUtility.makeImageView("logo.png", null, 100);
        HBox header = new HBox(Utility.streamNullable(title, logo).toArray(Node[]::new));
        header.getStyleClass().add("logo-container");
        header.setAlignment(Pos.BOTTOM_CENTER);

        TextArea info = new TextArea(
            "Version: " + System.getProperty("columnal.version") + "\n" +
            "Operating system: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")\n" +
            "Location: " + System.getProperty("user.dir") + "\n"
        );
        info.setEditable(false);
        getDialogPane().setContent(GUI.borderTopCenter(header, info));
    }
}
