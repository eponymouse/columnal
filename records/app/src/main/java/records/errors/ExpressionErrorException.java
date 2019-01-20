package records.errors;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditor.ColumnAvailability;
import records.transformations.expression.Expression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.function.Function;

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
        public final Function<ColumnId, ColumnAvailability> groupedColumns;
        public final @Nullable DataType expectedType;

        protected EditableExpression(Expression current, @Nullable TableId srcTableId, Function<ColumnId, ColumnAvailability> groupedColumns, @Nullable DataType expectedType)
        {
            this.current = current;
            this.srcTableId = srcTableId;
            this.groupedColumns = groupedColumns;
            this.expectedType = expectedType;
        }

        @OnThread(Tag.Simulation)
        public abstract Table replaceExpression(Expression changed) throws InternalException;
    }
}
