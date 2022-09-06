package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.Expression;
import records.transformations.function.FunctionList;
import test.DummyManager;
import xyz.columnal.utility.Pair;

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
        return new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), name, args);
    }

    protected Unit getUnit(String name) throws InternalException, UserException
    {
        UnitManager m = parent.getTypeManager().getUnitManager();
        return m.loadUse(name);
    }
}
