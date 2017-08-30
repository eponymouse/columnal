package records.gui.expressioneditor;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.jetbrains.annotations.NotNull;
import records.data.Column;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.BooleanLiteralContext;
import records.grammar.ExpressionParser.NumericLiteralContext;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.*;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import records.transformations.function.FunctionType;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Anything which fits in a normal text field without special structure, that is:
 *   - Column reference
 *   - Numeric literal
 *   - Boolean literal
 *   - Partial function name (until later transformed to function call)
 *   - Variable reference.
 */
public class GeneralExpressionEntry extends GeneralOperandEntry<Expression, ExpressionNodeParent> implements OperandNode<Expression>, ErrorDisplayer, EEDisplayNodeParent
{
    private static final String ARROW_SAME_ROW = "\u2192";
    private static final String ARROW_WHOLE = "\u2195";

    public static enum Status
    {
        COLUMN_REFERENCE_SAME_ROW("column-inner"),
        COLUMN_REFERENCE_WHOLE("column-inner"),
        TAG(null) /* if no inner */,
        LITERAL(null) /*number or bool, not string */,
        ANY(null) /* Pattern match "any" item */,
        VARIABLE_DECL(null) /* Declare new variable in pattern */,
        VARIABLE_USE(null),
        UNFINISHED(null);

        private final @Nullable String innerStyle;

        private Status(@Nullable String innerStyle)
        {
            this.innerStyle = innerStyle;
        }

        public @Nullable String getStyleWhenInner()
        {
            return innerStyle;
        }
    }

    /**
     * Shortcut for opening a bracketed section.  This is passed back to us by reference
     * from AutoCompletion, hence we mark it @Interned to allow reference comparison.
     */
    private final KeyShortcutCompletion bracketCompletion;
    /**
     * Shortcut for opening a unit section.  This is passed back to us by reference
     * from AutoCompletion.
     */
    private final Completion unitCompletion;
    /**
     * Shortcut for opening a string literal.  This is passed back to us by reference
     * from AutoCompletion, hence we mark it @Interned to allow reference comparison.
     */
    private final KeyShortcutCompletion stringCompletion;

    /**
     * Completion for declaring a new variable
     */
    private final Completion varDeclCompletion;

    /**
     * Completion for if-then-else
     */
    private final Completion ifCompletion;

    /**
     * Completion for match expressions
     */
    private final Completion matchCompletion;

    /**
     * Set to true while updating field with auto completion.  Allows us to avoid
     * certain listeners firing which should only fire when the user has made a change.
     */
    private boolean completing;

