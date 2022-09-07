package xyz.columnal.transformations.function.core;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.transformations.expression.function.ValueFunction;

public class TypeOf extends FunctionDefinition
{
    public TypeOf() throws InternalException
    {
        super("core:type of");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
        {
            // TODO return the actual type literal once we define the GADT
            return new TaggedValue(0, null, DataTypeUtility.fromTags(ImmutableList.<TagType<Object>>of(new TagType<Object>("Type", null))));
        }
    }
}
