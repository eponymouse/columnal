package records.transformations.function.list;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionDefinition.FunctionTypes;
import records.transformations.function.FunctionDefinition.FunctionTypesUniform;
import records.transformations.function.FunctionDefinition.TypeMatcher;
import records.transformations.function.FunctionGroup;
import records.transformations.function.FunctionInstance;
import records.types.MutVar;
import records.types.TupleTypeExp;
import records.types.TypeCons;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

import java.util.function.Supplier;

public class AnyAllNone extends FunctionGroup
{
    public AnyAllNone()
    {
        super("any/all/none", "anyAllNone.short", ImmutableList.of(
            new FunctionDefinition("any", "any.mini", new Matcher(() -> new Processor(true, null, false))),
            new FunctionDefinition("all", "all.mini", new Matcher(() -> new Processor(null, false, true))),
            new FunctionDefinition("none", "none.mini", new Matcher(() -> new Processor(false, null, true)))));
    }
    
    private static class Processor extends FunctionInstance
    {
        private final @Nullable @Value Boolean returnIfTrueFound;
        private final @Nullable @Value Boolean returnIfFalseFound;
        private final @Value Boolean returnAtEnd;

        private Processor(@Nullable Boolean returnIfTrueFound, @Nullable Boolean returnIfFalseFound, Boolean returnAtEnd)
        {
            this.returnIfTrueFound = returnIfTrueFound == null ? null : DataTypeUtility.value(returnIfTrueFound);
            this.returnIfFalseFound = returnIfFalseFound == null ? null : DataTypeUtility.value(returnIfFalseFound);
            this.returnAtEnd = DataTypeUtility.value(returnAtEnd);
        }

        @Override
        @OnThread(Tag.Simulation)
        public @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            @Value Object @Value[] args = Utility.castTuple(param, 2);
            ListEx list = Utility.cast(args[0], ListEx.class);
            ValueFunction processElement = Utility.cast(args[1], ValueFunction.class);
            for (int i = 0; i < list.size(); i++)
            {
                @Value Boolean result = Utility.cast(processElement.call(list.get(i)), Boolean.class);
                if (result && returnIfTrueFound != null)
                    return returnIfTrueFound;
                else if (!result && returnIfFalseFound != null)
                    return returnIfFalseFound;
            }
            return returnAtEnd;
        }
    }

    private static class Matcher implements TypeMatcher
    {
        private final Supplier<FunctionInstance> makeInstance;

        private Matcher(Supplier<FunctionInstance> makeInstance)
        {
            this.makeInstance = makeInstance;
        }

        @Override
        public FunctionTypes makeParamAndReturnType(TypeManager typeManager) throws InternalException
        {
            MutVar elemType = new MutVar(null);
            return new FunctionTypesUniform(typeManager, makeInstance, TypeExp.fromConcrete(null, DataType.BOOLEAN), new TupleTypeExp(null, ImmutableList.of(new TypeCons(null, TypeExp.CONS_LIST, elemType), new TypeCons(null, TypeExp.CONS_FUNCTION, elemType, TypeExp.fromConcrete(null, DataType.BOOLEAN))), true));
        }
    }
}
