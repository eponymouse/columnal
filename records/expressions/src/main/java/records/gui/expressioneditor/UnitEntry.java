package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.SingleUnit;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnfinishedUnitExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// Like GeneralExpressionEntry but for units only
public class UnitEntry extends GeneralOperandEntry<UnitExpression, UnitNodeParent> implements OperandNode<UnitExpression>, ErrorDisplayer
{
    private static final KeyShortcutCompletion bracketedCompletion = new KeyShortcutCompletion("Bracketed", '(');

    /** Flag used to monitor when the initial content is set */
    private final SimpleBooleanProperty initialContentEntered = new SimpleBooleanProperty(false);


    public UnitEntry(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, String initialContent, boolean userEntered)
    {
        super(UnitExpression.class, parent);
        if (!userEntered)
        {
            textField.setText(initialContent); // Do before auto complete is on the field
            initialContentEntered.set(true);
        }
        @SuppressWarnings("initialization") // Dummy variable to allow suppressing warning about the self method reference:
        AutoComplete dummy = new AutoComplete(textField, this::getSuggestions, new CompletionListener(), c -> !Character.isAlphabetic(c) && Character.getType(c) != Character.CURRENCY_SYMBOL  && (parent.operations.isOperatorAlphabet(c, parent.getThisAsSemanticParent()) || parent.terminatedByChars().contains(c)));
        updateNodes();

        if (userEntered)
        {
            // Do this after auto-complete is set up and we are set as part of parent,
            // in case it finishes a completion:
            FXUtility.runAfter(() -> {
                textField.setText(initialContent);
                initialContentEntered.set(true);
            });
        }
    }

    private List<Completion> getSuggestions(String current, CompletionQuery completionQuery)
    {
        List<Completion> r = new ArrayList<>();
        r.add(bracketedCompletion);
        r.add(new NumericLiteralCompletion());
        for (SingleUnit unit : parent.getThisAsSemanticParent().getUnitManager().getAllDeclared())
        {
            r.add(new KnownUnitCompletion(unit.getName()));
        }
        r.removeIf(c -> !c.shouldShow(current));
        return r;
    }

    @Override
    public void focus(Focus side)
    {
        super.focus(side);
        FXUtility.onceTrue(initialContentEntered, () -> {
            // Only if we haven't lost focus in the mean time, adjust ours:
            if (isFocused())
                super.focus(side);
        });
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
        String text = textField.getText().trim();

        if (text.isEmpty())
        {
            return new UnfinishedUnitExpression("");
        }

        try
        {
            int num = Integer.parseInt(text);
            UnitExpressionIntLiteral unitExpressionIntLiteral = new UnitExpressionIntLiteral(num);
            errorDisplayer.record(this, unitExpressionIntLiteral);
            return unitExpressionIntLiteral;
        }
        catch (NumberFormatException e)
        {
            if (parent.getThisAsSemanticParent().getUnitManager().isUnit(text))
            {
                SingleUnitExpression singleUnitExpression = new SingleUnitExpression(text);
                errorDisplayer.record(this, singleUnitExpression);
                return singleUnitExpression;
            }
            else
            {
                return new UnfinishedUnitExpression(text);
            }
        }
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child;
    }

    private static class NumericLiteralCompletion extends Completion
    {
        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, currentText);
        }

        @Override
        public boolean shouldShow(String input)
        {
            // To allow "+" or "-", we add zero at the end before parsing:
            return Utility.parseIntegerOpt(input + "0").isPresent();
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return onlyAvailableCompletion ? CompletionAction.SELECT : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            if (curInput.isEmpty())
                return "0123456789+-_".contains("" + character);
            else
            {
                if (curInput.contains("."))
                    return "0123456789_".contains("" + character);
                else
                    return "0123456789._".contains("" + character);
            }
        }
    }

    private class CompletionListener extends SimpleCompletionListener
    {
        @Override
        protected String selected(String currentText, AutoComplete.@Nullable Completion c, String rest)
        {
            if (c == bracketedCompletion)
            {
                UnitCompound bracketedExpression = new UnitCompound(parent, false);
                bracketedExpression.focusWhenShown();
                parent.replace(UnitEntry.this, bracketedExpression);
            }
            else if (rest.equals("}") || rest.equals(")"))
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

        @Override
        public String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
        {
            return currentText;
        }
    }

    private static class KnownUnitCompletion extends Completion
    {
        private final String name;

        public KnownUnitCompletion(String name)
        {
            this.name = name;
        }

        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(name));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return name.toLowerCase().startsWith(input.toLowerCase());
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return name.equals(input) ? (onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT) : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return name.contains("" + character);
        }
    }
}
