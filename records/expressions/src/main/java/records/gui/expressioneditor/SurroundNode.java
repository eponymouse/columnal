package records.gui.expressioneditor;

import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
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
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * A common parent class for items which have a outer "head" text field at the very beginning,
 * followed by some inner Consecutive with arguments.  This includes things like function calls
 * (head is function name), tag expressions (head is tag name).
 *
 */
public abstract class SurroundNode implements EEDisplayNodeParent, OperandNode<Expression, ExpressionNodeParent>, ErrorDisplayer<Expression>, ExpressionNodeParent
{
    public static final double BRACKET_WIDTH = 0.0;
    private static final double aspectRatio = 0.2;
    protected final TextField head;
    protected final @Nullable ConsecutiveBase<Expression, ExpressionNodeParent> contents;
    protected final ConsecutiveBase<Expression, ExpressionNodeParent> parent;
    /**
     * The semantic parent which can be asked about available variables, etc
     */
    protected final ExpressionNodeParent semanticParent;
    private final String cssClass;
    // Only used if contents is null.  We don't make this nullable if contents is present,
    // mainly because it makes all the nullness checks a pain.
    private final ObservableList<Node> noInnerNodes;
    private final ErrorDisplayer<Expression> showError;

    @SuppressWarnings("initialization")
    public SurroundNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent, String cssClass, @Localized String headLabel, String startingHead, boolean hasInner, @Nullable Expression startingContent)
    {
        this.parent = parent;
        this.semanticParent = semanticParent;
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
                        parent.focusRightOf(SurroundNode.this, Focus.LEFT);
                }
                else
                    super.forward();
            }
        };
        head.setText(startingHead);
        this.cssClass = cssClass;
        Pair<VBox, ErrorDisplayer<Expression>> vBoxAndErrorShow = ExpressionEditorUtil.withLabelAbove(head, this.cssClass, headLabel, this, getEditor(), e -> getParent().replaceLoad(this, e), getParentStyles());
        VBox vBox = vBoxAndErrorShow.getFirst();
        this.showError = vBoxAndErrorShow.getSecond();
        noInnerNodes = FXCollections.observableArrayList();
        if (hasInner)
        {
            contents = new ContentConsecutive(vBox, startingContent) {
                @Override
                @OnThread(Tag.FXPlatform)
                protected BracketedStatus getChildrenBracketedStatus()
                {
                    return BracketedStatus.DIRECT_ROUND_BRACKETED;
                }
            };
        }
        else
        {
            contents = null;
            noInnerNodes.setAll(vBox);
        }

        FXUtility.addChangeListenerPlatformNN(head.textProperty(), text -> {
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
    @Override
    public void focusWhenShown()
    {
        if (contents != null)
        {
            contents.focusWhenShown();
        }
        else
        {
            head.requestFocus();
        }
    }

    @Override
    public ConsecutiveBase<Expression, ExpressionNodeParent> getParent()
    {
        return parent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        if (contents != null)
            contents.setSelected(selected);
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
    }

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child, Focus side)
    {
        // It's bound to be arguments asking us, nothing beyond that:
        parent.focusRightOf(this, side);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
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
    public <C extends LoadableExpression<C, ?>> @Nullable Pair<ConsecutiveChild<? extends C, ?>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        Stream<Pair<ConsecutiveChild<? extends C, ?>, Double>> stream = Utility.streamNullable(ConsecutiveChild.closestDropSingle(this, Expression.class, head, loc, forType));
        if (contents != null)
            return Stream.<Pair<ConsecutiveChild<? extends C, ?>, Double>>concat(stream, Utility.<Pair<ConsecutiveChild<? extends C, ?>, Double>>streamNullable(contents.findClosestDrop(loc, forType))).min(Comparator.comparing(p -> p.getSecond())).orElse(null);
        else
            return stream.findFirst().orElse(null);
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
        public static final double TOP_GAP = 0.0;
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


            Node lhs = new OpenBracketOuter(heightProperty());
            lhs.getStyleClass().add(cssClass + "-bracket");
            rhs = new OpenBracketInner(heightProperty().subtract(TOP_GAP));

            getChildren().addAll(topLabel, bottomField, lhs, rhs);
            lhs.setManaged(false);
            rhs.setManaged(false);
            lhs.setLayoutX(0);
            lhs.setLayoutY(TOP_GAP);
            rhs.setLayoutX(BRACKET_WIDTH);
            rhs.setLayoutY(TOP_GAP);

            setCursor(Cursor.TEXT);
            setOnMouseClicked(e -> {
                if (contents != null)
                    contents.focusBlankAtLeft();
            });
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

    protected class ContentConsecutive extends Consecutive<Expression, ExpressionNodeParent> implements ChangeListener<@Nullable String>
    {
        private final OpenBracketShape openBracket;
        private final OpenBracketShape closeBracket;
        private @Nullable ObservableObjectValue<@Nullable String> innerOpenStyle;
        private @Nullable ObservableObjectValue<@Nullable String> innerCloseStyle;

        private ContentConsecutive(VBox vBox, OpenBracketShape openBracket, OpenBracketShape closeBracket, @Nullable Expression args)
        {
            super(ConsecutiveBase.EXPRESSION_OPS, SurroundNode.this, new HBox(vBox, openBracket), closeBracket, cssClass, args == null ? null : SingleLoader.withSemanticParent(args.loadAsConsecutive(true), SurroundNode.this), ')');
            this.openBracket = openBracket;
            this.closeBracket = closeBracket;
            closeBracket.prefHeightProperty().bind(openBracket.heightProperty());
            updateDisplay();
        }

        public ContentConsecutive(VBox vBox, @Nullable Expression args)
        {
            this(vBox, new OpenBracketShape(), new OpenBracketShape() {{setScaleX(-1);}}, args);
        }

        @OnThread(Tag.FXPlatform)
        @Override
        protected ExpressionNodeParent getThisAsSemanticParent()
        {
            return SurroundNode.this;
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
        @SuppressWarnings("interned")
        @OnThread(Tag.FX)
        public void changed(ObservableValue<? extends @Nullable String> observable, @Nullable String oldValue, @Nullable String newValue)
        {
            if (observable == innerOpenStyle && openBracket != null)
                openBracket.setInnerStyleBegin(newValue);
            if (observable == innerCloseStyle && closeBracket != null)
                closeBracket.setInnerStyleBegin(newValue);
        }

        @OnThread(Tag.FXPlatform)
        @Override
        public boolean isFocused()
        {
            return childIsFocused();
        }

        @Override
        @OnThread(Tag.FXPlatform)
        protected boolean hasImplicitRoundBrackets()
        {
            return true;
        }
    }

    @Override
    public boolean isFocused()
    {
        return head.isFocused() || (contents != null && contents.childIsFocused());
    }

    @Override
    public void focusChanged()
    {
        if (contents != null)
            contents.focusChanged();
    }

    @Override
    public void showError(StyledString error, List<ErrorAndTypeRecorder.QuickFix<Expression>> quickFixes)
    {
        showError.showError(error, quickFixes);
    }

    @Override
    public boolean isShowingError()
    {
        return showError.isShowingError();
    }

    @Override
    public void showType(String type)
    {
        showError.showType(type);
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child || (contents != null && contents.isOrContains(child));
    }
}
