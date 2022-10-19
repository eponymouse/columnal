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

import annotation.qual.ImmediateValue;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.dtf.DocumentTextField;
import xyz.columnal.gui.dtf.RecogniserDocument;
import xyz.columnal.gui.dtf.TableDisplayUtility.RecogniserAndType;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.ErrorableLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

@OnThread(Tag.FXPlatform)
public class EnterValueDialog<V extends @NonNull @ImmediateValue Object> extends ErrorableLightDialog<V>
{
    private final RecogniserDocument<V> document;

    public EnterValueDialog(DimmableParent parent, DataType dataType, RecogniserAndType<V> recogniserAndType) throws InternalException
    {
        super(parent, true);
        initModality(Modality.APPLICATION_MODAL);
        String initialContent;
        try
        {
            initialContent = DataTypeUtility.valueToStringFX(DataTypeUtility.makeDefaultValue(dataType));
        }
        catch (UserException e)
        {
            // Shouldn't happen when converting default value
            initialContent = "";
        }
        document = new RecogniserDocument<V>(initialContent, recogniserAndType.itemClass, recogniserAndType.recogniser, null, (a, b, c) -> {}, k -> getDialogPane().lookupButton(ButtonType.OK).requestFocus(), null);
        DocumentTextField textField = new DocumentTextField(null);
        textField.setDocument(document);
        getDialogPane().setContent(GUI.borderTopCenterBottom(null, textField, getErrorLabel()));
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        setOnShown(e -> FXUtility.runAfter(() -> {
            textField.requestFocus();
            textField.selectAll();
        }));
    }


    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, V> calculateResult()
    {
        return document.getLatestValue().<@Localized String, V>mapBoth(err -> err.error.toPlain(), v -> v);
    }
}