    /**
     * Current status of the field.
     */
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.UNFINISHED);

    /**
     * The auto-complete which will show when the user is entering input.
     */
    private final AutoComplete autoComplete;

    /**
     * An optional component appearing after the text field, for specifying units.
     * Surrounded by curly brackets.
     */
    private @Nullable UnitCompoundBase unitSpecifier;

    /**
     * The semantic parent which can be asked about available variables, etc
     */
    private final ExpressionNodeParent semanticParent;

    public GeneralExpressionEntry(String content, Status initialStatus, ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent)
    {
        super(Expression.class, parent);
        this.semanticParent = semanticParent;
        bracketCompletion = new KeyShortcutCompletion("Bracketed expressions", '(');
        stringCompletion = new KeyShortcutCompletion("Text", '\"');
        unitCompletion = new AddUnitCompletion();
        ifCompletion = new KeywordCompletion(ExpressionLexer.IF);
        matchCompletion = new KeywordCompletion(ExpressionLexer.MATCH);
        varDeclCompletion = new VarDeclCompletion();
        textField.setText(content);
        updateNodes();

        this.autoComplete = new AutoComplete(textField, this::getSuggestions, new CompletionListener(), c -> !Character.isAlphabetic(c) && (parent.operations.isOperatorAlphabet(c, parent.getThisAsSemanticParent()) || parent.terminatedByChars().contains(c)));

        FXUtility.addChangeListenerPlatformNN(status, s -> {
            // Need to beware that some status values may map to same pseudoclass:
            // First turn all off:
            for (Status possibleStatus : Status.values())
            {
                container.pseudoClassStateChanged(getPseudoClass(possibleStatus), false);
            }
            // Then turn on the one we want:
            container.pseudoClassStateChanged(getPseudoClass(s), true);
            typeLabel.setText(getTypeLabel(s));
        });
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), t -> {
            if (!completing)
            {
                status.set(Status.UNFINISHED);
            }
            else
                completing = false;
            textField.pseudoClassStateChanged(PseudoClass.getPseudoClass("ps-empty"), t.isEmpty());
            parent.changed(GeneralExpressionEntry.this);
        });
        textField.pseudoClassStateChanged(PseudoClass.getPseudoClass("ps-empty"), textField.getText().isEmpty());
        status.setValue(initialStatus);
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.concat(Stream.of(container), unitSpecifier == null ? Stream.empty() : unitSpecifier.nodes().stream());
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return Utility.streamNullable(unitSpecifier);
    }

    private static String getTypeLabel(Status s)
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

    private static PseudoClass getPseudoClass(Status s)
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

    @RequiresNonNull({"bracketCompletion", "unitCompletion", "stringCompletion", "ifCompletion", "matchCompletion", "varDeclCompletion", "parent", "semanticParent"})
    private List<Completion> getSuggestions(@UnknownInitialization(EntryNode.class)GeneralExpressionEntry this, String text, CompletionQuery completionQuery) throws UserException, InternalException
    {
        ArrayList<Completion> r = new ArrayList<>();
        r.add(bracketCompletion);
        r.add(stringCompletion);
        r.add(ifCompletion);
        r.add(matchCompletion);
        r.add(new NumericLiteralCompletion());
        addAllFunctions(r);
        r.add(new SimpleCompletion("", "any", "", Status.ANY));
        r.add(new SimpleCompletion("", "true", "", Status.LITERAL));
        r.add(new SimpleCompletion("", "false", "", Status.LITERAL));
        for (Column column : parent.getEditor().getAvailableColumns())
        {
            r.add(new SimpleCompletion(ARROW_SAME_ROW, column.getName().getRaw(), " [value in this row]", Status.COLUMN_REFERENCE_SAME_ROW));
            r.add(new SimpleCompletion(ARROW_WHOLE, column.getName().getRaw(), " [whole column]", Status.COLUMN_REFERENCE_WHOLE));
        }
        for (DataType dataType : parent.getEditor().getTypeManager().getKnownTaggedTypes().values())
        {
            for (TagType<DataType> tagType : dataType.getTagTypes())
            {
                r.add(new TagCompletion(dataType.getTaggedTypeName(), tagType));
            }
        }

        for (Pair<String, @Nullable DataType> variable : parent.getThisAsSemanticParent().getAvailableVariables(this))
        {
            r.add(new VarUseCompletion(variable.getFirst()));
        }

        // Must be last as it should be lowest priority:
        if (completionQuery != CompletionQuery.LEAVING_SLOT)
            r.add(unitCompletion);

        if (canDeclareVariable())
        {
            r.add(varDeclCompletion);
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

    @SuppressWarnings("initialization")
    private boolean canDeclareVariable(@UnknownInitialization GeneralExpressionEntry this)
    {
        return semanticParent.canDeclareVariable(this);
    }

    @RequiresNonNull("parent")
    private void addAllFunctions(@UnknownInitialization(EntryNode.class)GeneralExpressionEntry this, ArrayList<Completion> r)
    {
        for (FunctionDefinition function : FunctionList.FUNCTIONS)
        {
            r.add(new FunctionCompletion(function, parent.getEditor().getTypeManager().getUnitManager()));
        }
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return FXUtility.<Status, @Nullable String>mapBindingLazy(status, Status::getStyleWhenInner);
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
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(prefix.isEmpty() ? null : new Label(" " + prefix + " "), new ReadOnlyStringWrapper(text + suffix));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return text.startsWith(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return text.equals(input) ? (onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT) : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
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
        private final UnitManager unitManager;

        public FunctionCompletion(FunctionDefinition function, UnitManager unitManager)
        {
            this.function = function;
            this.unitManager = unitManager;
        }

        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(function.getName() + "(...)"));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return function.getName().startsWith(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
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
        public boolean features(String curInput, char character)
        {
            return function.getName().contains("" + character);
        }

        @Override
        public @Nullable Node getFurtherDetails()
        {
            TextFlow textFlow = new TextFlow();
            Text functionName = new Text(function.getName() + "\n");
            functionName.getStyleClass().add("function-info-name");
            textFlow.getChildren().add(functionName);
            textFlow.getChildren().addAll(
                TranslationUtility.makeTextLine(function.getShortDescriptionKey(), "function-info-short-description")
            );
            List<Node> overloads = new ArrayList<>();
            try
            {
                List<FunctionType> overloadTypes = function.getOverloads(unitManager);
                for (FunctionType functionType : overloadTypes)
                {
                    Text overloadType = new Text(functionType.getParamDisplay() + " -> " + functionType.getReturnDisplay() + "\n");
                    overloadType.getStyleClass().add("function-info-overload-type");
                    overloads.add(overloadType);
                    if (functionType.getOverloadDescriptionKey() != null)
                    {
                        overloads.addAll(TranslationUtility.makeTextLine(functionType.getOverloadDescriptionKey(), "function-info-overload-description"));
                    }
                }
            }
            catch (InternalException e)
            {
                Utility.log(e);
            }
            textFlow.getChildren().addAll(overloads);
            textFlow.getStyleClass().add("function-info");
            return textFlow;
        }
    }

    private static class NumericLiteralCompletion extends GeneralCompletion
    {
        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, currentText);
        }

        @Override
        public boolean shouldShow(String input)
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
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return onlyAvailableCompletion ? CompletionAction.SELECT : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
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
    public Expression save(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        if (status.get() == Status.COLUMN_REFERENCE_SAME_ROW || status.get() == Status.COLUMN_REFERENCE_WHOLE)
        {
            return errorDisplayer.record(this, new ColumnReference(new ColumnId(textField.getText()), status.get() == Status.COLUMN_REFERENCE_SAME_ROW ? ColumnReferenceType.CORRESPONDING_ROW : ColumnReferenceType.WHOLE_COLUMN));
        }
        else if (status.get() == Status.LITERAL)
        {
            NumericLiteralContext number = parseOrNull(ExpressionParser::numericLiteral);
            if (number != null)
            {
                try
                {
                    return errorDisplayer.record(this, new NumericLiteral(Utility.parseNumber(number.getText()), unitSpecifier == null ? null : unitSpecifier.save(errorDisplayer, onError)));
                }
                catch (UserException e)
                {
                    return errorDisplayer.record(this, new UnfinishedExpression(textField.getText().trim()));
                }
            }
            BooleanLiteralContext bool = parseOrNull(ExpressionParser::booleanLiteral);
            if (bool != null)
            {
                return errorDisplayer.record(this, new BooleanLiteral(bool.getText().equals("true")));
            }
        }
        else if (status.get() == Status.VARIABLE_DECL && textField.getText().trim().length() > 0)
        {
            return new VarDeclExpression(textField.getText().trim());
        }
        else if (status.get() == Status.VARIABLE_USE)
        {
            return new VarUseExpression(textField.getText().trim());
        }
        // Unfinished:
        return errorDisplayer.record(this, new UnfinishedExpression(textField.getText().trim()));
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

    private static class KeywordCompletion extends Completion
    {
        private final String keyword;

        private KeywordCompletion(int expressionLexerKeywordIndex)
        {
            this.keyword = Utility.literal(ExpressionLexer.VOCABULARY, expressionLexerKeywordIndex);
        }

        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(keyword));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return keyword.startsWith(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return input.equals(keyword) ? (onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT) : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return keyword.startsWith(curInput) && keyword.substring(curInput.length()).contains("" + character);
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
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(tagType.getName() + " [type " + typeName.getRaw() + "]"));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return tagType.getName().startsWith(input) || getScopedName().startsWith(input);
        }

        @NotNull
        private String getScopedName()
        {
            return typeName.getRaw() + ":" + tagType.getName();
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            if (tagType.equals(input) || getScopedName().equals(input))
                return onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT;
            return CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
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

    public @Nullable Column getColumn()
    {
        if (status.get() == Status.COLUMN_REFERENCE_SAME_ROW || status.get() == Status.COLUMN_REFERENCE_WHOLE)
        {
            // TODO get columns from... somewhere
        }
        return null;
    }

    private class CompletionListener extends SimpleCompletionListener
    {
        public CompletionListener()
        {
        }

        @Override
        protected String selected(String currentText, @Interned @Nullable Completion c, String rest)
        {
            if (c instanceof KeyShortcutCompletion)
            {
                @Interned KeyShortcutCompletion ksc = (@Interned KeyShortcutCompletion) c;
                if (ksc == bracketCompletion)
                {
                    BracketedExpression bracketedExpression = new BracketedExpression(ConsecutiveBase.EXPRESSION_OPS, parent, new Label("("), new Label(")"), null);
                    bracketedExpression.focusWhenShown();
                    parent.replace(GeneralExpressionEntry.this, bracketedExpression);
                }
                else if (ksc == stringCompletion)
                    parent.replace(GeneralExpressionEntry.this, focusWhenShown(new StringLiteralNode("", parent)));
            }
            else if (c == unitCompletion)
            {
                if (unitSpecifier == null)
                {
                    addUnitSpecifier(); // Should we put rest in the curly brackets?
                    status.set(Status.LITERAL);
                }
                else
                {
                    // If it's null and we're at the end, move into it:
                    if (rest.isEmpty())
                        unitSpecifier.focus(Focus.LEFT);
                }
            }
            else if (c != null && c.equals(ifCompletion))
            {
                parent.replace(GeneralExpressionEntry.this, new IfThenElseNode(parent, semanticParent));
            }
            else if (c != null && c.equals(matchCompletion))
            {
                parent.replace(GeneralExpressionEntry.this, focusWhenShown(new PatternMatchNode(parent, null)));
            }
            else if (c instanceof FunctionCompletion)
            {
                // What to do with rest != "" here? Don't allow? Skip to after args?
                FunctionCompletion fc = (FunctionCompletion)c;
                parent.replace(GeneralExpressionEntry.this, focusWhenShown(new FunctionNode(fc.function, semanticParent,null, parent)));
            }
            else if (c instanceof TagCompletion)
            {
                TagCompletion tc = (TagCompletion)c;
                TagExpressionNode tagExpressionNode = new TagExpressionNode(parent, semanticParent, tc.typeName, tc.tagType);
                if (tc.tagType.getInner() != null)
                {
                    tagExpressionNode.focusWhenShown();
                }
                else
                {
                    parent.focusRightOf(GeneralExpressionEntry.this, Focus.LEFT);
                }
                parent.replace(GeneralExpressionEntry.this, tagExpressionNode);
            }
            else if (c == varDeclCompletion)
            {
                completing = true;
                status.setValue(Status.VARIABLE_DECL);
                parent.setOperatorToRight(GeneralExpressionEntry.this, rest);
                parent.focusRightOf(GeneralExpressionEntry.this, Focus.RIGHT);
                return currentText;
            }
            else if (c instanceof VarUseCompletion)
            {
                completing = true;
                status.setValue(Status.VARIABLE_USE);
                parent.setOperatorToRight(GeneralExpressionEntry.this, rest);
                parent.focusRightOf(GeneralExpressionEntry.this, Focus.RIGHT);
                return currentText;
            }
            else if (c == null || c instanceof GeneralCompletion)
            {
                @Nullable GeneralCompletion gc = (GeneralCompletion) c;
                completing = true;
                parent.setOperatorToRight(GeneralExpressionEntry.this, rest);
                status.setValue(gc == null ? Status.UNFINISHED : gc.getType());
                // End of following operator, since we pushed rest into there:
                parent.focusRightOf(GeneralExpressionEntry.this, Focus.RIGHT);
                if (gc instanceof SimpleCompletion)
                {
                    prefix.setText(((SimpleCompletion)gc).prefix);
                    return ((SimpleCompletion) gc).getCompletedText();
                }
                else // Numeric literal or unfinished:
                {
                    prefix.setText("");
                    return currentText;
                }
            }
            else
                Utility.logStackTrace("Unsupported completion: " + c.getClass());
            return textField.getText();
        }
    }

    private static <T extends EEDisplayNode> T focusWhenShown(T node)
    {
        node.focusWhenShown();
        return node;
    }

    private void addUnitSpecifier()
    {
        if (unitSpecifier == null)
        {
            unitSpecifier = focusWhenShown(new UnitCompoundBase(this, true));
        }
        updateNodes();
        updateListeners();
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.RIGHT && unitSpecifier != null)
            unitSpecifier.focus(Focus.RIGHT);
        else
            super.focus(side);
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child;
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child, Focus side)
    {
        // Child is bound to be units:
        parent.focusRightOf(this, side);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        textField.requestFocus();
        textField.positionCaret(textField.getLength());
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getDeclaredVariables()
    {
        if (status.get() == Status.VARIABLE_DECL)
            return Collections.singletonList(new Pair<String, @Nullable DataType>(textField.getText().trim(), null));
        else
            return Collections.emptyList();
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return parent.getEditor();
    }

    private class AddUnitCompletion extends Completion
    {
        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(new Label(" { "), new ReadOnlyStringWrapper("Units"));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return isNumeric(input) && unitSpecifier == null;
        }

        private boolean isNumeric(String input)
        {
            return input.codePoints().allMatch(c -> (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.' || c == '{');
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            if (input.contains("{"))
            {
                return CompletionAction.COMPLETE_IMMEDIATELY;
            }
            else
            {
                return CompletionAction.NONE;
            }
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return character == '{';
        }
    }

    private static class VarDeclCompletion extends Completion
    {
        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(new Label("Named match"), currentText);
        }

        @Override
        public boolean shouldShow(String input)
        {
            return input.length() >= 1 && Character.isLetter(input.codePointAt(0));
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return onlyAvailableCompletion ? CompletionAction.SELECT : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return (curInput.length() >= 1 && (Character.isDigit(character) || character == ' ')) || Character.isLetter(character);
        }
    }

    private class VarUseCompletion extends Completion
    {
        private final String name;

        private VarUseCompletion(String name)
        {
            this.name = name;
        }

        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(new Label("Variable"), new ReadOnlyStringWrapper(name));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return name.startsWith(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return name.toLowerCase().startsWith(input.toLowerCase()) ? CompletionAction.SELECT : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return name.contains("" + character);
        }
    }
}
