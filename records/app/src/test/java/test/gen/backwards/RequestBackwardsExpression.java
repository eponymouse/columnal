package test.gen.backwards;

import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;

public interface RequestBackwardsExpression
{
    public Expression make(DataType type, Object targetValue, int maxLevels) throws UserException, InternalException;
    
    public TypeManager getTypeManager();
}
