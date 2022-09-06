package xyz.columnal.utility.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;

/**
 * A dialog which has an error label, and a single converter function which returns
 * either an error or the result.  Prevents OK button doing anything if we are currently
 * in the error state.
 */
public abstract class ErrorableDialog<R> extends Dialog<R>
{
    private final ErrorLabel errorLabel = new ErrorLabel();
    private @Nullable R result;

    /**
     * Note: this constructor sets the buttons to Ok/cancel.  It's important that
     * you do not set the buttons again afterwards as it destroys the
     * listeners set here.
     */
    public ErrorableDialog(@Nullable DialogPane customDialogPane)
    {
        if (customDialogPane != null)
            setDialogPane(customDialogPane);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            FXUtility.mouse(this).calculateResult().either_(err -> {
                result = null;
                errorLabel.setText(err);
                Log.debug("Error: " + err);
                e.consume(); // Prevent OK doing anything
            }, r -> {result = r;});
        });
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        //We bind so that if subclass mistakenly tries to set, it will get an error:
        resultConverterProperty().bind(new ReadOnlyObjectWrapper<javafx.util.Callback<ButtonType, @Nullable R>>(bt -> {
            if (bt == ButtonType.OK)
            {
                if (result != null)
                {
                    return result;
                }
                else
                {
                    Log.logStackTrace("OK pressed successfully but blank result");
                }
            }
            return null;
        }));
    }
    
    public ErrorableDialog()
    {
        this(null);
    }

    // Given back as Node because it's only meant for adding to GUI.  Subclasses don't set
    // the text, we do.
    @OnThread(Tag.FXPlatform)
    public final Node getErrorLabel(@UnknownInitialization(ErrorableDialog.class) ErrorableDialog<R> this)
    {
        return errorLabel;
    }

    /**
     * Gets either an error or the result.  If there is an error, it may be called again.  If a
     * result is returned, it will not be called again.
     */
    @OnThread(Tag.FXPlatform)
    protected abstract Either<@Localized String, R> calculateResult();
}
