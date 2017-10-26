package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;

public class StringLeft extends FunctionDefinition
{
    public StringLeft()
    {
        super("left", "left.short");
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
            Object @Value [] params = Utility.cast(param, Object[].class);
            if (params.length != 2)
                throw new InternalException("Wrong number of params in left arguments: " + params.length);
            String src = Utility.cast(params[0], String.class);
            return src.substring(0, src.offsetByCodePoints(0, Utility.cast(params[1], Integer.class)));
        }
    }
}
