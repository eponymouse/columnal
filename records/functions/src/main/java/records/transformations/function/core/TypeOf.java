package records.transformations.function.core;

import annotation.qual.Value;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.types.MutVar;
import records.types.TypeClassRequirements;
import records.types.TypeCons;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.TaggedValue;
import utility.ValueFunction;

public class TypeOf extends FunctionDefinition
{
    public TypeOf()
    {
        super("core/typeOf", "typeOf.mini", TypeOf::valueToType);
    }

    private static FunctionTypes valueToType(TypeManager typeManager) throws InternalException
    {
        MutVar paramType = new MutVar(null, TypeClassRequirements.empty());
        TypeExp returnType = TypeCons.typeExpToTypeGADT(null, paramType);        
        
        return new FunctionTypesUniform(typeManager, Instance::new, returnType, paramType);
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
        {
            // TODO return the actual type literal once we define the GADT
            return new TaggedValue(0, null);
        }
    }
}
