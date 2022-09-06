package utility.gui;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Arrays;
import java.util.Comparator;

public interface TimedFocusable
{
    // In terms of System.currentTimeMillis()
    @OnThread(Tag.FXPlatform)
    public long lastFocusedTime();
    
    @OnThread(Tag.FXPlatform)
    public static @Nullable TimedFocusable getRecentlyFocused(TimedFocusable... items)
    {
        long cur = System.currentTimeMillis();
        return Arrays.stream(items).map(x -> new Pair<>(x, x.lastFocusedTime())).filter(p -> p.getSecond() > cur - 250L).sorted(Comparator.comparing(p -> p.getSecond())).map(p -> p.getFirst()).findFirst().orElse(null);
    }
}
