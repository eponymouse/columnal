package records.errors;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

public class ExpressionErrorException extends UserException
{
    public final EditableExpression editableExpression;

    public ExpressionErrorException(StyledString styledString, EditableExpression editableExpression)
    {
        super(styledString);
        this.editableExpression = editableExpression;
    }
    
    public static abstract class EditableExpression
    {
        public final Expression current;
        public final @Nullable TableId srcTableId;
        public final boolean perRow;
        public final @Nullable DataType expectedType;

        protected EditableExpression(Expression current, @Nullable TableId srcTableId, boolean perRow, @Nullable DataType expectedType)
        {
            this.current = current;
            this.srcTableId = srcTableId;
            this.perRow = perRow;
            this.expectedType = expectedType;
        }

        @OnThread(Tag.Simulation)
        public abstract Table replaceExpression(Expression changed) throws InternalException;
    }
}
