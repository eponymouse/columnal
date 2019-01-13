package records.gui.flex;

import com.google.common.collect.ImmutableList;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.fxmisc.richtext.model.EditableStyledDocument;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SimpleEditableStyledDocument;
import org.fxmisc.richtext.model.StyledText;
import records.gui.flex.Recogniser.ParseProgress;
import records.gui.flex.Recogniser.SuccessDetails;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;

import java.util.Collection;
import java.util.function.UnaryOperator;

@OnThread(Tag.FXPlatform)
public class EditorKit<T>
{
    private final Recogniser<T> recogniser;
    private final FXPlatformBiConsumer<String, @Nullable T> onChange;
    private final FXPlatformRunnable relinquishFocus;
    private final String ERROR_CLASS = "input-error";
    private Either<StyledString, T> latestValue = Either.left(StyledString.s("Loading"));
    private ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> focusedDocument;
    private Pair<ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>>, UnaryOperator<Integer>> unfocusedDocument;
    private @Nullable FlexibleTextField field;
    private boolean settingDocument = false;
    private ChangeListener<String> changeListener = new TextChangeListener();
    private @Nullable FXPlatformRunnable onFocusLost;

    private void onUnfocusedChange()
    {
        // Look for non-focused text changes and update our latest value:
        if (field != null && !field.isFocused() && !settingDocument)
            setLatestValue(field.getText(), false);
    }

    public EditorKit(String initialValue, Recogniser<T> recogniser, FXPlatformBiConsumer<String, @Nullable T> onChange, FXPlatformRunnable relinquishFocus)
    {
        this.recogniser = recogniser;
        this.onChange = onChange;
        this.relinquishFocus = relinquishFocus;
        this.focusedDocument = ReadOnlyStyledDocument.fromString(initialValue, ImmutableList.<String>of(), ImmutableList.<String>of(), StyledText.<Collection<String>>textOps());
        this.unfocusedDocument = new Pair<>(focusedDocument, UnaryOperator.identity());
        setLatestValue(initialValue, false);
    }

    public boolean isEditable()
    {
        return true;
    }

    public void setField(@Nullable FlexibleTextField flexibleTextField)
    {
        FlexibleTextField oldField = this.field;
        if (oldField != null)
        {
            oldField.textProperty().removeListener(changeListener);
            // Save previous document:
            focusedDocument = ReadOnlyStyledDocument.from(oldField.getDocument());
            unfocusedDocument = new Pair<>(ReadOnlyStyledDocument.from(focusedDocument), UnaryOperator.identity());
            oldField.replaceText("");
        }
        this.field = flexibleTextField;

        if (flexibleTextField != null)
        {
            flexibleTextField.replace(flexibleTextField.isFocused() ? focusedDocument : unfocusedDocument.getFirst());
            flexibleTextField.textProperty().addListener(changeListener);
            flexibleTextField.setEditable(isEditable());
        }
    }

    public void relinquishFocus()
    {
        relinquishFocus.run();
    }

    public void focusChanged(String text, boolean focused)
    {
        final FlexibleTextField fieldFinal = this.field;
        if (fieldFinal == null)
            return; // Shouldn't happen, but satisfy nullness checker
        
        if (!focused)
        {
            focusedDocument = ReadOnlyStyledDocument.from(fieldFinal.getDocument());
            // Defocusing copies focused over unfocused, so that we do not use
            // an old unfocusedDocument without the latest changes.  Any unfocused
            // special content will be set later on
            unfocusedDocument = new Pair<>(focusedDocument, UnaryOperator.identity());
            setLatestValue(text, true);
            if (onFocusLost != null)
                onFocusLost.run();
        }
        else
        {
            int pos = fieldFinal.getCaretPosition();
            fieldFinal.replace(focusedDocument);
            fieldFinal.moveTo(unfocusedDocument.getSecond().apply(pos));
        }
    }

    @RequiresNonNull({"recogniser", "onChange"})
    @EnsuresNonNull("latestValue")
    private void setLatestValue(@UnknownInitialization(Object.class)EditorKit<T>this, String text, boolean callListener)
    {
        // Clear existing errors:
        if (field != null)
            field.getContent().setStyleSpans(0, field.getContent().getStyleSpans(0, field.getLength()).mapStyles(ss -> ss.stream().filter(s -> !s.equals(ERROR_CLASS)).collect(ImmutableList.<String>toImmutableList())));
        
        latestValue = recogniser.process(ParseProgress.fromStart(text), false).flatMap(SuccessDetails::requireEnd).mapBoth(err -> {
            //Log.debug("### Entry error: " + err.error.toPlain() + " in: " + text);
            if (field != null)
                field.getContent().setStyleSpans(err.errorPosition, field.getContent().getStyleSpans(err.errorPosition, err.errorPosition + 1).mapStyles(addToSet(ERROR_CLASS)));
            return err.error;
        }, succ -> {
            // TODO apply styles
            return succ.value;
        });
        if (callListener)
            onChange.consume(text, latestValue.leftToNull());
    }

    private static UnaryOperator<Collection<String>> addToSet(String item)
    {
        return ss -> {
            if (!ss.contains(item))
                return Utility.prependToList(item, ss);
            else
                return ss;
        };
    }

    public Either<StyledString, T> getLatestValue()
    {
        return latestValue;
    }
    
    public void setUnfocusedDocument(ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc, UnaryOperator<Integer> mapCaretPos)
    {
        unfocusedDocument = new Pair<>(doc, mapCaretPos);
        // Note: only ReadOnlyStyledDocument has implemented equals, so the order
        // here is actually crucial:
        if (field != null && !field.isFocused() && !doc.equals(field.getDocument()))
        {
            settingDocument = true;
            field.replace(doc);
            settingDocument = false;
        }
    }

    public void setOnFocusLost(FXPlatformRunnable onFocusLost)
    {
        this.onFocusLost = onFocusLost;
    }

    private class TextChangeListener implements ChangeListener<String>
    {
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        @Override
        public void changed(ObservableValue<? extends String> prop, String oldVal, String newVal)
        {
            onUnfocusedChange();
        }
    }
}
