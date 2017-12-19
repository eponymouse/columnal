package records.transformations.function;

import annotation.qual.Value;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionType.FunctionTypes;
import records.transformations.function.FunctionType.TypeMatcher;
import records.types.MutVar;
import records.types.TypeCons;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.Collections;
import java.util.List;

public class Min extends FunctionGroup
{
    public Min()
    {
        super("min", "min.short");
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        TypeMatcher listOfAny = () -> {
            TypeExp any = new MutVar(null);
            return new FunctionTypes(any, new TypeCons(null, TypeExp.CONS_LIST, any));
        };
        return Collections.singletonList(new FunctionType(Instance::new, listOfAny, null));
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
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
