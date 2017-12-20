package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import records.data.unit.UnitManager;
import records.error.InternalException;

/**
 * Created by neil on 11/12/2016.
 */
public class FunctionGroup
{
    private final String name;
    private final @LocalizableKey String shortDescriptionKey;
    // Not final, due to setter:
    private ImmutableList<FunctionDefinition> functions;

    FunctionGroup(@LocalizableKey String shortDescriptionKey, FunctionDefinition singleMember)
    {
        this.name = singleMember.getName();
        this.shortDescriptionKey = shortDescriptionKey;
        this.functions = ImmutableList.of(singleMember);
    }
    
    FunctionGroup(String groupName, @LocalizableKey String shortDescriptionKey, ImmutableList<FunctionDefinition> members)
    {
        this.name = groupName;
        this.shortDescriptionKey = shortDescriptionKey;
        this.functions = members;
    }

    public String getName()
    {
        return name;
    }

    public @LocalizableKey String getShortDescriptionKey()
    {
        return shortDescriptionKey;
    }

    public ImmutableList<FunctionDefinition> getFunctions(UnitManager mgr) throws InternalException
    {
        return functions;
    }

    // For the temporal functions, it's too awkward to pass them to the constructor, so we also
    // have a setter: 
    void setFunctions(ImmutableList<FunctionDefinition> functions)
    {
        this.functions = functions;
    }
}
