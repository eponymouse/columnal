package records.gui.flex;

import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
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

import java.util.Collection;
import java.util.function.UnaryOperator;

@OnThread(Tag.FXPlatform)
public class EditorKit<T>
{
    private final Recogniser<T> recogniser;
    private final FXPlatformBiConsumer<String, T> onChange;
    private final FXPlatformRunnable relinquishFocus;
    private Either<StyledString, T> latestValue = Either.left(StyledString.s("Loading"));
    private ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> latestDocument;
    private Pair<ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>>, UnaryOperator<Integer>> unfocusedDocument;
    private @Nullable FlexibleTextField field;

    public EditorKit(String initialValue, Recogniser<T> recogniser, FXPlatformBiConsumer<String, T> onChange, FXPlatformRunnable relinquishFocus)
    {
        this.recogniser = recogniser;
        this.onChange = onChange;
        this.relinquishFocus = relinquishFocus;
        this.latestValue = recogniser.process(ParseProgress.fromStart(initialValue), false).mapBoth(err -> err.error, succ -> succ.value);        
        this.latestDocument = ReadOnlyStyledDocument.fromString(initialValue, ImmutableList.of(), ImmutableList.of(), StyledText.textOps());
        this.unfocusedDocument = new Pair<>(latestDocument, UnaryOperator.identity());
    }

    public boolean isEditable()
    {
        return true;
    }

    public void setField(@Nullable FlexibleTextField flexibleTextField)
    {
        if (this.field != null)
        {
            latestDocument = ReadOnlyStyledDocument.from(field.getDocument());
            unfocusedDocument = new Pair<>(latestDocument, UnaryOperator.identity());
        }
        this.field = flexibleTextField;
    }

    public ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> getLatestDocument(boolean focused)
    {
        return focused ? latestDocument : unfocusedDocument.getFirst();
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
            latestDocument = ReadOnlyStyledDocument.from(fieldFinal.getDocument());
            latestValue = recogniser.process(ParseProgress.fromStart(text), false).flatMap(SuccessDetails::requireEnd).mapBoth(err -> {
                Log.debug("### Entry error: " + err.error.toPlain() + " in: " + text);
                return err.error;
            }, succ -> {
                // TODO apply styles
                onChange.consume(text, succ.value);
                return succ.value;
            });
            unfocusedDocument = new Pair<>(latestDocument, UnaryOperator.identity());
        }
        else
        {
            int pos = fieldFinal.getCaretPosition();
            fieldFinal.replace(latestDocument);
            fieldFinal.moveTo(unfocusedDocument.getSecond().apply(pos));
        }
    }

    public Either<StyledString, T> getLatestValue()
    {
        return latestValue;
    }
    
    public void setUnfocusedDocument(ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc, UnaryOperator<Integer> mapCaretPos)
    {
        unfocusedDocument = new Pair<>(doc, mapCaretPos);
        if (field != null && !field.isFocused())
            field.replace(doc);
    }
}
