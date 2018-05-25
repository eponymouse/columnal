package test.gen.backwards;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;

public interface RequestBackwardsExpression
{
    public Expression make(DataType type, Object targetValue, int maxLevels) throws UserException, InternalException;

    public @Value Object makeValue(DataType t) throws UserException, InternalException;
    
    public DataType makeType() throws InternalException, UserException;
    
    public TypeManager getTypeManager();
}
