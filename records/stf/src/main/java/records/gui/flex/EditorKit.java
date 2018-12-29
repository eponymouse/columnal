package records.gui.flex;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.SimpleEditableStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import records.gui.flex.Recogniser.ParseProgress;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformBiConsumer;

import java.util.Collection;

@OnThread(Tag.FXPlatform)
public class EditorKit<T>
{
    private final Recogniser<T> recogniser;
    private final FXPlatformBiConsumer<String, T> onChange;
    private Either<StyledString, T> latestValue = Either.left(StyledString.s("Loading"));

    public EditorKit(Recogniser<T> recogniser, FXPlatformBiConsumer<String, T> onChange)
    {
        this.recogniser = recogniser;
        this.onChange = onChange;
    }

    public boolean isEditable()
    {
        return true;
    }

    public void setField(@Nullable FlexibleTextField flexibleTextField)
    {
        
    }

    public StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> getLatestDocument()
    {
        return new SimpleEditableStyledDocument<>(ImmutableList.of(), ImmutableList.of());
    }

    public void fieldChanged(String text, boolean focused)
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
