package xyz.columnal.transformations.function.list;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.transformations.expression.function.ValueFunction;
import threadchecker.Tag;

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
        public @Value Object _call() throws UserException, InternalException
        {
            ListEx list = arg(0, ListEx.class);
            for (int i = 0; i < list.size(); i++)
            {
                int iFinal = i;
                @Value Boolean result = Utility.cast(callArg(1, new @Value Object [] {list.get(i)}), Boolean.class);
                if (result && returnIfTrueFound != null)
                {
                    addUsedLocations(args -> Utility.streamNullable(args.get(0).getListElementLocation(iFinal)));
                    return returnIfTrueFound;
                }
                else if (!result && returnIfFalseFound != null)
                {
                    addUsedLocations(args -> Utility.streamNullable(args.get(0).getListElementLocation(iFinal)));
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
