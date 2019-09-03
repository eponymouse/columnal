package records.plugins;

import com.google.common.collect.ImmutableMap;
import records.data.SaveTag;
import utility.Pair;
import utility.SimulationConsumer;

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
