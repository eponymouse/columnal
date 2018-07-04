package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.PopOver;
import records.error.InternalException;
import records.gui.FixList;
import records.gui.FixList.FixInfo;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.QuickFix;
import styled.StyledShowable;
import styled.StyledString;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Manages the display of errors, quick fixes and type information in the expression editor.
 * 
 * The rule for when it displays is either:
 *  - The user hovers over the label at the top of an expression item (regardless of error state), OR
 *  - (The user hovers over the main part or focuses the main part) AND there is an error.
 * Bindings to do this will get too intricate, so we just keep track of the properties involved
 *  
 * Additionally, when we should hide, but there is an error showing, we hide slowly, so the user gets
 * time to mouse-over us to access the fixes and so on.  If the user mouses-over us, we cancel any current
 * hide.
 *  
 */
public class ExpressionInfoDisplay
{
    private final SimpleStringProperty type = new SimpleStringProperty("");
    private final SimpleObjectProperty<StyledString> errorMessage = new SimpleObjectProperty<>(StyledString.s(""));
    private final SimpleObjectProperty<ImmutableList<FixInfo>> fixes = new SimpleObjectProperty<>(ImmutableList.of());
    private final VBox expressionNode;
    // Only currently used for debugging:
    private final TextField textField;
    private final TopLevelEditor.ErrorMessageDisplayer popup;
    private final SimpleBooleanProperty maskingErrors = new SimpleBooleanProperty();
    
    
    @SuppressWarnings("initialization")
    public ExpressionInfoDisplay(VBox expressionNode, Label topLabel, TextField textField)
    {
        this.expressionNode = expressionNode;
        this.textField = textField;
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), this::textFieldFocusChanged);
        FXUtility.addChangeListenerPlatformNN(expressionNode.hoverProperty(), b -> {
            mouseHoverNode = b ? expressionNode : null;
            popup.setMouseError(expressionNode, b ? makeError() : null);
        });
        FXUtility.addChangeListenerPlatformNN(topLabel.hoverProperty(), b -> {
            popup.setMouseError(topLabel, b ? makeError() : null);
        });
        FXUtility.addChangeListenerPlatformNN(errorMessage, s -> {
            popup.updateMouseError()
        });
        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            OptionalInt fKey = FXUtility.FKeyNumber(e.getCode());
            if (e.isShiftDown() && fKey.isPresent() && popup != null)
            {
                e.consume();
                hide(true);
                // 1 is F1, but should trigger fix zero:
                fixes.get().get(fKey.getAsInt() - 1).executeFix.run();
            }
            if (e.getCode() == KeyCode.ESCAPE)
            {
                hideImmediately();
            }
        });

        // Default is to mask errors if field has never *lost* focus.
        maskingErrors.set(true);
    }

    public void hideImmediately()
    {
        if (popup != null)
            hide(true);
    }

    public void clearError()
    {
        errorMessage.set(StyledString.s(""));
        fixes.set(ImmutableList.of());
    }

    public BooleanExpression maskingErrors()
    {
        return maskingErrors;
    }

    public void unmaskErrors()
    {
        maskingErrors.set(false);
    }


    

    private void mouseHoverStatusChanged()
    {
        updateShowHide(false);
    }

    private void textFieldFocusChanged(boolean newFocused)
    {
        this.focused = newFocused;
        if (!focused)
        {
            // Lost focus, show errors:
            maskingErrors.set(false);
        }
        updateShowHide(true);
    }


    
    
    public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void addMessageAndFixes(StyledString msg, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> fixes, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> editor)
    {
        // The listener on this property should make the popup every time:
        errorMessage.set(StyledString.concat(errorMessage.get(), msg));
        this.fixes.set(Utility.concatI(this.fixes.get(), fixes.stream().map(q -> new FixInfo(q.getTitle(), q.getCssClasses(), () -> {
            Log.debug("Clicked fix: " + q.getTitle());
            if (popup != null)
                hide(true);
            try
            {
                Pair<EXPRESSION, EXPRESSION> replaceWith = q.getReplacement();
                editor.replaceSubExpression(replaceWith.getFirst(), replaceWith.getSecond());
            }
            catch (InternalException e)
            {
                Log.log(e);
                // User clicked expecting it to work, so better tell them:
                FXUtility.showError(e);
            }
        })).collect(ImmutableList.toImmutableList())));
        if (popup != null)
        {
            popup.fixList.setFixes(this.fixes.get());
        }
        //Log.debug("Message and fixes on [[" + textField.getText() + "]]: " + this.errorMessage + " " + this.fixes.get().size() + " " + this.fixes.get().stream().map(f -> f._debug_getName()).collect(Collectors.joining(", ")));
    }
    
    public boolean isShowingError()
    {
        return !errorMessage.get().toPlain().isEmpty();
    }
    
    public void setType(String type)
    {
        this.type.set(type);
    }
    
    
}
