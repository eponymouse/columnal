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

import com.google.common.collect.ImmutableSet;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.DialogPaneWithSideButtons;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.LightDialog;

@OnThread(Tag.FXPlatform)
public class PickTableDialog extends LightDialog<Table>
{
    public PickTableDialog(View view, @Nullable Table destTable, Point2D lastScreenPos)
    {
        // We want the cancel button to appear to the right, because otherwise the auto complete hides it:
        super(view, new DialogPaneWithSideButtons());
        initModality(Modality.NONE);


        // Should also exclude tables which use destination as a source, to prevent cycles:
        ImmutableSet<Table> excludeTables = destTable == null ? ImmutableSet.of() : ImmutableSet.of(destTable);
        PickTablePane pickTablePane = new PickTablePane(view.getManager(), excludeTables, "", t -> {
            setResult(t);
            close();
        });
        pickTablePane.setFieldPrefWidth(400.0);
        getDialogPane().setContent(pickTablePane);

        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        
        setOnShowing(e -> {
            view.enableTablePickingMode(lastScreenPos, getDialogPane().sceneProperty(), excludeTables, t -> {
                // We shouldn't need the mouse call here, I think this is a checker framework bug:
                FXUtility.mouse(this).setResult(t);
                close();
            });
        });
        
        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(pickTablePane::focusEntryField);
        });
        setOnHiding(e -> {
            view.disablePickingMode();
        });
        getDialogPane().getStyleClass().add("pick-table-dialog");
        //org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

}
