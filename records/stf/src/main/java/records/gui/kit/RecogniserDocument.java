package records.gui.kit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.gui.flex.EditorKit;
import records.gui.flex.Recogniser;
import records.gui.flex.Recogniser.ErrorDetails;
import records.gui.flex.Recogniser.ParseProgress;
import records.gui.flex.Recogniser.SuccessDetails;
import utility.Either;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

public final class RecogniserDocument<V> extends DisplayDocument
{
    private final Recogniser<V> recogniser;
    private final FXPlatformBiConsumer<String, @Nullable V> saveChange;
    private final FXPlatformRunnable relinquishFocus;
    private OptionalInt curErrorPosition = OptionalInt.empty();
    private Either<ErrorDetails, SuccessDetails<V>> latestValue;

    public RecogniserDocument(String initialContent, Class<V> valueClass, Recogniser<V> recogniser, FXPlatformBiConsumer<String, @Nullable V> saveChange, FXPlatformRunnable relinquishFocus)
    {
        super(initialContent);
        this.recogniser = recogniser;
        this.saveChange = saveChange;
        this.relinquishFocus = relinquishFocus;
        recognise();
    }

    @Override
    void focusChanged(boolean focused)
    {
        super.focusChanged(focused);
        if (!focused)
            recognise();
    }

    @EnsuresNonNull("latestValue")
    @RequiresNonNull({"recogniser", "saveChange"})
    private void recognise(@UnknownInitialization(DisplayDocument.class) RecogniserDocument<V> this)
    {
        String text = getText();
        latestValue = recogniser.process(ParseProgress.fromStart(text), false)
                        .flatMap(SuccessDetails::requireEnd);
        latestValue.ifLeft(err -> {
            curErrorPosition = OptionalInt.of(err.errorPosition);
            saveChange.consume(text, null);
        });
        latestValue.ifRight(x -> {
            curErrorPosition = OptionalInt.empty();
            saveChange.consume(text, x.value);
        });
    }

    @Override
    Stream<Pair<Set<String>, String>> getStyledSpans()
    {
        if (!curErrorPosition.isPresent())
            return super.getStyledSpans();
        
        return ImmutableList.<Pair<Set<String>, String>>of(
            new Pair<Set<String>, String>(ImmutableSet.of(), getText().substring(0, curErrorPosition.getAsInt())),
            new Pair<Set<String>, String>(ImmutableSet.of(EditorKit.ERROR_CLASS), getText().substring(curErrorPosition.getAsInt(), curErrorPosition.getAsInt() + 1)),
            new Pair<Set<String>, String>(ImmutableSet.of(), getText().substring(curErrorPosition.getAsInt() + 1))
        ).stream();
    }

    @Override
    boolean hasError()
    {
        return curErrorPosition.isPresent();
    }

    @Override
    void defocus()
    {
        relinquishFocus.run();
    }

    public Either<ErrorDetails, V> getLatestValue()
    {
        return latestValue.map(x -> x.value);
    }
}
