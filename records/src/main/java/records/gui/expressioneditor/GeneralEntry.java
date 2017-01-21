package records.gui.expressioneditor;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.BooleanLiteralContext;
import records.grammar.ExpressionParser.NumericLiteralContext;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Anything which fits in a normal text field without special structure, that is:
 *   - Column reference
 *   - Numeric literal
 *   - Boolean literal
 *   - Partial function name (until later transformed to function call)
 *   - Variable reference.
 */
public class GeneralEntry extends LeafNode implements OperandNode
{
    private static final String ARROW_SAME_ROW = "\u2192";
    private static final String ARROW_WHOLE = "\u2195";

    public static enum Status
    {
        COLUMN_REFERENCE_SAME_ROW,
        COLUMN_REFERENCE_WHOLE,
        TAG /* if no inner */,
        LITERAL /*number or bool, not string */,
        VARIABLE_USE,
        UNFINISHED;
    }


    /**
     * The outermost container for the whole thing:
     */
    private final VBox container;
    /**
     * Shortcut for opening a bracketed section.  This is passed back to us by reference
     * from AutoCompletion, hence we mark it @Interned to allow reference comparison.
     */
    private final @Interned KeyShortcutCompletion bracketCompletion;
    /**
     * Shortcut for opening a string literal.  This is passed back to us by reference
     * from AutoCompletion, hence we mark it @Interned to allow reference comparison.
     */
    private final @Interned KeyShortcutCompletion stringCompletion;
    /**
     * Set to true while updating field with auto completion.  Allows us to avoid
     * certain listeners firing which should only fire when the user has made a change.
     */
    private boolean completing;

    /**
     * A label to the left of the text-field, used for displaying things like the
     * arrows on column reference
     */
    private final Label prefix;

