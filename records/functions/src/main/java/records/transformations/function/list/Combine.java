package records.transformations.function.list;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.types.MutVar;
import records.types.TupleTypeExp;
import records.types.TypeClassRequirements;
import records.types.TypeCons;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

// Foldr, by another name.
public class Combine extends FunctionDefinition
{
    public Combine()
    {
        super("listprocess/combine", "combine.mini", Combine::combine);
    }

    private static FunctionTypes combine(TypeManager typeManager)
    {
        TypeExp innerType = new MutVar(null, TypeClassRequirements.empty());
        TypeExp listOfInner = TypeCons.list(null, innerType);
        TypeExp combineFunction = TypeCons.function(null,
            new TupleTypeExp(null, ImmutableList.of(innerType, innerType), true),
            innerType    
        );
        
        return new FunctionTypesUniform(typeManager, Instance::new,
            innerType,
            new TupleTypeExp(null, ImmutableList.of(listOfInner, combineFunction), true)
        );
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
        {
            @Value Object @Value [] args = Utility.castTuple(arg, 2);
            ListEx list = Utility.cast(args[0], ListEx.class);
            if (list.size() == 0)
                throw new UserException("Called combine with empty list");
            @Value Object acc = list.get(0);
            ValueFunction function = Utility.cast(args[1], ValueFunction.class);
            for (int i = 1; i < list.size(); i++)
            {
                acc = function.call(new @Value Object[] {acc, list.get(i)});
            }
            return acc;
        }
    }
}
