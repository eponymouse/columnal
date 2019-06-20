package utility;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.InputStream;
import java.net.URL;

/**
 * Used to call getResource with the right class loader
 */
@OnThread(Tag.Any)
public class ResourceUtility
{
    @SuppressWarnings("nullness")
    private static ClassLoader classLoader = ResourceUtility.class.getClassLoader();

    public static @Nullable URL getResource(String name)
    {
        return classLoader.getResource(name);
    }
    
    public static @Nullable InputStream getResourceAsStream(String name)
    {
        return classLoader.getResourceAsStream(name);
    }

    public static void setClassLoader(ClassLoader classLoader)
    {
        ResourceUtility.classLoader = classLoader;
    }
}
