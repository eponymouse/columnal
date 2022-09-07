package xyz.columnal.transformations.function.optional;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction1;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.TaggedValue;

public class GetOptionalOrError extends FunctionDefinition
{
    public GetOptionalOrError() throws InternalException
    {
        super("optional:get optional");
    }
    
    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction1<TaggedValue>(TaggedValue.class) {
            @Override
            public @OnThread(Tag.Simulation) @Value Object call1(@Value TaggedValue taggedValue) throws InternalException, UserException
            {
                if (taggedValue.getTagIndex() == 1 && taggedValue.getInner() != null)
                    return taggedValue.getInner();
                else
                    throw new UserException("get optional - missing value");
            }
        };
    }
}
