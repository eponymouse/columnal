package utility.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import javafx.util.Callback;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.ErrorableTextField;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;

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
