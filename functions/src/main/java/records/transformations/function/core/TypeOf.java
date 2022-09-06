package records.transformations.function.core;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import records.transformations.expression.function.ValueFunction;

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
