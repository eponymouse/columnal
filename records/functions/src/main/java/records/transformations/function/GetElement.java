package records.transformations.function;

import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition.FunctionTypes;
import records.transformations.function.FunctionDefinition.TypeMatcher;
import records.types.MutVar;
import records.types.NumTypeExp;
import records.types.TupleTypeExp;
import records.types.TypeCons;
import records.types.TypeExp;
import records.types.units.UnitExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 17/01/2017.
 */
public class GetElement extends FunctionGroup
{
    public GetElement()
    {
        super("element", "element.short");
    }

    // Takes parameters: column/array, index
    @Override
    public List<FunctionDefinition> getOverloads(UnitManager mgr) throws InternalException
    {
        TypeMatcher listOfAnyAndIndex = () -> {
            TypeExp any = new MutVar(null);
            return new FunctionTypes(any, new TupleTypeExp(null,
                ImmutableList.of(
                    new TypeCons(null, TypeExp.CONS_LIST, any),
                    new NumTypeExp(null, UnitExp.SCALAR)
                ), true
            ));
        };
        
        return Collections.singletonList(new FunctionDefinition(Instance::new, listOfAnyAndIndex, null));
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        @OnThread(Tag.Simulation)
        public @Value Object getValue(int rowIndex, @Value Object params) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(params, 2);
            @UserIndex int userIndex = DataTypeUtility.userIndex(paramList[1]);
            return Utility.getAtIndex(Utility.valueList(paramList[0]), userIndex);
        }
    }
}
