package records.rinterop;

import records.error.InternalException;
import records.error.UserException;

/**
 * An RValue is effectively an algebraic data type, best understood by looking at RVisitor.
 */
public interface RValue
{
    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException;
}
