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

import com.google.common.collect.ImmutableList;
import javafx.scene.control.ButtonType;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TableManager;
import xyz.columnal.transformations.HideColumns;
import xyz.columnal.gui.dialog.HideColumnsPanel;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.LightDialog;

/**
 * A dialog for editing a HideColumns transformation.
 */
@OnThread(Tag.FXPlatform)
public class HideColumnsDialog extends LightDialog<ImmutableList<ColumnId>>
{
    public HideColumnsDialog(DimmableParent parent, TableManager tableManager, HideColumns hideColumns)
    {
        super(parent);

        HideColumnsPanel hideColumnsPanel = new HideColumnsPanel(tableManager, hideColumns.getSrcTableId(), hideColumns.getHiddenColumns());
        getDialogPane().setContent(hideColumnsPanel.getNode());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
            {
                return hideColumnsPanel.getHiddenColumns();
            }
            return null;
        });
    }
}
