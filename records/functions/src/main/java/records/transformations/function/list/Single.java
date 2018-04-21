package records.transformations.function.list;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.types.MutVar;
import records.types.TypeExp;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

public class Single extends FunctionDefinition
{
    public Single()
    {
        super("list/single", "single.mini", typeManager -> {
            TypeExp any = new MutVar(null);
            return new FunctionTypesUniform(typeManager, Instance::new, any, TypeExp.list(null, any)
            );
        });
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            ListEx list = Utility.cast(param, ListEx.class);
            if (list.size() == 1)
                return list.get(0);
            else
                throw new UserException("List must be of size 1, but was size " + list.size());
        }
    }
}
