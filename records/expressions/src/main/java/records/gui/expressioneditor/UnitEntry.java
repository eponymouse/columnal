package records.gui.expressioneditor;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.SingleUnit;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.Completion.ShowStatus;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import utility.ExBiFunction;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

// Like GeneralExpressionEntry but for units only
public class UnitEntry extends GeneralOperandEntry<UnitExpression, UnitSaver> implements ErrorDisplayer<UnitExpression, UnitSaver>
{
    private final KeyShortcutCompletion bracketedCompletion = new KeyShortcutCompletion("autocomplete.brackets", '(');

    private final KeyShortcutCompletion endCompletion = new KeyShortcutCompletion("autocomplete.end", '}');

    /** Flag used to monitor when the initial content is set */
    private final SimpleBooleanProperty initialContentEntered = new SimpleBooleanProperty(false);

    UnitEntry(ConsecutiveBase<UnitExpression, UnitSaver> parent, String initialContent)
    {
        super(UnitExpression.class, parent);
        @SuppressWarnings("initialization") // Suppressing warning about the self method reference:
        ExBiFunction<String, CompletionQuery, Stream<Completion>> getSuggestions = this::getSuggestions;
        this.autoComplete = new AutoComplete<Completion>(textField, getSuggestions, new CompletionListener(), WhitespacePolicy.DISALLOW, UnitExpressionOps::differentAlphabet);
        updateNodes();
        textField.setText(initialContent);
    }

    private Stream<Completion> getSuggestions(String current, CompletionQuery completionQuery)
    {
        ArrayList<Completion> r = new ArrayList<>();
        r.add(bracketedCompletion);
        r.add(endCompletion);
        r.add(new NumericLiteralCompletion());
        for (SingleUnit unit : getUnitManager().getAllDeclared())
        {
            r.add(new KnownUnitCompletion(unit.getName()));
        }
        r.removeIf(c -> c.shouldShow(current) == ShowStatus.NO_MATCH);
        return r.stream();
    }

    private UnitManager getUnitManager()
    {
        return parent.getEditor().getTypeManager().getUnitManager();
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

    /*
    @Override
    public @Recorded UnitExpression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        String text = textField.getText().trim();

        if (text.isEmpty())
        {
            return errorDisplayer.recordUnit(this, new SingleUnitExpression(""));
        }

        try
        {
            int num = Integer.parseInt(text);
            UnitExpressionIntLiteral unitExpressionIntLiteral = new UnitExpressionIntLiteral(num);
            return errorDisplayer.recordUnit(this, unitExpressionIntLiteral);
        }
        catch (NumberFormatException e)
        {
            if (getUnitManager().isUnit(text))
            {
                SingleUnitExpression singleUnitExpression = new SingleUnitExpression(text);
                return errorDisplayer.recordUnit(this, singleUnitExpression);
            }
            else
            {
                return errorDisplayer.recordUnit(this, new SingleUnitExpression(text));
            }
        }
    }
    */

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child;
    }

    private static class NumericLiteralCompletion extends Completion
    {
        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(currentText, null);
        }

        @Override
        public ShowStatus shouldShow(String input)
        {
            // To allow "+" or "-", we add zero at the end before parsing:
            return Utility.parseIntegerOpt(input + "0").isPresent() ? ShowStatus.PHANTOM : ShowStatus.NO_MATCH;
        }

        @Override
        public boolean features(String curInput, int character)
        {
            String possible;
            if (curInput.isEmpty())
                possible = "0123456789+-_";
            else
            {
                if (curInput.contains("."))
                    possible = "0123456789_";
                else
                    possible = "0123456789._";
            }
            return Utility.containsCodepoint(possible, character);
        }
    }

    private class CompletionListener extends SimpleCompletionListener<Completion>
    {
        @Override
        protected @Nullable String selected(String currentText, AutoComplete.@Nullable Completion c, String rest)
        {
            @Nullable String newText = null;
            if (c == endCompletion)
            {
                parent.parentFocusRightOfThis(Focus.LEFT);
                return null;
            }
            
            /*
            if (c == bracketedCompletion)
            {
                UnitCompound bracketedExpression = new UnitCompound(parent, false, null);
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
            */
            else if (c != null && c instanceof KnownUnitCompletion)
            {
                newText = ((KnownUnitCompletion)c).completion;
            }

            boolean moveFocus = true;

            completing = true;
            // Must do this while completing so that we're not marked as blank:
            if (moveFocus)
            {
                if (rest.isEmpty())
                    parent.focusRightOf(UnitEntry.this, Focus.LEFT);
                else
                    parent.addOperandToRight(UnitEntry.this, rest, true);
            }
            return newText;
        }

        @Override
        public String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
        {
            return currentText;
        }

        @Override
        public void tabPressed()
        {
            parent.focusRightOf(UnitEntry.this, Focus.LEFT);
        }
    }
    
    private static class KnownUnitCompletion extends SimpleCompletion
    {
        public KnownUnitCompletion(String name)
        {
            super(name, null);
        }
    }
    
    public static enum UnitOp
    {
        MULTIPLY("*"), DIVIDE("/"), RAISE("^");
        
        private final String op;
        
        private UnitOp(String op)
        {
            this.op = op;
        }

        public String getContent()
        {
            return op;
        }
    }

    public static enum UnitBracket
    {
        OPEN_ROUND("("), CLOSE_ROUND(")");
        
        private final String bracket;

        private UnitBracket(String bracket)
        {
            this.bracket = bracket;
        }

        public String getContent()
        {
            return bracket;
        }
    }

    public static SingleLoader<UnitExpression, UnitSaver> load(String value)
    {
        return p -> new UnitEntry(p, value);
    }

    public static SingleLoader<UnitExpression, UnitSaver> load(UnitOp value)
    {
        return load(value.getContent());
    }

    @Override
    public void save(UnitSaver saver)
    {
        String text = textField.getText().trim();
        for (UnitOp unitOp : UnitOp.values())
        {
            if (unitOp.getContent().equals(text))
            {
                saver.saveOperator(unitOp, this, c -> {});
                return;
            }
        }

        for (UnitBracket unitBracket : UnitBracket.values())
        {
            if (unitBracket.getContent().equals(text))
            {
                saver.saveBracket(unitBracket, this, c -> {});
                return;
            }
        }
        
        OptionalInt num = Utility.parseIntegerOpt(text);
        if (num.isPresent())
            saver.saveOperand(new UnitExpressionIntLiteral(num.getAsInt()), this, this, c -> {});
        else
            saver.saveOperand(new SingleUnitExpression(text), this, this, c -> {});
    }
}
