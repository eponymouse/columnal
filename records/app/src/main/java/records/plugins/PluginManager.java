package records.plugins;

import log.Log;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public final class PluginManager
{
    private static final PluginManager INSTANCE = new PluginManager();
    
    private final ArrayList<Plugin> loadedPlugins = new ArrayList<>();

    public static PluginManager getInstance()
    {
        return INSTANCE;
    }

    public Stream<String> getLoadedPluginNames()
    {
        return loadedPlugins.stream().map(p -> p.getPluginName());
    }

    public void scanForPlugins(File directory)
    {
        if (!directory.exists())
        {
            Log.error("Could not find plugins directory " + directory.getAbsolutePath());
            return;
        }

        File[] pluginFiles = directory.listFiles(f -> f.getName().endsWith(".columnalplugin"));
        if (pluginFiles == null)
        {
            Log.error("Could not find any plugin files in " + directory.getAbsolutePath());
            return;
        }
        for (File pluginFile : pluginFiles)
        {
            try
            {
                JarFile jarFile = new JarFile(pluginFile, true);
                Manifest manifest = jarFile.getManifest();
                if (manifest == null)
                {
                    Log.error("No manifest in " + pluginFile.getAbsolutePath());
                    continue;
                }
                String mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                if (mainClass == null)
                {
                    Log.error("No listed plugin class in " + pluginFile.getAbsolutePath());
                    continue;
                }
                ClassLoader classLoader = new URLClassLoader(new URL[] {pluginFile.toURI().toURL()}, getClass().getClassLoader());
                Class<?> pluginLoader = classLoader.loadClass(mainClass);
                LoadablePlugin loadablePlugin = (LoadablePlugin) (pluginLoader.getDeclaredConstructor().newInstance());

                Plugin plugin = loadablePlugin.load();
                loadedPlugins.add(plugin);
            }
            catch (Throwable e)
            {
                Log.log("Problem reading plugin " + pluginFile.getName(), e);
            }
        }
    }
}
