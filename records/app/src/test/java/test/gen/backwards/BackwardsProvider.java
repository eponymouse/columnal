package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.Expression;
import test.DummyManager;
import utility.Pair;

import java.util.List;

public abstract class BackwardsProvider
{
    protected final SourceOfRandomness r;
    protected final RequestBackwardsExpression parent;

    public BackwardsProvider(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        this.r = r;
        this.parent = parent;
    }
    
    public abstract List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException;

    public abstract List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException;

    protected final CallExpression call(String name, Expression... args)
    {
        return new CallExpression(parent.getTypeManager().getUnitManager(), name, args);
    }

    protected Unit getUnit(String name) throws InternalException, UserException
    {
        UnitManager m = DummyManager.INSTANCE.getUnitManager();
        return m.loadUse(name);
    }
}
