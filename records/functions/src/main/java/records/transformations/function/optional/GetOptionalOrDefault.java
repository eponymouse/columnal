package records.transformations.function.optional;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.ValueFunction1;
import records.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;

public class GetOptionalOrDefault extends FunctionDefinition
{
    public GetOptionalOrDefault() throws InternalException
    {
        super("optional:get optional or");
    }
    
    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction2<TaggedValue, Object>(TaggedValue.class, Object.class) {
            @Override
            public @OnThread(Tag.Simulation) @Value Object call2(@Value TaggedValue taggedValue, @Value Object defaultValue) throws InternalException, UserException
            {
                if (taggedValue.getTagIndex() == 1 && taggedValue.getInner() != null)
                    return taggedValue.getInner();
                else
                    return defaultValue;
            }
        };
    }
}
