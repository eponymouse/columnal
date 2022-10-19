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

package xyz.columnal.gui.table.app;

import com.google.common.collect.ImmutableList;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.gui.table.app.PickTypeTransformDialog.TypeTransform;
import xyz.columnal.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.gui.DialogPaneWithSideButtons;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.LightDialog;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class PickTypeTransformDialog extends LightDialog<TypeTransform>
{
    public static class TypeTransform
    {
        public final FXPlatformSupplier<Optional<Expression>> transformed;
        private final DataType destinationType;

        public TypeTransform(Expression transformed, DataType destinationType)
        {
            this.transformed = () -> Optional.of(transformed);
            this.destinationType = destinationType;
        }

        public TypeTransform(FXPlatformSupplier<Optional<Expression>> transformed, DataType destinationType)
        {
            this.transformed = transformed;
            this.destinationType = destinationType;
        }

        @Override
        public String toString()
        {
            return destinationType.toString();
        }
        
        public boolean _test_hasDestinationType(DataType dataType)
        {
            return destinationType.equals(dataType);
        }
    }

    public PickTypeTransformDialog(DimmableParent parent, ImmutableList<TypeTransform> pickFrom)
    {
        super(parent, new DialogPaneWithSideButtons());
        initModality(Modality.APPLICATION_MODAL);
        ListView<TypeTransform> listView = new ListView<>();
        listView.getStyleClass().add("destination-type-list");
        listView.getItems().setAll(pickFrom);
        listView.setPrefHeight(150);
        BorderPane.setMargin(listView, new Insets(10, 0, 0, 0));
        getDialogPane().setContent(GUI.borderTopCenter(
            GUI.label("transformType.header"),
            listView
        ));
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("cancel-button");
        if (!pickFrom.isEmpty())
            listView.getSelectionModel().selectFirst();
        else
        {
            listView.setPlaceholder(new Label("No suitable destination types"));
            getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
        }
        setResultConverter(bt -> bt == ButtonType.OK ? listView.getSelectionModel().getSelectedItem() : null);
        setOnShown(e -> FXUtility.runAfter(listView::requestFocus));
        
    }
}
