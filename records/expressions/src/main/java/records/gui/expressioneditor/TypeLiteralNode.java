package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanExpression;
import javafx.scene.control.Label;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.TopLevelEditor.ErrorInfo;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.QuickFix;
import records.transformations.expression.Expression;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeSaver;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An Expression with a type expression inside.
 */
public class TypeLiteralNode extends TreeLiteralNode<Expression, ExpressionSaver>
{
    private final Consecutive<TypeExpression, TypeSaver> type;
    
    @SuppressWarnings("initialization")
    public TypeLiteralNode(ConsecutiveBase<Expression, ExpressionSaver> parent, @Nullable TypeExpression startingType)
    {
        super(parent);
        this.type = new Consecutive<TypeExpression, TypeSaver>(ConsecutiveBase.TYPE_OPS, this, "type{", "}", "", startingType == null ? null : startingType.loadAsConsecutive(BracketedStatus.TOP_LEVEL))
        {
            @Override
            public @Recorded TypeExpression save()
            {
                TypeSaver typeSaver = new TypeSaver(this);
                save(typeSaver);
                return typeSaver.finish(children.get(children.size() - 1));
            }

            @Override
            protected boolean hasImplicitRoundBrackets()
            {
                return false;
            }

            @Override
            public boolean isFocused()
            {
                return childIsFocused();
            }

            @Override
            public boolean showCompletionImmediately(@UnknownInitialization ConsecutiveChild<TypeExpression, TypeSaver> child)
            {
                // Even if the type is complete, they'll need to write the '}' to come back out, so show always:
                return true;
            }
        };
        updateNodes();
        updateListeners();
    }


    @Override
    protected EEDisplayNode getInnerDisplayNode()
    {
        return type;
    }

    @Override
    public void removeNestedBlanks()
    {
        type.removeBlanks();
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<Expression, ExpressionSaver>> quickFixes)
    {
    }

    @Override
    public ImmutableList<ErrorInfo> getErrors()
    {
        return ImmutableList.of();
    }
    
    /*
    @Override
    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.record(this, new TypeLiteralExpression(
            errorDisplayer.recordType(type, type.saveUnrecorded(errorDisplayer, onError))
        ));
    }
    */

    @Override
    public void setHoverDropLeft(boolean on)
    {

    }

    @Override
    public void focusChanged()
    {
        type.focusChanged();
    }

    @Override
    public @OnThread(Tag.FXPlatform) Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return type._test_getHeaders();
    }

    @Override
    public void save(ExpressionSaver saver)
    {
        //Log.debug("Saved as: " + type);
        //Log.debug("  From:\n      " + type.children.stream().map(c -> (c instanceof EntryNode) ? ((EntryNode)c).textField.getText() : "£" + c.getClass()).collect(Collectors.joining("\n      ")));
        
        saver.saveOperand(new TypeLiteralExpression(type.save()), this, this, c -> {});
    }

    @Override
    public void unmaskErrors()
    {
        type.unmaskErrors();
    }

    @Override
    public boolean isFocusPending()
    {
        return false;
    }

    @Override
    public void flushFocusRequest()
    {
        type.flushFocusRequest();
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        type.visitLocatable(visitor);
    }

    @Override
    public void clearAllErrors()
    {
        type.clearAllErrors();
    }

    @Override
    public void bindDisable(BooleanExpression disabledProperty)
    {
        type.bindDisable(disabledProperty);
    }
}
