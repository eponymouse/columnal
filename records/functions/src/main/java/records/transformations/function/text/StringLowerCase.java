package records.transformations.function.text;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.ValueFunction1;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;

import java.util.Locale;

public class StringLowerCase extends FunctionDefinition
{

    public static final String NAME = "text:lower case";

    public StringLowerCase() throws InternalException
    {
        super(NAME);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction1<String>(String.class) {

            @Override
            public @OnThread(Tag.Simulation) @Value Object call1(@Value String s) throws InternalException, UserException
            {
                return DataTypeUtility.value(s.toLowerCase(Locale.ENGLISH));
            }
        };
    }
}
