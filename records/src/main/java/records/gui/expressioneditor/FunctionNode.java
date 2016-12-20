package records.gui.expressioneditor;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.transformations.expression.CallExpression;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 19/12/2016.
 */
public class FunctionNode implements ExpressionParent, OperandNode
{
    private final TextField functionName;
    private final Consecutive arguments;
    private final ExpressionParent parent;

    @SuppressWarnings("initialization")
    public FunctionNode(String funcName, ExpressionParent parent)
    {
        this.parent = parent;
        this.functionName = new LeaveableTextField(this, parent) {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void forward()
            {
                if (getCaretPosition() == getLength())
                    arguments.focus(Focus.LEFT);
                else
                    super.forward();
            }
        };
        functionName.getStyleClass().add("function-name");
        Label typeLabel = new Label("function");
        typeLabel.getStyleClass().add("function-top");
        VBox vBox = new VBox(typeLabel, functionName);
        vBox.getStyleClass().add("function");
        arguments = new Consecutive(this, new HBox(vBox, new Label("(")), new Label(")"));

        functionName.setText(funcName);
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return arguments.nodes();
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
        {
            functionName.requestFocus();
            functionName.positionCaret(0);
        }
        else
            arguments.focus(side);
    }

    @Override
    public @Nullable DataType inferType()
    {
        return null;
    }

    @Override
    public ExpressionNode prompt(String prompt)
    {
        // Ignore
        return this;
    }

    @Override
    public @Nullable Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        // TODO keep track of whether function is known
        // TODO allow units (second optional consecutive)
        @Nullable List<Expression> argExps = arguments.toArgs();
        if (argExps != null)
            return new CallExpression(functionName.getText(), Collections.emptyList(), argExps);
        else
            return null;
    }

    // Focuses the arguments because we are only shown after they've chosen the function
    public FunctionNode focusWhenShown()
    {
        arguments.focusWhenShown();
        return this;
    }

    @Override
    public @Nullable DataType getType(ExpressionNode child)
    {
        // Not valid for multiple args anyway
        return null;
    }

    @Override
    public List<ColumnId> getAvailableColumns()
    {
        return parent.getAvailableColumns();
    }

    @Override
    public List<String> getAvailableVariables(ExpressionNode child)
    {
        return parent.getAvailableVariables(this);
    }

    @Override
    public boolean isTopLevel()
    {
        return false;
    }

    @Override
    public void focusRightOf(ExpressionNode child)
    {
        // It's bound to be arguments asking us, nothing beyond that:
        parent.focusRightOf(this);
    }

    @Override
    public void focusLeftOf(ExpressionNode child)
    {
        functionName.requestFocus();
        functionName.positionCaret(functionName.getLength());
    }
}
