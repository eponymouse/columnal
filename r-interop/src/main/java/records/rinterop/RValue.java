package records.rinterop;

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

/**
 * An RValue is effectively an algebraic data type, best understood by looking at RVisitor.
 */
public interface RValue
{
    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException;
}
