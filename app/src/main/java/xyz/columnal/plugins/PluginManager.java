package records.plugins;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import xyz.columnal.log.Log;
import xyz.columnal.data.PluggedContentHandler;
import xyz.columnal.data.SaveTag;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationConsumer;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One instance per opened project.
 */
public final class PluginManager implements PluggedContentHandler
{
    private final ArrayList<Plugin> loadedPlugins = new ArrayList<>();
    
    public PluginManager()
    {
        try
        {
            scanForPlugins(new File(Utility.getStorageDirectory(), "plugins"));
            Log.normal("Loaded plugins: " + getLoadedPluginNames().collect(Collectors.joining(", ")));
        }
        catch (IOException e)
        {
            Log.log("Error finding storage directory to load plugins", e);
        }
    }

    public Stream<String> getLoadedPluginNames()
    {
        return loadedPlugins.stream().map(p -> p.getPluginName());
    }

    public ImmutableList<Plugin> getLoadedPlugins()
    {
        return ImmutableList.copyOf(loadedPlugins);
    }

    @Override
    public ImmutableMap<String, SimulationConsumer<Pair<SaveTag, String>>> getHandledContent(Consumer<StyledString> onError)
    {
        HashMap<String, Pair<String, SimulationConsumer<Pair<SaveTag, String>>>> combined = new HashMap<>();

        for (Plugin plugin : getLoadedPlugins())
        {
            for (Entry<String, SimulationConsumer<Pair<SaveTag, String>>> handled : plugin.getHandledContent().entrySet())
            {
                Pair<String, SimulationConsumer<Pair<SaveTag, String>>> prev = combined.putIfAbsent(handled.getKey(), new Pair<>(plugin.getPluginName(), handled.getValue()));
                if (prev != null)
                {
                    onError.accept(StyledString.s("Plugin " + plugin.getPluginName() + " handles content " + handled.getKey() + " but so does plugin " + prev.getFirst()));
                }
            }
        }
        return Utility.mapValues(combined, p -> p.getSecond());
    }

    private void scanForPlugins(File directory)
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
