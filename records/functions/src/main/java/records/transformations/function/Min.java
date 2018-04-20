package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableSet;
import records.error.InternalException;
import records.error.UserException;
import records.types.MutVar;
import records.types.TypeCons;
import records.types.TypeExp;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

public class Min extends FunctionDefinition
{
    public Min()
    {
        super("minimum", "minimum.mini", typeManager -> {
            TypeExp any = new MutVar(null, ImmutableSet.of("Comparable"));
            return new FunctionTypesUniform(typeManager, Instance::new, any, TypeExp.list(null, any));
        });
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value ListEx list = Utility.cast(param, ListEx.class);
            if (list.size() == 0)
            {
                throw new UserException("Cannot take minimum of empty list");
            }
            else
            {
                @Value Object min = list.get(0);
                for (int i = 1; i < list.size(); i++)
                {
                    @Value Object val = list.get(i);
                    if (Utility.compareValues(min, val) > 0)
                        min = val;
                }
                return min;
            }
        }
    }
}
