package records.transformations.function.core;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.types.MutVar;
import records.types.TupleTypeExp;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.ValueFunction;

public class AsType extends FunctionDefinition
{
    public AsType()
    {
        super("asType", "asType.mini", AsType::type);
    }

    private static FunctionTypes type(TypeManager typeManager) throws InternalException
    {
        TypeExp innerType = new MutVar(null);
        TypeExp typeType = TypeExp.typeExpToTypeGADT(null, innerType);
        
        return new FunctionTypesUniform(typeManager, Instance::new, innerType, new TupleTypeExp(null, ImmutableList.of(typeType, innerType), true));
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
        {
            return Utility.castTuple(arg, 2)[1];
        }
    }
}
