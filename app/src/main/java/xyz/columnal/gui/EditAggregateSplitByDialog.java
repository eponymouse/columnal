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

import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.Table;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.gui.ErrorableLightDialog;
import xyz.columnal.utility.gui.FXUtility;

import java.util.Objects;

@OnThread(Tag.FXPlatform)
public class EditAggregateSplitByDialog extends ErrorableLightDialog<ImmutableList<ColumnId>>
{
    private final @Nullable Table srcTable;
    private final AggregateSplitByPane splitList;

    public EditAggregateSplitByDialog(View parent, @Nullable Point2D lastScreenPos, @Nullable Table srcTable, @Nullable Pair<ColumnId, ImmutableList<String>> example, ImmutableList<ColumnId> originalSplitBy)
    {
        super(parent, true);
        setResizable(true);
        initModality(Modality.NONE);
        this.srcTable = srcTable;

        splitList = new AggregateSplitByPane(srcTable, originalSplitBy, example);
        splitList.setMinWidth(250.0);
        splitList.setMinHeight(150.0);
        splitList.setPrefWidth(300.0);
        
        getDialogPane().setContent(new BorderPane(splitList, null, null, getErrorLabel(), null));
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        getDialogPane().getStyleClass().add("sort-list-dialog");
        setOnShowing(e -> {
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
            parent.enableColumnPickingMode(lastScreenPos, getDialogPane().sceneProperty(), p -> Objects.equals(srcTable, p.getFirst()), t -> {
                splitList.pickColumnIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, ImmutableList<ColumnId>> calculateResult()
    {
        @Nullable ImmutableList<ColumnId> items = splitList.getItems();
        if (items == null)
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.name"));
        else
            return Either.right(items);
    }
}
