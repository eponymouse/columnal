package xyz.columnal.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.Objects;

@OnThread(Tag.Any)
public class Settings
{
    // IMPORTANT: if you add a field, you must redefine equals and hash code!
    
    // Can be null, in which case use PATH  (effectively just "R", but appears blank in settings)
    public final @Nullable File pathToRExecutable;
    public final boolean useColumnalRLibs;

    public Settings(@Nullable File pathToRExecutable, boolean useColumnalRLibs)
    {
        this.pathToRExecutable = pathToRExecutable;
        this.useColumnalRLibs = useColumnalRLibs;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Settings settings = (Settings) o;
        return useColumnalRLibs == settings.useColumnalRLibs &&
            Objects.equals(pathToRExecutable, settings.pathToRExecutable);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(pathToRExecutable, useColumnalRLibs);
    }
}
