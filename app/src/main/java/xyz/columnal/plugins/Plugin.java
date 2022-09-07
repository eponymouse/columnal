package xyz.columnal.plugins;

import com.google.common.collect.ImmutableMap;
import xyz.columnal.data.SaveTag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationConsumer;

public abstract class Plugin
{
    private final String name;
    
    public Plugin(String name)
    {
        this.name = name;
    }

    public final String getPluginName()
    {
        return name;
    }
    
    public abstract ImmutableMap<String, SimulationConsumer<Pair<SaveTag, String>>> getHandledContent();
}
