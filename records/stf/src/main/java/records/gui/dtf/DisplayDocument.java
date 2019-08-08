package records.gui.dtf;

import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.dtf.Document.TrackedPosition.Bias;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A basic document that tracks positions and can be edited.
 */
public abstract class DisplayDocument extends Document
{
    private String content;
    private final List<WeakReference<TrackedPosition>> trackedPositions = new ArrayList<>();

    public DisplayDocument(String content)
    {
        this.content = content;
    }

    @Override
    Stream<Pair<Set<String>, String>> getStyledSpans(boolean focused)
    {
        return Stream.of(new Pair<>(ImmutableSet.of(), content));
    }

    @Override
    void replaceText(int startPosIncl, int endPosExcl, String text)
    {
        content = content.substring(0, startPosIncl) + text + content.substring(endPosExcl);

        for (Iterator<WeakReference<TrackedPosition>> iterator = trackedPositions.iterator(); iterator.hasNext(); )
        {
            WeakReference<TrackedPosition> ref = iterator.next();
            TrackedPosition trackedPosition = ref.get();
            if (trackedPosition == null)
                iterator.remove();
            else
            {
                if (trackedPosition.getPosition() >= startPosIncl)
                {
                    if (trackedPosition.getPosition() <= endPosExcl)
                    {
                        trackedPosition.moveTo(trackedPosition.getBias() == Bias.FORWARD ? startPosIncl + text.length() : startPosIncl);
                    }
                    else
                    {
                        trackedPosition.moveBy(text.length() - (endPosExcl - startPosIncl));
                    }
                }
            }
            
        }
        
        notifyListeners();
    }

    @Override
    TrackedPosition trackPosition(int pos, Bias bias, @Nullable FXPlatformRunnable onChange)
    {
        TrackedPosition trackedPosition = new TrackedPosition(pos, bias, onChange);
        trackedPositions.add(new WeakReference<>(trackedPosition));
        return trackedPosition;
    }

    @Override
    public int getLength()
    {
        return content.length();
    }

    @Override
    boolean isEditable()
    {
        return true;
    }

    @Override
    public String getText(@UnknownInitialization(DisplayDocument.class) DisplayDocument this)
    {
        return content;
    }

    @Override
    boolean hasError()
    {
        return false;
    }

    @Override
    void focusChanged(boolean focused)
    {
    }
}
