package utility.gui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

/**
 * A LightDialog variant that when OK is clicked, checks if result is valid.
 * 
 * If is not valid, errors are shown in appropriate fields (determined by subclass)
 * and the dialog remains.  If OK is clicked a second time without modifying
 * the values, it is returned anyway.
 * @param <R>
 */
@OnThread(Tag.FXPlatform)
public abstract class DoubleOKLightDialog<R> extends LightDialog<R>
{
    private final PopOver clickOkAgainPrompt;
    private boolean modifiedSinceLastOK = true;
    private long lastOKPress = System.currentTimeMillis();
    
    public static enum Validity
    {
        IMPOSSIBLE_TO_SAVE,
        ERROR_BUT_CAN_SAVE,
        NO_ERRORS
    }
    
    protected DoubleOKLightDialog(DimmableParent parent, @Nullable DialogPane customDialogPane)
    {
        super(parent, customDialogPane);
        
        clickOkAgainPrompt = new PopOver(new Label("Errors found. Click OK\n again to save anyway"));
        clickOkAgainPrompt.getStyleClass().add("ok-double-prompt");

        FXUtility.listen(getDialogPane().getButtonTypes(), bts -> {
            Node okButton = getDialogPane().lookupButton(ButtonType.OK);
            if (okButton != null)
                okButton.addEventFilter(ActionEvent.ACTION, Utility.later(this)::checkOK);
        });
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
            {
                clickOkAgainPrompt.hide(Duration.ZERO);
                return Utility.later(this).calculateResult();
            }
            return null;
        });
    }

    private void checkOK(ActionEvent e)
    {
        // Ignore modifications during checkValidity:
        boolean prevModified = modifiedSinceLastOK;
        Validity validity = checkValidity();
        modifiedSinceLastOK = prevModified;
        
        switch (validity)
        {
            case IMPOSSIBLE_TO_SAVE:
                showAllErrors();
                e.consume();
                break;
            case ERROR_BUT_CAN_SAVE:
                if (modifiedSinceLastOK)
                {
                    clickOkAgainPrompt.show(getDialogPane().lookupButton(ButtonType.OK));
                    showAllErrors();
                    modifiedSinceLastOK = false;
                    e.consume();
                }
                else if (System.currentTimeMillis() < lastOKPress + 300)
                {
                    // Suppress double-clicks:
                    e.consume();
                }
                break;
            case NO_ERRORS:
                break;
        }
        lastOKPress = System.currentTimeMillis();
    }

    protected void notifyModified(@UnknownInitialization(DoubleOKLightDialog.class) DoubleOKLightDialog<R> this)
    {
        modifiedSinceLastOK = true;
    }
    
    protected abstract Validity checkValidity();
    
    protected abstract void showAllErrors();
    
    protected abstract @Nullable R calculateResult();
}
