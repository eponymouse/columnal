package xyz.columnal.transformations.expression.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;

public interface FunctionLookup
{
    /**
     * Name can be unscoped, or scoped with backslashes
     */
    public @Nullable StandardFunctionDefinition lookup(String functionName) throws InternalException;

    public ImmutableList<StandardFunctionDefinition> getAllFunctions() throws InternalException;
}
