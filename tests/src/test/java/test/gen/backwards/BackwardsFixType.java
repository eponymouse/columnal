package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.TypeLiteralExpression;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;

import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsFixType extends BackwardsProvider
{
    public BackwardsFixType(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of();
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of(() -> {
            TypeManager m = parent.getTypeManager();
            return TypeLiteralExpression.fixType(m, FunctionList.getFunctionLookup(m.getUnitManager()), JellyType.fromConcrete(targetType), parent.make(targetType, targetValue, maxLevels - 1));
        });
    }
}
