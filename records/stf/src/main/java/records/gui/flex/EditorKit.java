package records.gui.flex;

import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.EditableStyledDocument;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SimpleEditableStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.flex.Recogniser.ParseProgress;
import records.gui.stf.TableDisplayUtility;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.Collection;

@OnThread(Tag.FXPlatform)
public class EditorKit<T>
{
    private final Recogniser<T> recogniser;
    private final FXPlatformBiConsumer<String, T> onChange;
    private final FXPlatformRunnable relinquishFocus;
    private Either<StyledString, T> latestValue = Either.left(StyledString.s("Loading"));
    private ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> latestDocument;
    private @Nullable FlexibleTextField field;

    public EditorKit(String initialValue, Recogniser<T> recogniser, FXPlatformBiConsumer<String, T> onChange, FXPlatformRunnable relinquishFocus)
    {
        this.recogniser = recogniser;
        this.onChange = onChange;
        this.relinquishFocus = relinquishFocus;
        this.latestValue = recogniser.process(ParseProgress.fromStart(initialValue)).mapBoth(err -> err.error, succ -> succ.value);        
        this.latestDocument = ReadOnlyStyledDocument.fromString(initialValue, ImmutableList.of(), ImmutableList.of(), StyledText.textOps());
    }

    public boolean isEditable()
    {
        return true;
    }

    public void setField(@Nullable FlexibleTextField flexibleTextField)
    {
        if (this.field != null)
            latestDocument = ReadOnlyStyledDocument.from(field.getDocument());
        this.field = flexibleTextField;
    }

    public EditableStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> getLatestDocument()
    {
        SimpleEditableStyledDocument<Collection<String>, Collection<String>> doc = new SimpleEditableStyledDocument<>(ImmutableList.of(), ImmutableList.of());
        doc.replace(0, 0, latestDocument);
        return doc;
    }

    public void relinquishFocus()
    {
        relinquishFocus.run();
    }

    public void focusChanged(String text, boolean focused)
    {
        if (!focused)
        {
            latestValue = recogniser.process(ParseProgress.fromStart(text)).mapBoth(err -> {
                return err.error;
            }, succ -> {
                // TODO apply styles
                onChange.consume(text, succ.value);
                return succ.value;
            });
        }
    }
}
