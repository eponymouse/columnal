package records.errors;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import records.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.TypeState;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformSupplierInt;

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
        public final ColumnLookup columnLookup;
        public final FXPlatformSupplierInt<TypeState> makeTypeState;
        public final @Nullable DataType expectedType;

        protected EditableExpression(Expression current, @Nullable TableId srcTableId, ColumnLookup columnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType)
        {
            this.current = current;
            this.srcTableId = srcTableId;
            this.columnLookup = columnLookup;
            this.makeTypeState = makeTypeState;
            this.expectedType = expectedType;
        }

        @OnThread(Tag.Simulation)
        public abstract Table replaceExpression(Expression changed) throws InternalException;
    }
}
