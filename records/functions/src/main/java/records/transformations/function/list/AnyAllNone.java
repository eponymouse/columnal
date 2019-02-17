package records.transformations.function.list;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import records.data.ValueFunction;

// Maybe this should be a package
public abstract class AnyAllNone
{
    private static class Processor extends ValueFunction
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
        public @Value Object call() throws UserException, InternalException
        {
            ListEx list = arg(0, ListEx.class);
            ValueFunction processElement = arg(1, ValueFunction.class);
            for (int i = 0; i < list.size(); i++)
            {
                @Value Boolean result = Utility.cast(processElement.call(new @Value Object [] {list.get(i)}), Boolean.class);
                if (result && returnIfTrueFound != null)
                {
                    if (recordBooleanExplanation)
                    {
                        int iFinal = i;
                        booleanExplanation = withArgLoc(0, a -> a.getListElementLocation(iFinal));
                    }
                    return returnIfTrueFound;
                }
                else if (!result && returnIfFalseFound != null)
                {
                    if (recordBooleanExplanation)
                    {
                        int iFinal = i;
                        booleanExplanation = withArgLoc(0, a -> a.getListElementLocation(iFinal));
                    }
                    return returnIfFalseFound;
                }
            }
            return returnAtEnd;
        }
    }

    public static class Any extends FunctionDefinition
    {
        public Any() throws InternalException
        {
            super("listprocess:any");
        }

        @Override
        public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
        {
            return new Processor(true, null, false);
        }
    }

    public static class All extends FunctionDefinition
    {
        public All() throws InternalException
        {
            super("listprocess:all");
        }

        @Override
        public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
        {
            return new Processor(null, false, true);
        }
    }

    public static class None extends FunctionDefinition
    {
        public None() throws InternalException
        {
            super("listprocess:none");
        }

        @Override
        public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
        {
            return new Processor(false, null, true);
        }
    }
}
