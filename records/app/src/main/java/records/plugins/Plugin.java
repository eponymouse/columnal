package records.plugins;

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
}
