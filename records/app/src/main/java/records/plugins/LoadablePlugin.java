package records.plugins;

/**
 * An indirection for loading plugins.  Subclasses must always have a zero-arg constructor,
 * which will be called by the plugin manager.  Then the load method (or methods plural in future)
 * can take safely-typed parameters to create the actual plugin instance.
 */
public interface LoadablePlugin
{
    Plugin load() throws Exception;
}
