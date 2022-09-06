package records.transformations.function.text;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.util.ArrayList;

public class StringReplaceMany extends FunctionDefinition
{
    public StringReplaceMany() throws InternalException
    {
        super("text:replace many");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            ListEx replacements = arg(0, ListEx.class);
            @Value String whole = arg(1, String.class);
            String[] finds = new String[replacements.size()];
            String[] replaces = new String[finds.length];
            for (int i = 0; i < finds.length; i++)
            {
                Record record = Utility.cast(replacements.get(i), Record.class);
                finds[i] = Utility.cast(record.getField("find"), String.class);
                replaces[i] = Utility.cast(record.getField("replace"), String.class);
            }
            // Quite naive implementation, could be sped up:
            StringBuilder stringBuilder = new StringBuilder();
            int beginSegment = 0;
            int charIndex = 0;
            nextChar: while (charIndex < whole.length())
            {
                for (int findIndex = 0; findIndex < finds.length; findIndex++)
                {
                    String find = finds[findIndex];
                    if (whole.startsWith(find, charIndex))
                    {
                        if (beginSegment < charIndex)
                        {
                            stringBuilder.append(whole, beginSegment, charIndex);
                        }
                        stringBuilder.append(replaces[findIndex]);
                        charIndex += find.length();
                        beginSegment = charIndex;
                        continue nextChar;
                    }
                }
                charIndex++;
            }
            if (beginSegment == 0) // Replaced nothing, return original
                return whole;
            else
                return DataTypeUtility.value(stringBuilder.append(whole, beginSegment, charIndex).toString());
        }
    }
}
