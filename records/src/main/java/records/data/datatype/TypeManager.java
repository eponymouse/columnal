package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by neil on 21/12/2016.
 */
public class TypeManager
{
    private final HashMap<TypeId, DataType> knownTypes = new HashMap<>();

    // Either makes a new one, or fetches the existing one if it is the same type
    // or renames it to a spare name and returns that.
    public DataType registerTaggedType(String idealTypeName, List<TagType<DataType>> tagTypes) throws InternalException
    {
        TypeId idealTypeId = new TypeId(idealTypeName);
        if (knownTypes.containsKey(idealTypeId))
        {
            DataType existingType = knownTypes.get(idealTypeId);
            // Check if it's the same:
            if (tagTypes.equals(existingType.getTagTypes()))
            {
                // It is; all is well
                return existingType;
            }
            else
            {
                // Keep trying new names:
                return registerTaggedType(increaseNumber(idealTypeName), tagTypes);
            }
        }
        else
        {
            DataType newType = DataType.tagged(idealTypeId, tagTypes);
            knownTypes.put(idealTypeId, newType);
            return newType;
        }
    }

    private static String increaseNumber(String str)
    {
        if (str.length() <= 1)
            return str + "0";
        // Don't alter first char even if digit:
        int i;
        for (i = str.length() - 1; i > 0; i--)
        {
            if (str.charAt(i) < '0' || str.charAt(i) > '9')
            {
                i = i + 1;
                break;
            }
        }
        String numberPart = str.substring(i);
        BigInteger num = numberPart.isEmpty() ? BigInteger.ZERO : new BigInteger(numberPart);
        return str.substring(0, i) + num.add(BigInteger.ONE).toString();
    }

    public @Nullable DataType lookupType(String text)
    {
        return knownTypes.get(new TypeId(text));
    }

    public Map<TypeId, DataType> getKnownTypes()
    {
        return Collections.unmodifiableMap(knownTypes);
    }
}
