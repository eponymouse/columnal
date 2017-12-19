package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;

/**
 * Created by neil on 11/12/2016.
 */
public class FunctionGroup
{
    private final String name;
    private final @LocalizableKey String shortDescriptionKey;

    FunctionGroup(@LocalizableKey String shortDescriptionKey, FunctionDefinition singleMember)
    {
        this.name = singleMember.getName();
        this.shortDescriptionKey = shortDescriptionKey;
    }

    FunctionGroup(String groupName, @LocalizableKey String shortDescriptionKey, ImmutableList<FunctionDefinition> members)
    {
        this.name = groupName;
        this.shortDescriptionKey = shortDescriptionKey;
    }

    public String getName()
    {
        return name;
    }

    public @LocalizableKey String getShortDescriptionKey()
    {
        return shortDescriptionKey;
    }
}
