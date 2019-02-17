package records.transformations.function.tuple;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import records.data.ValueFunction;

public class TupleAccessors
{
    public static ImmutableList<FunctionDefinition> getFunctions() throws InternalException
    {
        ImmutableList<@FuncDocKey String> names = ImmutableList.<@FuncDocKey String>of("tuple:first", "tuple:second", "tuple:third", "tuple:fourth", "tuple:fifth", "tuple:sixth", "tuple:seventh", "tuple:eighth", "tuple:ninth", "tuple:tenth");
        
        ImmutableList.Builder<FunctionDefinition> functions = ImmutableList.builder();
        for (int i = 0; i < names.size(); i++)
        {
            int zeroBased = i;
            functions.add(new FunctionDefinition(names.get(i)) {
                @Override
                public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
                {
                    return new ValueFunction()
                    {
                        @Override
                        @SuppressWarnings("value") // Because we can't use castTuple
                        public @OnThread(Tag.Simulation) @Value Object call() throws InternalException, UserException
                        {
                            return arg(zeroBased);
                        }
                    };
                }
            });
        }
        return functions.build();
    }
}
