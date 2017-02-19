package records.gui.expressioneditor;

import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
import utility.gui.FXUtility;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 19/12/2016.
 */
public class FunctionNode implements ExpressionParent, OperandNode
{
    private final TextField functionName;
    private final ConsecutiveBase arguments;
    private final ConsecutiveBase parent;
    private final FunctionDefinition function;

    @SuppressWarnings("initialization") // Because LeaveableTextField gets marked uninitialized
    public FunctionNode(FunctionDefinition function, ConsecutiveBase parent)
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
        VBox vBox = ExpressionEditorUtil.withLabelAbove(functionName, "function", "function", this);
        arguments = new ArgsConsecutive(vBox);

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
    public Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        // TODO keep track of whether function is known
        // TODO allow units (second optional consecutive)
        Expression argExp = arguments.toExpression(onError);
        return new CallExpression(functionName.getText(), Collections.emptyList(), argExp);
    }

    // Focuses the arguments because we are only shown after they've chosen the function
    public FunctionNode focusWhenShown()
    {
        arguments.focusWhenShown();
        return this;
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return new ReadOnlyObjectWrapper<@Nullable String>("function-inner");
    }

    @Override
    public ConsecutiveBase getParent()
    {
        return parent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        // TODO
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
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

    @Override
    public Stream<String> getParentStyles()
    {
        return parent.getParentStyles();
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return parent.getEditor();
    }

    private static final double aspectRatio = 0.2;
    public static final double BRACKET_WIDTH = 2.0;

    private static class OpenBracketOuter extends Path
    {
        @SuppressWarnings("initialization") // Need to annotate all the Shape API
        public OpenBracketOuter(DoubleExpression heightProperty)
        {
            getElements().addAll(
                new MoveTo(0, 0),
                new HLineTo() {{xProperty().bind(heightProperty.multiply(aspectRatio).subtract(BRACKET_WIDTH));}},
                new CubicCurveTo() {{
                    controlY2Property().bind(heightProperty);
                    yProperty().bind(heightProperty);
                    xProperty().bind(heightProperty.multiply(aspectRatio).subtract(BRACKET_WIDTH));
                }},
                new HLineTo(0),
                new VLineTo(0)
            );

            setStroke(null);
        }
    }

    private static class OpenBracketInner extends Path
    {
        @SuppressWarnings("initialization") // Need to annotate all the Shape API
        public OpenBracketInner(DoubleExpression heightProperty)
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
            setStroke(null);
        }
    }

    private static class OpenBracketShape extends VBox
    {
        public static final double TOP_GAP = 2.0;
        private final Node rhs;

        public OpenBracketShape()
        {
            Label topLabel = new Label(" ");
            topLabel.getStyleClass().add("function-top");
            topLabel.setVisible(false);
            TextField bottomField = new TextField("");
            bottomField.getStyleClass().add("entry-field");
            bottomField.setMinWidth(2.0);
            bottomField.setPrefWidth(2.0);
            bottomField.setVisible(false);


            Node lhs = new OpenBracketOuter(heightProperty().subtract(1.0));
            lhs.getStyleClass().add("function-bracket");
            rhs = new OpenBracketInner(heightProperty().subtract(TOP_GAP));

            getChildren().addAll(topLabel, bottomField, lhs, rhs);
            lhs.setManaged(false);
            rhs.setManaged(false);
            lhs.setLayoutX(0);
            lhs.setLayoutY(TOP_GAP);
            rhs.setLayoutX(BRACKET_WIDTH);
            rhs.setLayoutY(TOP_GAP);
        }

        @OnThread(Tag.FX)
        public void setInnerStyleBegin(@Nullable String style)
        {
            if (style == null)
                rhs.getStyleClass().clear();
            else
                rhs.getStyleClass().setAll(style);
        }

        @Override
        @OnThread(Tag.FX)
        protected double computePrefWidth(double height)
        {
            return (computePrefHeight(-1) - TOP_GAP) * aspectRatio + BRACKET_WIDTH;
        }
    }

    private class ArgsConsecutive extends Consecutive implements ChangeListener<@Nullable String>
    {
        private final OpenBracketShape openBracket;
        private final OpenBracketShape closeBracket;
        private @Nullable ObservableObjectValue<@Nullable String> innerOpenStyle;
        private @Nullable ObservableObjectValue<@Nullable String> innerCloseStyle;

        private ArgsConsecutive(VBox vBox, OpenBracketShape openBracket, OpenBracketShape closeBracket)
        {
            super(FunctionNode.this, new HBox(vBox, openBracket), closeBracket, "function");
            this.openBracket = openBracket;
            this.closeBracket = closeBracket;
            closeBracket.prefHeightProperty().bind(openBracket.heightProperty());
            updateDisplay();
        }

        public ArgsConsecutive(VBox vBox)
        {
            this(vBox, new OpenBracketShape(), new OpenBracketShape() {{setScaleX(-1);}});
        }

        @SuppressWarnings("initialization") // Because we use this as a listener
        @OnThread(Tag.FXPlatform)
        protected void updateDisplay(@UnknownInitialization(ConsecutiveBase.class) ArgsConsecutive this)
        {
            if (openBracket != null) // Can be in constructor
            {
                if (innerOpenStyle != null)
                    innerOpenStyle.removeListener(this);
                innerOpenStyle = operands.isEmpty() ? null : operands.get(0).getStyleWhenInner();
                if (innerOpenStyle != null)
                    innerOpenStyle.addListener(this);
                openBracket.setInnerStyleBegin(innerOpenStyle == null ? null : innerOpenStyle.get());
            }
            if (closeBracket != null) // Can be in constructor
            {
                if (innerCloseStyle != null)
                    innerCloseStyle.removeListener(this);
                innerCloseStyle = operands.isEmpty() ? null : operands.get(operands.size() - 1).getStyleWhenInner();
                if (innerCloseStyle != null)
                    innerCloseStyle.addListener(this);
                closeBracket.setInnerStyleBegin(innerCloseStyle == null ? null : innerCloseStyle.get());
            }
        }

        @Override
        @OnThread(Tag.FX)
        public void changed(ObservableValue<? extends @Nullable String> observable, @Nullable String oldValue, @Nullable String newValue)
        {
            if (observable == innerOpenStyle && openBracket != null)
                openBracket.setInnerStyleBegin(newValue);
            if (observable == innerCloseStyle && closeBracket != null)
                closeBracket.setInnerStyleBegin(newValue);
        }
    }

    @Override
    public Pair<ConsecutiveChild, Double> findClosestDrop(Point2D loc)
    {
        return Stream.of(new Pair<ConsecutiveChild, Double>(this, FXUtility.distanceToLeft(functionName, loc)), arguments.findClosestDrop(loc)).min(Comparator.comparing(Pair::getSecond)).get();
    }
}
