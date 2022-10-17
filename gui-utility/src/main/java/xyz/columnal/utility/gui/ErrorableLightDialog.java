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

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.util.Callback;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.TranslationUtility;

/**
 * A dialog which has an error label, and a single converter function which returns
 * either an error or the result.  Prevents OK button doing anything if we are currently
 * in the error state.
 */
@OnThread(Tag.FXPlatform)
public abstract class ErrorableLightDialog<R> extends LightDialog<R>
{
    private final ErrorLabel errorLabel = new ErrorLabel();
    private @Nullable R result;

    public ErrorableLightDialog(DimmableParent parent, boolean buttonsToSide)
    {
        super(parent, buttonsToSide ? new DialogPaneWithSideButtons() : null);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("cancel-button");
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            if (errorLabel.getParent() == null)
            {
                try
                {
                    throw new InternalException("In dialog " + getClass() + " error label is not in scene while button pressed");
                }
                catch (InternalException ex)
                {
                    Log.log(ex);
                }
            }
            
            FXUtility.mouse(this).calculateResult().either_(err -> {
                result = null;
                errorLabel.setText(TranslationUtility.getString("error.colon", err));
                e.consume(); // Prevent OK doing anything
            }, r -> {result = r;});
        });
        //We bind so that if subclass mistakenly tries to set, it will get an error:
        resultConverterProperty().bind(new ReadOnlyObjectWrapper<Callback<ButtonType, @Nullable R>>(bt -> {
            if (bt == ButtonType.OK)
            {
                if (result != null)
                {
                    return result;
                }
                else
                {
                    Log.logStackTrace("OK pressed successfully but blank result for " + getClass());
                }
            }
            return null;
        }));
    }

    // Given back as Node because it's only meant for adding to GUI.  Subclasses don't set
    // the text, we do.
    @OnThread(Tag.FXPlatform)
    protected final Node getErrorLabel(@UnknownInitialization(ErrorableLightDialog.class) ErrorableLightDialog<R>this)
    {
        return errorLabel;
    }
    
    protected void clearErrorLabel(@UnknownInitialization(ErrorableLightDialog.class) ErrorableLightDialog<R> this)
    {
        errorLabel.setText("");
    }

    protected void clearErrorLabelOnChange(@UnknownInitialization(ErrorableLightDialog.class) ErrorableLightDialog<R> this, ErrorableTextField<?> field)
    {
        field.onTextChange(() -> {
            clearErrorLabel();
        });
    }

    /**
     * Gets either an error or the result.  If there is an error, it may be called again.  If a
     * result is returned, it will not be called again.
     */
    @OnThread(Tag.FXPlatform)
    protected abstract Either<@Localized String, R> calculateResult();
}
