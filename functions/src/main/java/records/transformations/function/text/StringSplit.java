package records.transformations.function.text;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;

import java.util.regex.Pattern;

public class StringSplit extends FunctionDefinition
{
    public StringSplit() throws InternalException
    {
        super("text:split text");
    }
    
    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction2<String, String>(String.class, String.class) {

            @Override
            public @OnThread(Tag.Simulation) @Value Object call2(@Value String text, @Value String separator) throws InternalException, UserException
            {
                String[] split;
                if (separator.isEmpty())
                    split = text.codePoints().mapToObj(n -> Utility.codePointToString(n)).toArray(String[]::new);
                else
                    split = text.split(Pattern.quote(separator), -1);
                return DataTypeUtility.value(new ListEx() {

                    @Override
                    public int size() throws InternalException, UserException
                    {
                        return split.length;
                    }

                    @Override
                    public @Value Object get(int index) throws InternalException, UserException
                    {
                        if (index < 0 || index >= split.length)
                            throw new UserException("Invalid list index: " + index);
                        return DataTypeUtility.value(split[index]);
                    }
                });
            }
        };
    }
}
