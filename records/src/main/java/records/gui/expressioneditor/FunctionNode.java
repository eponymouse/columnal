package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableObjectValue;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.Expression;
import records.transformations.function.FunctionDefinition;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 19/12/2016.
 */
public class FunctionNode extends SurroundNode
{
    private final FunctionDefinition function;

    @SuppressWarnings("initialization") // Because LeaveableTextField gets marked uninitialized
    public FunctionNode(FunctionDefinition function, @Nullable Expression argumentsExpression, ConsecutiveBase parent)
    {
        super(parent, "function", "function", function.getName(), true, argumentsExpression);
        this.function = function;
    }

    @Override
    public @Nullable DataType inferType()
    {
        return null;
    }

    @Override
    public OperandNode prompt(String prompt)
    {
        // Ignore
        return this;
    }

    @SuppressWarnings("nullness") // contents is known to be non-null.
    @Override
    public Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        // TODO keep track of whether function is known
        // TODO allow units (second optional consecutive)
        Expression argExp = contents.toExpression(onError);
        return new CallExpression(head.getText(), Collections.emptyList(), argExp);
    }

    //@Override
    //public @Nullable DataType getType(ExpressionNode child)
    //{
        // Not valid for multiple args anyway
        //return null;
    //}

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(ExpressionNode child) throws InternalException, UserException
    {
        return Utility.mapList(function.getLikelyArgTypes(getEditor().getTypeManager().getUnitManager()), t -> new Pair<>(t, Collections.emptyList()));
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        return parent.getAvailableVariables(this);
    }
}
