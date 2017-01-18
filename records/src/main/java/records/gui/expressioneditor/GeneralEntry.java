package records.gui.expressioneditor;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.BooleanLiteralContext;
import records.grammar.ExpressionParser.NumericLiteralContext;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StringLiteral;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Anything which fits in a normal text field without special prefix:
 *   - Numeric literal
 *   - Boolean literal
 *   - Function name (until later transformed to function call)
 *   - Variable reference.
 */
public class GeneralEntry extends LeafNode implements OperandNode
{
    private final VBox container;
    private final @Interned KeyShortcutCompletion bracketCompletion;
    private final @Interned KeyShortcutCompletion stringCompletion;
    private final @Interned KeyShortcutCompletion patternMatchCompletion;
    private boolean completing; // Set to true while updating field with auto completion

    public static enum Status
    {
        COLUMN_REFERENCE, TAG, LITERAL /*number or bool, not string */, VARIABLE, FUNCTION, UNFINISHED;
    }

    private final TextField textField;
    private final Label typeLabel;
    private ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.UNFINISHED);
    private final ObservableList<Node> nodes;
    private final AutoComplete autoComplete;

    @SuppressWarnings("initialization")
    public GeneralEntry(String content, Consecutive parent)
    {
        super(parent);
        bracketCompletion = new @Interned KeyShortcutCompletion("Bracketed expressions", '(');
        stringCompletion = new @Interned KeyShortcutCompletion("Text", '\"');
        patternMatchCompletion = new @Interned KeyShortcutCompletion("Pattern match", '?');
        this.textField = new LeaveableTextField(this, parent);
        textField.setText(content);
        textField.getStyleClass().add("entry-field");
        Utility.sizeToFit(textField, null, null);
        typeLabel = new Label();
        typeLabel.getStyleClass().add("entry-type");
        container = new VBox(typeLabel, textField) {
            @Override
            @OnThread(Tag.FX)
            public double getBaselineOffset()
            {
                return typeLabel.getHeight() + getSpacing() + textField.getBaselineOffset();
            }
        };
        container.getStyleClass().add("entry");
        this.nodes = FXCollections.observableArrayList(container);
        this.autoComplete = new AutoComplete(textField, this::getSuggestions, new SimpleCompletionListener()
        {
            @Override
            protected String selected(String currentText, Completion c, String rest)
            {
                if (c instanceof KeyShortcutCompletion)
                {
                    @Interned KeyShortcutCompletion ksc = (@Interned KeyShortcutCompletion) c;
                    if (ksc == bracketCompletion)
                        parent.replace(GeneralEntry.this, new Bracketed(Collections.<Function<Consecutive, OperandNode>>singletonList(e -> new GeneralEntry("", e).focusWhenShown()), parent, new Label("("), new Label(")")));
                    else if (ksc == stringCompletion)
                        parent.replace(GeneralEntry.this, new StringLiteralNode(parent).focusWhenShown());
                    else if (ksc == patternMatchCompletion)
                        parent.replace(GeneralEntry.this, new PatternMatchNode(parent).focusWhenShown());
                }
                else if (c instanceof FunctionCompletion) // Must come before general due to inheritance
                {
                    // What to do with rest != "" here? Don't allow? Skip to after args?
                    FunctionCompletion fc = (FunctionCompletion)c;
                    parent.replace(GeneralEntry.this, new FunctionNode(fc.function.getName(), parent).focusWhenShown());
                }
                else if (c instanceof GeneralCompletion)
                {
                    GeneralCompletion gc = (GeneralCompletion) c;
                    completing = true;
                    parent.setOperatorToRight(GeneralEntry.this, rest);
                    status.setValue(gc.getType());
                    if (gc instanceof FunctionCompletion)
                        return ((FunctionCompletion)gc).getCompletedText();
                    else if (gc instanceof SimpleCompletion)
                        return ((SimpleCompletion)gc).getCompletedText();
                    else // Numeric literal:
                        return currentText;
                }
                else
                    Utility.logStackTrace("Unsupported completion: " + c.getClass());
                return textField.getText();
            }
        }, OperatorEntry::isOperatorAlphabet);

        Utility.addChangeListenerPlatformNN(status, s -> {
            for (Status possibleStatus : Status.values())
            {
                container.pseudoClassStateChanged(getPseudoClass(possibleStatus), possibleStatus == s);
            }
            typeLabel.setText(getTypeLabel(s));
        });
        Utility.addChangeListenerPlatformNN(textField.textProperty(), t -> {
            if (!completing)
            {
                status.set(Status.UNFINISHED);
            }
            else
                completing = false;
            textField.pseudoClassStateChanged(PseudoClass.getPseudoClass("ps-empty"), t.isEmpty());
            parent.changed(GeneralEntry.this);
        });
        textField.pseudoClassStateChanged(PseudoClass.getPseudoClass("ps-empty"), textField.getText().isEmpty());
    }

    private String getTypeLabel(Status s)
    {
        switch (s)
        {
            case COLUMN_REFERENCE:
                return "column";
            case TAG:
                return "tag";
            case LITERAL:
                return "";
            case VARIABLE:
                return "variable";
            case FUNCTION:
                return "call";
            case UNFINISHED:
                return "error";
        }
        return "";
    }

    private PseudoClass getPseudoClass(Status s)
    {
        String pseudoClass;
        switch (s)
        {
            case COLUMN_REFERENCE:
                pseudoClass = "ps-column";
                break;
            case TAG:
                pseudoClass = "ps-tag";
                break;
            case LITERAL:
                pseudoClass = "ps-literal";
                break;
            case VARIABLE:
                pseudoClass = "ps-variable";
                break;
            case FUNCTION:
                pseudoClass = "ps-function";
                break;
            default:
                pseudoClass = "ps-unfinished";
                break;
        }
        return PseudoClass.getPseudoClass(pseudoClass);
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    private List<Completion> getSuggestions(String text) throws UserException, InternalException
    {
        ArrayList<Completion> r = new ArrayList<>();
        r.add(bracketCompletion);
        r.add(stringCompletion);
        r.add(patternMatchCompletion);
        r.add(new NumericLiteralCompletion());
        addAllFunctions(r);
        r.add(new SimpleCompletion("true", Status.LITERAL));
        r.add(new SimpleCompletion("false", Status.LITERAL));
        for (ColumnId columnId : parent.getAvailableColumns())
        {
            r.add(new SimpleCompletion(columnId.getRaw(), Status.COLUMN_REFERENCE));
        }
        // TODO: use type to prioritise, but not to filter (might always be followed by
        //   ? to do pattern match)
        /*
        @Nullable DataType t = parent.getType(this);
        if (t != null)
        {
            t.apply(new DataTypeVisitor<UnitType>()
            {

                @Override
                public UnitType number(NumberInfo displayInfo) throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }

                @Override
                public UnitType text() throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }

                @Override
                public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }

                @Override
                public UnitType bool() throws InternalException, UserException
                {
                    // It's common they might want to follow with <, =, etc, so
                    // we show all completions:

                    return UnitType.UNIT;
                }

                @Override
                public UnitType tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }
            });
        }
        */
        r.removeIf(c -> !c.shouldShow(text));
        return r;
    }

    private void addAllFunctions(ArrayList<Completion> r)
    {
        for (FunctionDefinition function : FunctionList.FUNCTIONS)
        {
            r.add(new FunctionCompletion(function));
        }
    }

    public GeneralEntry focusWhenShown()
    {
        Utility.onNonNull(textField.sceneProperty(), scene -> focus(Focus.RIGHT));
        return this;
    }

    private static abstract class GeneralCompletion extends Completion
    {
        abstract Status getType();
    }

    private static class SimpleCompletion extends GeneralCompletion
    {
        private final String text;
        private final Status type;

        public SimpleCompletion(String text, Status type)
        {
            this.text = text;
            this.type = type;
        }

        @Override
        Pair<@Nullable Node, String> getDisplay(String currentText)
        {
            return new Pair<>(null, text);
        }

        @Override
        boolean shouldShow(String input)
        {
            return text.startsWith(input);
        }

        String getCompletedText()
        {
            return text;
        }

        Status getType()
        {
            return type;
        }
    }


    public static class FunctionCompletion extends GeneralCompletion
    {
        private final FunctionDefinition function;

        public FunctionCompletion(FunctionDefinition function)
        {
            this.function = function;
        }

        @Override
        Pair<@Nullable Node, String> getDisplay(String currentText)
        {
            return new Pair<>(null, function.getName());
        }

        @Override
        boolean shouldShow(String input)
        {
            return function.getName().startsWith(input);
        }

        String getCompletedText()
        {
            return function.getName();
        }

        @Override
        Status getType()
        {
            return Status.FUNCTION;
        }
    }

    private static class NumericLiteralCompletion extends GeneralCompletion
    {
        @Override
        Pair<@Nullable Node, String> getDisplay(String currentText)
        {
            return new Pair<>(null, currentText.trim());
        }

        @Override
        boolean shouldShow(String input)
        {
            try
            {
                return Utility.parseAsOne(input, ExpressionLexer::new, ExpressionParser::new, p -> p.numericLiteral())
                    != null;
            }
            catch (InternalException | UserException e)
            {
                return false;
            }
        }

        @Override
        Status getType()
        {
            return Status.LITERAL;
        }
    }

    @Override
    public void focus(Focus side)
    {
        textField.requestFocus();
        textField.positionCaret(side == Focus.LEFT ? 0 : textField.getLength());
    }

    @Override
    public @Nullable DataType inferType()
    {
        return null;
    }

    @Override
    public ExpressionNode prompt(String prompt)
    {
        textField.setPromptText(prompt);
        return this;
    }

    @Override
    public @Nullable Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        if (status.get() == Status.COLUMN_REFERENCE)
        {
            return new ColumnReference(new ColumnId(textField.getText()), ColumnReferenceType.CORRESPONDING_ROW);
        }
        else if (status.get() == Status.LITERAL)
        {
            NumericLiteralContext number = parseOrNull(ExpressionParser::numericLiteral);
            if (number != null)
            {
                //TODO support units
                return new NumericLiteral(Utility.parseNumber(number.getText()), null);
            }
            BooleanLiteralContext bool = parseOrNull(ExpressionParser::booleanLiteral);
            if (bool != null)
            {
                return new BooleanLiteral(bool.getText().equals("true"));
            }
        }
        // Unfinished:
        return null;
    }

    private <T> @Nullable T parseOrNull(ExFunction<ExpressionParser, T> parse)
    {
        try
        {
            return Utility.parseAsOne(textField.getText().trim(), ExpressionLexer::new, ExpressionParser::new, parse);
        }
        catch (InternalException | UserException e)
        {
            return null;
        }
    }
}
