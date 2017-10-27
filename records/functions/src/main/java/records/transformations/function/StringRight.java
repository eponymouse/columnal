package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;

public class StringRight extends FunctionDefinition
{
    public StringRight()
    {
        super("right", "right.short");
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        return ImmutableList.of(new FunctionType(Instance::new, DataType.TEXT, DataType.tuple(DataType.TEXT, DataType.NUMBER), null));
    }

    private static class Instance extends FunctionInstance
    {

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            Object @Value [] params = Utility.castTuple(param, 2);
            String src = Utility.cast(params[0], String.class);
            int codePointCount = Utility.cast(params[1], Integer.class);
            if (codePointCount < 0)
                throw new UserException("Invalid count when calling right function: " + codePointCount);
            try
            {
                int totalCodepoints = src.codePointCount(0, src.length());
                return src.substring(src.offsetByCodePoints(0, Math.max(0, totalCodepoints - codePointCount)));
            }
            catch (IndexOutOfBoundsException e)
            {
                // Just return whole string:
                return src;
            }
        }
    }
}
