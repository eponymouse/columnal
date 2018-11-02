package records.gui.expressioneditor;

import annotation.recorded.qual.UnknownIfRecorded;
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
import records.gui.expressioneditor.TopLevelEditor.ErrorInfo;
import records.gui.expressioneditor.TopLevelEditor.ErrorMessageDisplayer;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.QuickFix;
import styled.StyledShowable;
import styled.StyledString;
import utility.FXPlatformFunction;
import utility.FXPlatformSupplier;
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
    private final FXPlatformFunction<CaretSide, ImmutableList<ErrorInfo>> getAdjacentErrors;
    
    public static enum CaretSide { LEFT, RIGHT }
    
    @SuppressWarnings("initialization")
    public ExpressionInfoDisplay(VBox expressionNode, Label topLabel, TextField textField, FXPlatformFunction<CaretSide, ImmutableList<ErrorInfo>> getAdjacentErrors, ErrorMessageDisplayer popup)
    {
        this.expressionNode = expressionNode;
        this.textField = textField;
        this.popup = popup;
        this.getAdjacentErrors = getAdjacentErrors;
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), this::textFieldFocusChanged);
        FXUtility.addChangeListenerPlatformNN(textField.caretPositionProperty(), pos -> textFieldFocusChanged(textField.isFocused()));
        // This hover includes subcomponents:
        FXUtility.addChangeListenerPlatformNN(expressionNode.hoverProperty(), b -> {
            if (b)
                popup.mouseHoverBegan(makeError(), expressionNode);
            else
                popup.mouseHoverEnded(expressionNode);
        });
        FXUtility.addChangeListenerPlatformNN(errorMessage, s -> {
            popup.updateError(makeError(), textField, expressionNode, topLabel);
        });
        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            OptionalInt fKey = FXUtility.FKeyNumber(e.getCode());
            if (e.isShiftDown() && fKey.isPresent() && popup != null)
            {
                e.consume();
                popup.hidePopup(true);
                // 1 is F1, but should trigger fix zero:
                fixes.get().get(fKey.getAsInt() - 1).executeFix.run();
            }
            if (e.getCode() == KeyCode.ESCAPE)
            {
                popup.hidePopup(true);
            }
        });

        // Default is to mask errors if field has never *lost* focus.
        maskingErrors.set(true);
    }

    // Returns null if there isn't currently any error to display
    private @Nullable ErrorInfo makeError()
    {
        if (maskingErrors.get())
            return null;
        if (errorMessage.get().toPlain().isEmpty())
            return null;
        
        return new ErrorInfo(errorMessage.get(), fixes.get());
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
    
    private void textFieldFocusChanged(boolean focused)
    {
        if (!focused)
        {
            // Lost focus, show errors:
            maskingErrors.set(false);
        }
        
        if (focused)
        {
            ImmutableList.Builder<ErrorInfo> adjacentErrors = ImmutableList.builder();
            
            if (textField.getCaretPosition() == 0)
            {
                adjacentErrors.addAll(getAdjacentErrors.apply(CaretSide.LEFT));
            }
            // Not else; both can apply if field is empty:
            if (textField.getCaretPosition() == textField.getLength())
            {
                adjacentErrors.addAll(getAdjacentErrors.apply(CaretSide.RIGHT));
            }
            
            popup.keyboardFocusEntered(makeError(), adjacentErrors.build(), textField);
        }
        else
        {
            popup.keyboardFocusExited(makeError(), textField);
        }
    }


    
    
    public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void addMessageAndFixes(StyledString msg, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> fixes, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> editor)
    {
        this.fixes.set(Utility.concatI(this.fixes.get(), fixes.stream().map(q -> new FixInfo(q.getTitle(), q.getCssClasses(), () -> {
            Log.debug("Clicked fix: " + q.getTitle());
            if (popup != null)
                popup.hidePopup(true);
            try
            {
                @SuppressWarnings("recorded")
                Pair<EXPRESSION, EXPRESSION> replaceWith = q.getReplacement();
                editor.replaceSubExpression(replaceWith.getFirst(), replaceWith.getSecond());
            }
            catch (InternalException e)
            {
                Log.log(e);
                // User clicked expecting it to work, so better tell them:
                FXUtility.showError("Error applying fix", e);
            }
        })).collect(ImmutableList.toImmutableList())));
        // The listener on this property should make the popup every time and set the fixes too, hence we 
        // must set errorMessage after setting fixes:
        errorMessage.set(StyledString.concat(errorMessage.get(), msg));
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
    
    public ImmutableList<ErrorInfo> getErrors()
    {
        StyledString err = errorMessage.get();
        if (err.equals(StyledString.s("")))
        {
            return ImmutableList.of();
        }
        else
        {
            return ImmutableList.of(new ErrorInfo(err, fixes.get()));
        }
    }
}
