package test.gen.backwards;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.UnitExpression;

public interface RequestBackwardsExpression
{
    public Expression make(DataType type, Object targetValue, int maxLevels) throws UserException, InternalException;

    public @Value Object makeValue(DataType t) throws UserException, InternalException;
    
    public DataType makeType() throws InternalException, UserException;
    
    public TypeManager getTypeManager();
    
    public UnitExpression makeUnitExpression(Unit unit);

    public @Value long genInt();
}
