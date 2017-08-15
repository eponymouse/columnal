package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformConsumer;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

// Like GeneralExpressionEntry but for units only
public class UnitEntry extends GeneralOperandEntry<UnitExpression, UnitNodeParent> implements OperandNode<UnitExpression>, ErrorDisplayer
{
    public UnitEntry(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, String initialContent)
    {
        super(UnitExpression.class, parent);
        textField.setText(initialContent);
        new AutoComplete(textField, UnitEntry::getSuggestions, new CompletionListener(), c -> !Character.isAlphabetic(c) && Character.getType(c) != Character.CURRENCY_SYMBOL  && (parent.operations.isOperatorAlphabet(c, parent.getThisAsSemanticParent()) || parent.terminatedByChars().contains(c)));
        updateNodes();
    }

    private static List<Completion> getSuggestions(String current, CompletionQuery completionQuery)
    {
        // TODO suggest units, and bracket
        return Collections.emptyList();
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(textField);
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public UnitExpression save(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        SingleUnitExpression singleUnitExpression = new SingleUnitExpression(textField.getText().trim());
        errorDisplayer.record(this, singleUnitExpression);
        return singleUnitExpression;
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child;
    }

    private class CompletionListener extends SimpleCompletionListener
    {
        @Override
        protected String selected(String currentText, AutoComplete.@Nullable Completion c, String rest)
        {
            if (rest.equals("}") || rest.equals(")"))
            {
                parent.parentFocusRightOfThis(Focus.LEFT);
            }
            else
            {
                parent.setOperatorToRight(UnitEntry.this, rest);
                parent.focusRightOf(UnitEntry.this, Focus.RIGHT);
            }
            return currentText;
        }
    }
}
