package records.gui.expressioneditor;

import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
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
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 * A common parent class for items which have a outer "head" text field at the very beginning,
 * followed by some inner Consecutive with arguments.  This includes things like function calls
 * (head is function name), tag expressions (head is tag name).
 *
 */
public abstract class SurroundNode implements ExpressionParent, OperandNode
{
    public static final double BRACKET_WIDTH = 2.0;
    private static final double aspectRatio = 0.2;
    protected final TextField head;
    protected final @Nullable ConsecutiveBase contents;
    protected final ConsecutiveBase parent;
    private final String cssClass;
    // Only used if contents is null.  We don't make this nullable if contents is present,
    // mainly because it makes all the nullness checks a pain.
    private final ObservableList<Node> noInnerNodes;

    @SuppressWarnings("initialization")
    public SurroundNode(ConsecutiveBase parent, String cssClass, @Localized String headLabel, String startingHead, boolean hasInner, @Nullable Expression startingContent)
    {
        this.parent = parent;
        this.head = new LeaveableTextField(this, parent) {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void forward()
            {
                if (getCaretPosition() == getLength())
                {
                    if (contents != null)
                        contents.focus(Focus.LEFT);
                    else
                        parent.focusRightOf(SurroundNode.this);
                }
                else
                    super.forward();
            }
        };
        head.setText(startingHead);
        this.cssClass = cssClass;
        VBox vBox = ExpressionEditorUtil.withLabelAbove(head, this.cssClass, headLabel, this);
        noInnerNodes = FXCollections.observableArrayList();
        if (hasInner)
            contents = new ContentConsecutive(vBox, startingContent);
        else
            contents = null;

        Utility.addChangeListenerPlatformNN(head.textProperty(), text -> {
            parent.changed(this);
        });
    }

    @Override
    public ObservableList<Node> nodes()
    {
        if (contents != null)
            return contents.nodes();
        else
            return noInnerNodes;
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
        {
            head.requestFocus();
            head.positionCaret(0);
        }
        else
        {
            if (contents != null)
                contents.focus(side);
            else
            {
                head.requestFocus();
                head.positionCaret(head.getText().length());
            }
        }
    }

    // Focuses the arguments because we are only shown after they've chosen the function
    public SurroundNode focusWhenShown()
    {
        if (contents != null)
        {
            contents.focusWhenShown();
        }
        else
        {
            head.requestFocus();
        }
        return this;
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
        head.requestFocus();
        head.positionCaret(head.getLength());
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

    @Override
    public Pair<ConsecutiveChild, Double> findClosestDrop(Point2D loc)
    {
        Pair<ConsecutiveChild, Double> headDist = new Pair<>(this, FXUtility.distanceToLeft(head, loc));
        if (contents != null)
            return Stream.of(headDist, contents.findClosestDrop(loc)).min(Comparator.comparing(Pair::getSecond)).get();
        else
            return headDist;
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return new ReadOnlyObjectWrapper<@Nullable String>(cssClass + "-inner");
    }

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

    private class OpenBracketShape extends VBox
    {
        public static final double TOP_GAP = 2.0;
        private final Node rhs;

        public OpenBracketShape()
        {
            Label topLabel = new Label(" ");
            topLabel.getStyleClass().add(cssClass + "-top");
            topLabel.setVisible(false);
            TextField bottomField = new TextField("");
            bottomField.getStyleClass().add("entry-field");
            bottomField.setMinWidth(2.0);
            bottomField.setPrefWidth(2.0);
            bottomField.setVisible(false);


            Node lhs = new OpenBracketOuter(heightProperty().subtract(1.0));
            lhs.getStyleClass().add(cssClass + "-bracket");
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

    protected class ContentConsecutive extends Consecutive implements ChangeListener<@Nullable String>
    {
        private final OpenBracketShape openBracket;
        private final OpenBracketShape closeBracket;
        private @Nullable ObservableObjectValue<@Nullable String> innerOpenStyle;
        private @Nullable ObservableObjectValue<@Nullable String> innerCloseStyle;

        private ContentConsecutive(VBox vBox, OpenBracketShape openBracket, OpenBracketShape closeBracket, @Nullable Expression args)
        {
            super(SurroundNode.this, new HBox(vBox, openBracket), closeBracket, cssClass, args == null ? null : args.loadAsConsecutive());
            this.openBracket = openBracket;
            this.closeBracket = closeBracket;
            closeBracket.prefHeightProperty().bind(openBracket.heightProperty());
            updateDisplay();
        }

        public ContentConsecutive(VBox vBox, @Nullable Expression args)
        {
            this(vBox, new OpenBracketShape(), new OpenBracketShape() {{setScaleX(-1);}}, args);
        }

        @SuppressWarnings("initialization") // Because we use this as a listener
        @OnThread(Tag.FXPlatform)
        protected void updateDisplay(@UnknownInitialization(ConsecutiveBase.class)SurroundNode.ContentConsecutive this)
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
}
