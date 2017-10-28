package records.transformations.function;

import annotation.qual.Value;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionType.AnyType;
import records.transformations.function.FunctionType.ArrayType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.Collections;
import java.util.List;

public class Max extends FunctionDefinition
{
    public Max()
    {
        super("max", "max.short");
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        return Collections.singletonList(new FunctionType(Instance::new, new ArrayType(new AnyType()), null));
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
                @Value Object max = list.get(0);
                for (int i = 1; i < list.size(); i++)
                {
                    @Value Object val = list.get(i);
                    if (Utility.compareValues(max, val) < 0)
                        max = val;
                }
                return max;
            }
        }
    }
}
