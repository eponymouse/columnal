package records.transformations.function.conversion;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
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
import utility.Utility;

public class ExtractNumber extends FunctionDefinition
{
    public ExtractNumber() throws InternalException
    {
        super("conversion:number from text");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction1<String>(String.class) {
            @Override
            public @OnThread(Tag.Simulation) @Value Object call1(@Value String s) throws InternalException, UserException
            {
                @MonotonicNonNull @Value Number result = null;
                for (int i = 0; i < s.length(); i++)
                {
                    if ('0' <= s.charAt(i) && s.charAt(i) <= '9')
                    {   
                        int start = i;
                        // Look before us for minus sign:
                        if (i > 0 && s.charAt(i - 1) == '-')
                            start -= 1;
                        // Chomp all digits:
                        while (i < s.length() && (('0' <= s.charAt(i) && s.charAt(i) <= '9') || s.charAt(i) == ','))
                            i += 1;
                        // Chomp dot and more digits:
                        if (i < s.length() && s.charAt(i) == '.')
                        {
                            i += 1;
                            while (i < s.length() && ('0' <= s.charAt(i) && s.charAt(i) <= '9'))
                                i += 1;
                        }
                        @Value Number parsed = null;
                        try
                        {
                            parsed = Utility.parseNumber(s.substring(start, i).replaceAll(",", ""));
                        }
                        catch (UserException e)
                        {
                            // Shouldn't happen; log but continue
                            Log.log(e);
                        }
                        if (parsed != null)
                        {
                            if (result != null)
                                throw new UserException("Two numbers found: " + result + " and " + parsed);
                            result = parsed;
                        }
                        
                        // Counteract the next increment:
                        i -=1;
                    }
                }
                
                if (result == null)
                    throw new UserException("No number found");
                else
                    return result;
            }
        };
    }
}
