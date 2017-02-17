package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.VLineTo;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.Expression;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

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
    private final FunctionDefinition function;

    @SuppressWarnings("initialization")
    public FunctionNode(FunctionDefinition function, ExpressionParent parent)
    {
        this.parent = parent;
        this.function = function;
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
        VBox vBox = ExpressionEditorUtil.withLabelAbove(functionName, "function", "function");
        arguments = new Consecutive(this, new HBox(vBox, new OpenBracketShape()), new Label(")"));

        Utility.addChangeListenerPlatformNN(functionName.textProperty(), text -> {
            parent.changed(this);
        });

        functionName.setText(function.getName());
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
    public OperandNode prompt(String prompt)
    {
        // Ignore
        return this;
    }

    @Override
    public @Nullable Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        // TODO keep track of whether function is known
        // TODO allow units (second optional consecutive)
        @Nullable Expression argExp = arguments.toExpression(onError);
        if (argExp != null)
            return new CallExpression(functionName.getText(), Collections.emptyList(), argExp);
        else
            return null;
    }

    // Focuses the arguments because we are only shown after they've chosen the function
    public FunctionNode focusWhenShown()
    {
        arguments.focusWhenShown();
        return this;
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
        return Utility.mapList(function.getLikelyArgTypes(getTypeManager().getUnitManager()), t -> new Pair<>(t, Collections.emptyList()));
    }

    @Override
    public List<Column> getAvailableColumns()
    {
        return parent.getAvailableColumns();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        return parent.getAvailableVariables(this);
    }

    @Override
    public TypeManager getTypeManager() throws InternalException
    {
        return parent.getTypeManager();
    }

    @Override
    public boolean isTopLevel()
    {
        return false;
    }

    @Override
    public void changed(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        // It's bound to be arguments asking us, nothing beyond that:
        parent.focusRightOf(this);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        functionName.requestFocus();
        functionName.positionCaret(functionName.getLength());
    }

    private static class OpenBracketClipLeft extends Path
    {
        private double aspectRatio = 0.2;
        @SuppressWarnings("initialization")
        public OpenBracketClipLeft(ReadOnlyDoubleProperty heightProperty)
        {
            getElements().addAll(
                new MoveTo(0, 0),
                new HLineTo() {{xProperty().bind(heightProperty.multiply(aspectRatio));}},
                new CubicCurveTo() {{
                    controlY2Property().bind(heightProperty);
                    yProperty().bind(heightProperty);
                    xProperty().bind(heightProperty.multiply(aspectRatio));
                }},
                new HLineTo(0),
                new VLineTo(0)
            );
            setFill(Color.BLACK);
            setStroke(null);
        }
    }

    private static class OpenBracketClipRight extends Path
    {
        private double aspectRatio = 0.2;
        @SuppressWarnings("initialization")
        public OpenBracketClipRight(ReadOnlyDoubleProperty heightProperty)
        {
            getElements().addAll(
                new MoveTo() {{xProperty().bind(heightProperty.multiply(aspectRatio));}},
                new CubicCurveTo() {{
                    controlY2Property().bind(heightProperty);
                    yProperty().bind(heightProperty);
                    xProperty().bind(heightProperty.multiply(aspectRatio));
                }},
                new VLineTo(0)
            );
            setFill(Color.BLACK);
            setStroke(null);
        }
    }

    @SuppressWarnings("initialization")
    private static class OpenBracketShape extends AnchorPane
    {
        public OpenBracketShape()
        {
            Label topLabel = new Label(" ");
            topLabel.getStyleClass().add("function-top");
            TextField bottomField = new TextField("");
            bottomField.setMinWidth(2.0);
            bottomField.setPrefWidth(2.0);
            bottomField.setVisible(false);
            VBox lhs = new VBox(topLabel, bottomField);
            lhs.getStyleClass().add("function");
            lhs.setClip(new OpenBracketClipLeft(lhs.heightProperty()));

            Label topLabelRHS = new Label(" ");
            topLabelRHS.getStyleClass().add("function-top");
            TextField bottomFieldRHS = new TextField("");
            bottomFieldRHS.setMinWidth(2.0);
            bottomFieldRHS.setPrefWidth(2.0);
            bottomFieldRHS.setVisible(false);
            VBox rhs = new VBox(topLabelRHS, bottomFieldRHS);
            rhs.getStyleClass().add("function");
            rhs.setClip(new OpenBracketClipRight(rhs.heightProperty()));
            getChildren().addAll(lhs, rhs);
            AnchorPane.setLeftAnchor(lhs, 0.0);
            AnchorPane.setRightAnchor(lhs, 3.0);
            AnchorPane.setRightAnchor(rhs, 0.0);
        }
    }
}