    /**
     * The textfield the user edits with the content
     */
    private final TextField textField;
    /**
     * The label which sits at the top describing the type
     */
    private final Label typeLabel;
    /**
     * Current status of the field.
     */
    private ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.UNFINISHED);
    /**
     * Permanent reference to list of contained nodes (for ExpressionNode.nodes)
     */
    private final ObservableList<Node> nodes;
    /**
     * The auto-complete which will show when the user is entering input.
     */
    private final AutoComplete autoComplete;

    @SuppressWarnings("initialization")
    public GeneralEntry(String content, Consecutive parent)
    {
        super(parent);
        bracketCompletion = new @Interned KeyShortcutCompletion("Bracketed expressions", '(');
        stringCompletion = new @Interned KeyShortcutCompletion("Text", '\"');
        this.textField = new LeaveableTextField(this, parent);
        textField.setText(content);
        textField.getStyleClass().add("entry-field");
        Utility.sizeToFit(textField, null, null);
        typeLabel = new Label();
        typeLabel.getStyleClass().add("entry-type");
        prefix = new Label();
        container = new VBox(typeLabel, new HBox(prefix, textField));
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
                    //else if (ksc == patternMatchCompletion)
                        //parent.replace(GeneralEntry.this, new PatternMatchNode(parent).focusWhenShown());
                }
                else if (c instanceof FunctionCompletion)
                {
                    // What to do with rest != "" here? Don't allow? Skip to after args?
                    FunctionCompletion fc = (FunctionCompletion)c;
                    parent.replace(GeneralEntry.this, new FunctionNode(fc.function.getName(), parent).focusWhenShown());
                }
                else if (c instanceof TagCompletion)
                {
                    TagCompletion tc = (TagCompletion)c;
                    parent.replace(GeneralEntry.this, new TagExpressionNode(parent, tc.typeName, tc.tagType).focusWhenShown());
                }
                else if (c instanceof GeneralCompletion)
                {
                    GeneralCompletion gc = (GeneralCompletion) c;
                    completing = true;
                    parent.setOperatorToRight(GeneralEntry.this, rest);
                    status.setValue(gc.getType());
                    if (gc instanceof SimpleCompletion)
                    {
                        prefix.setText(((SimpleCompletion)gc).prefix);
                        return ((SimpleCompletion) gc).getCompletedText();
                    }
                    else // Numeric literal:
                    {
                        prefix.setText("");
                        return currentText;
                    }
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
            case COLUMN_REFERENCE_SAME_ROW:
                return "column";
            case COLUMN_REFERENCE_WHOLE:
                return "whole column";
            case TAG:
                return "tag";
            case LITERAL:
                return "";
            case VARIABLE_USE:
                return "variable";
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
            case COLUMN_REFERENCE_SAME_ROW:
            case COLUMN_REFERENCE_WHOLE:
                pseudoClass = "ps-column";
                break;
            case TAG:
                pseudoClass = "ps-tag";
                break;
            case LITERAL:
                pseudoClass = "ps-literal";
                break;
            case VARIABLE_USE:
                pseudoClass = "ps-variable";
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
        r.add(new NumericLiteralCompletion());
        addAllFunctions(r);
        r.add(new SimpleCompletion("", "true", "", Status.LITERAL));
        r.add(new SimpleCompletion("", "false", "", Status.LITERAL));
        for (ColumnId columnId : parent.getAvailableColumns())
        {
            r.add(new SimpleCompletion(ARROW_SAME_ROW, columnId.getRaw(), " [value in this row]", Status.COLUMN_REFERENCE_SAME_ROW));
            r.add(new SimpleCompletion(ARROW_WHOLE, columnId.getRaw(), " [whole column]", Status.COLUMN_REFERENCE_WHOLE));
        }
        for (DataType dataType : parent.getAvailableTaggedTypes())
        {
            for (TagType<DataType> tagType : dataType.getTagTypes())
            {
                r.add(new TagCompletion(dataType.getTaggedTypeName(), tagType));
            }
        }

        // TODO: use type to prioritise and to filter
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
        private final String prefix;
        private final String text;
        private final String suffix;
        private final Status type;

        public SimpleCompletion(String prefix, String text, String suffix, Status type)
        {
            this.prefix = prefix;
            this.suffix = suffix;
            this.text = text;
            this.type = type;
        }


        @Override
        Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(prefix.isEmpty() ? null : new Label(" " + prefix + " "), new ReadOnlyStringWrapper(text + suffix));
        }

        @Override
        boolean shouldShow(String input)
        {
            return text.startsWith(input);
        }

        @Override
        CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return text.equals(input) ? (onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT) : CompletionAction.NONE;
        }

        @Override
        boolean features(String curInput, char character)
        {
            return text.contains("" + character);
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


    public static class FunctionCompletion extends Completion
    {
        private final FunctionDefinition function;

        public FunctionCompletion(FunctionDefinition function)
        {
            this.function = function;
        }

        @Override
        Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(function.getName() + "(...)"));
        }

        @Override
        boolean shouldShow(String input)
        {
            return function.getName().startsWith(input);
        }

        @Override
        CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            if (function.getName().equals(input))
            {
                if (onlyAvailableCompletion)
                    return CompletionAction.COMPLETE_IMMEDIATELY;
                else
                    return CompletionAction.SELECT;
            }
            if ((function.getName() + "(").equals(input))
                return CompletionAction.COMPLETE_IMMEDIATELY;
            return CompletionAction.NONE;
        }

        @Override
        boolean features(String curInput, char character)
        {
            return function.getName().contains("" + character);
        }
    }

    private static class NumericLiteralCompletion extends GeneralCompletion
    {
        @Override
        Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, currentText);
        }

        @Override
        boolean shouldShow(String input)
        {
            // To allow "+", "-", or "1.", we add zero at the end before parsing:
            try
            {
                return Utility.parseAsOne(input + "0", ExpressionLexer::new, ExpressionParser::new, p -> p.numericLiteral())
                    != null;
            }
            catch (InternalException | UserException e)
            {
                return false;
            }
        }

        @Override
        CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return onlyAvailableCompletion ? CompletionAction.SELECT : CompletionAction.NONE;
        }

        @Override
        boolean features(String curInput, char character)
        {
            if (curInput.isEmpty())
                return "0123456789+-._".contains("" + character);
            else
            {
                if (curInput.contains("."))
                    return "0123456789_".contains("" + character);
                else
                    return "0123456789._".contains("" + character);
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
    public OperandNode prompt(String prompt)
    {
        textField.setPromptText(prompt);
        return this;
    }

    @Override
    public @Nullable Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        if (status.get() == Status.COLUMN_REFERENCE_SAME_ROW || status.get() == Status.COLUMN_REFERENCE_WHOLE)
        {
            return new ColumnReference(new ColumnId(textField.getText()), status.get() == Status.COLUMN_REFERENCE_SAME_ROW ? ColumnReferenceType.CORRESPONDING_ROW : ColumnReferenceType.WHOLE_COLUMN);
        }
        else if (status.get() == Status.LITERAL)
        {
            NumericLiteralContext number = parseOrNull(ExpressionParser::numericLiteral);
            if (number != null)
            {
                //TODO support units
                try
                {
                    return new NumericLiteral(Utility.parseNumber(number.getText()), null);
                }
                catch (UserException e)
                {
                    return null;
                }
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

    private class TagCompletion extends Completion
    {
        private final TagType<DataType> tagType;
        private final TypeId typeName;

        public TagCompletion(TypeId taggedTypeName, TagType<DataType> tagType)
        {
            this.typeName = taggedTypeName;
            this.tagType = tagType;
        }

        @Override
        Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(tagType.getName() + " [type " + typeName.getRaw() + "]"));
        }

        @Override
        boolean shouldShow(String input)
        {
            return tagType.getName().startsWith(input) || getScopedName().startsWith(input);
        }

        @NotNull
        private String getScopedName()
        {
            return typeName.getRaw() + ":" + tagType.getName();
        }

        @Override
        CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            if (tagType.equals(input) || getScopedName().equals(input))
                return onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT;
            return CompletionAction.NONE;
        }

        @Override
        boolean features(String curInput, char character)
        {
            // Important to check type first.  If type is same as tag,
            // type will permit more characters to follow than tag alone:
            if (typeName.getRaw().startsWith(curInput))
            {
                // It is part/all of the type, what's left is colon
                return character == ':' || tagType.getName().contains("" + character);
            }
            else if (getScopedName().startsWith(curInput))
            {
                // Since type name didn't start with input, this must include middle
                // colon and then some:
                return getScopedName().substring(curInput.length()).contains("" + character);
            }
            else if (tagType.getName().startsWith(curInput))
            {
                return tagType.getName().substring(curInput.length()).contains("" + character);
            }
            return false;
        }
    }
}
