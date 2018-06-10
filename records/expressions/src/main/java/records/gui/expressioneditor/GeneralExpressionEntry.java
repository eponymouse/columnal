package records.gui.expressioneditor;

import annotation.qual.Value;
import annotation.recorded.qual.UnknownIfRecorded;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import log.Log;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.CompleteNumericLiteralContext;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ExpressionSaver.Context;
import records.jellytype.JellyType;
import records.transformations.expression.*;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.QuickFix.ReplacementTarget;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import styled.StyledString;
import utility.Either;
import utility.ExFunction;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Anything which fits in a normal text field without special structure, that is:
 *   - Column reference
 *   - Numeric literal
 *   - Boolean literal
 *   - Partial function name (until later transformed to function call)
 *   - Variable reference.
 */
public class GeneralExpressionEntry extends GeneralOperandEntry<Expression, ExpressionSaver> implements ConsecutiveChild<Expression, ExpressionSaver>, ErrorDisplayer<Expression, ExpressionSaver>
{
    public static final String ARROW_SAME_ROW = "\u2192";
    public static final String ARROW_WHOLE = "\u2195";
    
    private static enum GeneralPseudoclass
    {
        COLUMN_REFERENCE("ps-column"),
        FUNCTION("ps-function"),
        TAG("ps-tag"),
        LITERAL("ps-literal"),
        VARIABLE_USE("ps-use"),
        VARIABLE_DECL("ps-decl"),
        LAMBDA_ARG("ps-lambda"),
        MATCH_ANYTHING("ps-anything"),
        UNFINISHED("ps-unfinished");

        private final String name;
        private GeneralPseudoclass(String name)
        {
            this.name = name;
        }
    }
    
    private void afterSave(Context context) {}
    
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
    private final VarDeclCompletion varDeclCompletion;
    
    /**
     * Completion for fixed-type expressions
     */
    private final Completion typeLiteralCompletion;

    /** Flag used to monitor when the initial content is set */
    private final SimpleBooleanProperty initialContentEntered = new SimpleBooleanProperty(false);

    private @Nullable Pair<String, Function<String, Expression>> savePrefix;
    
    GeneralExpressionEntry(String initialValue, ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        super(Expression.class, parent);
        stringCompletion = new KeyShortcutCompletion("autocomplete.string", '\"');
        unitCompletion = new AddUnitCompletion();
        typeLiteralCompletion = new KeyShortcutCompletion("autocomplete.type", '`');
        varDeclCompletion = new VarDeclCompletion();
        updateNodes();

        this.autoComplete = new AutoComplete<Completion>(textField, this::getSuggestions, new CompletionListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, c -> !Character.isAlphabetic(c) && (parent.operations.isOperatorAlphabet(c) || parent.terminatedByChars().contains(c)));

        updateGraphics();
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), focus -> {
            updateGraphics();
        });
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), t -> {
            if (!completing)
            {
                savePrefix = null;
                prefix.setText("");
            }
            completing = false;
            updateGraphics();
            parent.changed(GeneralExpressionEntry.this);
        });
        textField.setText(initialValue);
        initialContentEntered.set(true);
    }

    @RequiresNonNull({"container", "textField", "typeLabel"})
    private void updateGraphics(@UnknownInitialization(Object.class) GeneralExpressionEntry this)
    {
        /*
        for (GeneralPseudoclass possibleStatus : GeneralPseudoclass.values())
        {
            container.pseudoClassStateChanged(PseudoClass.getPseudoClass(possibleStatus.name), false);
        }
        // Then turn on the one we want:
        GeneralPseudoclass actual = currentValue.get().getPseudoclass();
        if (actual != null)
            container.pseudoClassStateChanged(PseudoClass.getPseudoClass(actual.name), true);
        //textField.pseudoClassStateChanged(PseudoClass.getPseudoClass("ps-empty"), textField.getText().isEmpty());
        typeLabel.setText(currentValue.get().getTypeLabel(textField.isFocused()));
        */
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(container);
    }

    @RequiresNonNull({"unitCompletion", "stringCompletion", "typeLiteralCompletion", "varDeclCompletion", "parent"})
    private List<Completion> getSuggestions(@UnknownInitialization(EntryNode.class) GeneralExpressionEntry this, String text, CompletionQuery completionQuery) throws UserException, InternalException
    {
        ArrayList<Completion> r = new ArrayList<>();
        for (Keyword keyword : Keyword.values())
        {
            r.add(new KeywordCompletion(keyword));
        }
        for (Op op : Op.values())
        {
            r.add(new OperatorCompletion(op));
        }
        r.add(typeLiteralCompletion);
        
        addAllFunctions(r);
        r.add(new SimpleCompletion("true", null));
        r.add(new SimpleCompletion("false", null));
        for (ColumnReference column : Utility.iterableStream(parent.getEditor().getAvailableColumnReferences()))
        {
            r.add(new ColumnCompletion(column));
        }
        for (TaggedTypeDefinition taggedType : parent.getEditor().getTypeManager().getKnownTaggedTypes().values())
        {
            try
            {
                List<TagType<JellyType>> tagTypes = taggedType.getTags();
                for (int i = 0; i < tagTypes.size(); i++)
                {
                    r.add(new TagCompletion(new TagInfo(taggedType, i)));
                }
            }
            catch (InternalException e)
            {
                Log.log(e);
                // Forget that type, then...
            }
        }

        // Must be last as it should be lowest priority:
        if (completionQuery != CompletionQuery.LEAVING_SLOT)
            r.add(unitCompletion);

        r.add(varDeclCompletion);

        r.add(stringCompletion);
        r.add(new NumericLiteralCompletion());

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

    @RequiresNonNull("parent")
    private void addAllFunctions(@UnknownInitialization(EntryNode.class)GeneralExpressionEntry this, ArrayList<Completion> r) throws InternalException
    {
        for (FunctionDefinition function : FunctionList.getAllFunctions(parent.getEditor().getTypeManager().getUnitManager()))
        {
            r.add(new FunctionCompletion(function));
        }
    }

    private static abstract class GeneralCompletion extends Completion
    {
        abstract String getValue(String currentText);
    }

    private static class SimpleCompletion extends GeneralCompletion
    {
        private final String text;
        private final @Nullable @LocalizableKey String descriptionKey;

        public SimpleCompletion(String text, @Nullable @LocalizableKey String descriptionKey)
        {
            this.descriptionKey = descriptionKey;
            this.text = text;
        }


        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(text, descriptionKey == null ? null : TranslationUtility.getString(descriptionKey));
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

        @Override
        String getValue(String currentText)
        {
            return text;
        }

        // For debugging:
        @Override
        public String toString()
        {
            return "SimpleCompletion{" +
                    ", text='" + text + '\'' +
                    ", description='" + descriptionKey + '\'' +
                    '}';
        }
    }

    private static class ColumnCompletion extends GeneralCompletion
    {
        private final ColumnReference columnReference;

        public ColumnCompletion(ColumnReference columnReference)
        {
            this.columnReference = columnReference;
        }


        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(getArrow() + columnReference.getColumnId().getRaw(), getDescription());
        }

        private String getArrow()
        {
            if (columnReference.getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW)
            {
                return ARROW_SAME_ROW;
            }
            else
            {
                return ARROW_WHOLE;
            }
        }
        
        private @Localized String getDescription()
        {
            if (columnReference.getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW)
            {
                return TranslationUtility.getString("autocomplete.column.sameRow");
            }
            else
            {
                return TranslationUtility.getString("autocomplete.column.entire");
            }
        }

        @Override
        public boolean shouldShow(String input)
        {
            return columnReference.getColumnId().getRaw().startsWith(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return columnReference.getColumnId().getRaw().equals(input) ? (onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT) : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return columnReference.getColumnId().getRaw().contains("" + character);
        }

        @Override
        String getValue(String currentText)
        {
            return columnReference.getColumnId().getRaw();
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
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(function.getName() + "(...)", function.getMiniDescription());
        }

        @Override
        public boolean shouldShow(String input)
        {
            return (function.getName() + "(").startsWith(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            if ((function.getName() + "(").equals(input))
            {
                if (onlyAvailableCompletion)
                    return CompletionAction.COMPLETE_IMMEDIATELY;
                else
                    return CompletionAction.SELECT;
            }
            return CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return function.getName().contains("" + character) || (curInput.equals(function.getName()) && character == '(');
        }
        
        @Override
        public @Nullable String getFurtherDetailsURL()
        {
            String scopedName = "/function-" + function.getDocKey().replace("/", "-");
            URL url = getClass().getResource(scopedName + ".html");
            if (url != null)
                return url.toExternalForm() + "#" + scopedName;
            else
            {
                Log.error("Missing file: " + scopedName);
                return null;
            }
        }
    }

    private class NumericLiteralCompletion extends GeneralCompletion
    {
        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(currentText, null);
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
            CompleteNumericLiteralContext number = parseOrNull(ExpressionParser::completeNumericLiteral);
            if (number != null)
            {
                return CompletionAction.SELECT;
            }
            else
            {
                return CompletionAction.NONE;
            }
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
        String getValue(String currentText)
        {
            return currentText;
        }
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
        private final Keyword keyword;

        private KeywordCompletion(Keyword keyword)
        {
            this.keyword = keyword;
        }

        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(keyword.getContent(), null /* TODO */);
        }

        @Override
        public boolean shouldShow(String input)
        {
            return keyword.getContent().startsWith(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return input.equals(keyword.getContent()) ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return keyword.getContent().startsWith(curInput) && keyword.getContent().substring(curInput.length()).contains("" + character);
        }

        public Stream<SingleLoader<Expression, ExpressionSaver>> load()
        {
            return Stream.of(GeneralExpressionEntry.load(keyword));
        }
    }

    private static class OperatorCompletion extends Completion
    {
        private final Op operator;

        private OperatorCompletion(Op operator)
        {
            this.operator = operator;
        }

        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(operator.getContent(), null /* TODO */);
        }

        @Override
        public boolean shouldShow(String input)
        {
            return operator.getContent().startsWith(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return input.equals(operator.getContent()) ? (onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT) : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return operator.getContent().startsWith(curInput) && operator.getContent().substring(curInput.length()).contains("" + character);
        }

        public Stream<SingleLoader<Expression, ExpressionSaver>> load()
        {
            return Stream.of(GeneralExpressionEntry.load(operator));
        }
    }

    private class TagCompletion extends Completion
    {
        private final TagInfo tagInfo;

        public TagCompletion(TagInfo tagInfo)
        {
            this.tagInfo = tagInfo;
        }

        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(tagInfo.getTagInfo().getName() + " [type " + tagInfo.getTypeName() + "]", null);
        }

        @Override
        public boolean shouldShow(String input)
        {
            return tagInfo.getTagInfo().getName().startsWith(input) || getScopedName().startsWith(input);
        }

        @NonNull
        private String getScopedName()
        {
            return tagInfo.getTypeName().getRaw() + ":" + tagInfo.getTagInfo().getName();
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            if (tagInfo.getTagInfo().getName().equals(input) || getScopedName().equals(input))
                return onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT;
            return CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            // Important to check type first.  If type is same as tag,
            // type will permit more characters to follow than tag alone:
            if (tagInfo.getTypeName().getRaw().startsWith(curInput))
            {
                // It is part/all of the type, what's left is colon
                return character == ':' || tagInfo.getTagInfo().getName().contains("" + character);
            }
            else if (getScopedName().startsWith(curInput))
            {
                // Since type name didn't start with input, this must include middle
                // colon and then some:
                return getScopedName().substring(curInput.length()).contains("" + character);
            }
            else if (tagInfo.getTagInfo().getName().startsWith(curInput))
            {
                return tagInfo.getTagInfo().getName().substring(curInput.length()).contains("" + character);
            }
            return false;
        }
    }

    private class CompletionListener extends SimpleCompletionListener<Completion>
    {
        public CompletionListener()
        {
        }

        @Override
        protected String selected(String currentText, @Interned @Nullable Completion c, String rest)
        {
            // Only move focus if we had it to begin with:
            return selected(currentText, c, rest, isFocused());
        }

        
        private String selected(String currentText, @Interned @Nullable Completion c, String rest, boolean moveFocus)
        {
            savePrefix = null;
            prefix.setText("");
            
            if (c instanceof KeyShortcutCompletion)
            {
                @Interned KeyShortcutCompletion ksc = (@Interned KeyShortcutCompletion) c;
                if (ksc == stringCompletion)
                {
                    parent.replace(GeneralExpressionEntry.this, focusWhenShown(new StringLiteralNode("", parent)));
                }
                else if (ksc == typeLiteralCompletion)
                {
                    parent.replace(GeneralExpressionEntry.this, focusWhenShown(new TypeLiteralNode(parent, null)));
                }
            }
            else if (Objects.equals(c, unitCompletion))
            {
                parent.ensureOperandToRight(GeneralExpressionEntry.this,  o -> o instanceof UnitLiteralExpressionNode, () -> Stream.of(p -> {
                    UnitLiteralExpressionNode unitLiteralNode = new UnitLiteralExpressionNode(p, new SingleUnitExpression(rest));
                    unitLiteralNode.focusWhenShown();
                    return unitLiteralNode;
                }));
                return currentText.replace("{", "");
            }
            else if (c instanceof KeywordCompletion)
            {
                completing = true;
                // Must do this while completing so that we're not marked as blank:
                parent.focusRightOf(GeneralExpressionEntry.this, Focus.LEFT);                
                return ((KeywordCompletion) c).keyword.getContent();
            }
            else if (c instanceof OperatorCompletion)
            {
                completing = true;
                // Must do this while completing so that we're not marked as blank:
                parent.focusRightOf(GeneralExpressionEntry.this, Focus.LEFT);                
                return ((OperatorCompletion) c).operator.getContent();
            }
            else if (c instanceof FunctionCompletion)
            {
                // What to do with rest != "" here? Don't allow? Skip to after args?
                FunctionCompletion fc = (FunctionCompletion)c;
                completing = true;
                parent.ensureOperandToRight(GeneralExpressionEntry.this, GeneralExpressionEntry::isRoundBracket, () -> loadEmptyRoundBrackets());
                setPrefixFunction(fc.function);
                return fc.function.getName();
            }
            else if (c instanceof TagCompletion)
            {
                TagCompletion tc = (TagCompletion)c;
                
                if (tc.tagInfo.getTagInfo().getInner() != null)
                {
                    parent.ensureOperandToRight(GeneralExpressionEntry.this, GeneralExpressionEntry::isRoundBracket, () -> loadEmptyRoundBrackets());
                }
                else
                {
                    if (moveFocus)
                        parent.focusRightOf(GeneralExpressionEntry.this, Focus.LEFT);
                }
                completing = true;
                setPrefixTag(tc.tagInfo.getTypeName());
                return tc.tagInfo.getTagInfo().getName();
            }
            else if (c instanceof ColumnCompletion)
            {
                ColumnCompletion cc = (ColumnCompletion)c;
                completing = true;
                setPrefixColumn(cc.columnReference);
                return cc.columnReference.getColumnId().getRaw();
            }
            else if (c == varDeclCompletion)
            {
                completing = true;
                //parent.setOperatorToRight(GeneralExpressionEntry.this, rest);
                if (moveFocus)
                    parent.focusRightOf(GeneralExpressionEntry.this, Focus.RIGHT);
                return varDeclCompletion.getVarName(currentText);
            }
            else if (c == null || c instanceof GeneralCompletion)
            {
                @Nullable GeneralCompletion gc = (GeneralCompletion) c;
                completing = true;
                //parent.setOperatorToRight(GeneralExpressionEntry.this, rest);
                // End of following operator, since we pushed rest into there:
                if (moveFocus)
                
                if (gc instanceof SimpleCompletion)
                {
                    return ((SimpleCompletion) gc).getValue(currentText);
                }
                else // Numeric literal or unfinished:
                {
                    prefix.setText("");
                    return currentText;
                }
            }
            else
                Log.logStackTrace("Unsupported completion: " + c.getClass());
            return textField.getText();
        }

        @Override
        public String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
        {
            if (!(selectedItem instanceof KeyShortcutCompletion))
            {
                return selected(currentText, selectedItem, "", false);
            }
            return currentText;
        }

        @Override
        public void tabPressed()
        {
            parent.focusRightOf(GeneralExpressionEntry.this, Focus.LEFT);
        }
    }
    
    private static boolean isRoundBracket(ConsecutiveChild<Expression, ExpressionSaver> item)
    {
        return item instanceof GeneralExpressionEntry && ((GeneralExpressionEntry)item).textField.getText().equals(Keyword.OPEN_ROUND.getContent());
    }

    private Stream<SingleLoader<Expression, ExpressionSaver>> loadEmptyRoundBrackets()
    {
        return Stream.of(load(Keyword.OPEN_ROUND), load("").focusWhenShown(), load(Keyword.CLOSE_ROUND));
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
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child;
    }

    @Override
    public String toString()
    {
        // Useful for debugging:
        return super.toString() + ";" + textField;
    }

    private class AddUnitCompletion extends Completion
    {
        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent("{", TranslationUtility.getString("expression.autocomplete.units"));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return isNumeric(input);
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
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(currentText, TranslationUtility.getString("expression.autocomplete.variable"));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return input.startsWith("$") && (input.length() == "$".length() || Character.isLetter(input.codePointAt("$".length())));
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

        public String getVarName(String currentText)
        {
            return StringUtils.removeStart(currentText, "$");
        }
    }
    
    /*
    public static class ColumnRef extends GeneralOperand
    {
        private final ColumnReference columnReference;

        public ColumnRef(ColumnReference columnReference)
        {
            this.columnReference = columnReference;
        }

        @Override
        public String getContent()
        {
            String prefix = "";
            if (columnReference.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN)
                prefix = "@entire ";
            return prefix + columnReference.getColumnId().getRaw();
        }

        @Override
        public @Nullable GeneralPseudoclass getPseudoclass()
        {
            return GeneralPseudoclass.COLUMN_REFERENCE;
        }

        @Override
        public String getTypeLabel(boolean focused)
        {
            String s = "Column";
            if (columnReference.getTableId() != null)
                s += " " + columnReference.getTableId().getRaw() + "\\";
            return s;
        }

        @Override
        public @UnknownIfRecorded Expression saveUnrecorded(ErrorAndTypeRecorder onError)
        {
            return columnReference;
        }
    }
    */
    
    public static enum Op
    {
        AND("&"), OR("|"), MULTIPLY("*"), ADD("+"), SUBTRACT("-"), DIVIDE("/"), STRING_CONCAT(";"), EQUALS("="), NOT_EQUAL("<>"), PLUS_MINUS("\u00B1"), RAISE("^"),
        COMMA(","),
        LESS_THAN("<"), LESS_THAN_OR_EQUAL("<="), GREATER_THAN(">"), GREATER_THAN_OR_EQUAL(">=");

        private final String op;

        private Op(String op)
        {
            this.op = op;
        }

        public String getContent()
        {
            return op;
        }
    }
    
    public static SingleLoader<Expression, ExpressionSaver> load(String value)
    {
        return p -> new GeneralExpressionEntry(value, p);
    }

    public static SingleLoader<Expression, ExpressionSaver> load(ColumnReference columnReference)
    {
        return p -> {
            GeneralExpressionEntry gee = new GeneralExpressionEntry(columnReference.getColumnId().getRaw(), p);
            gee.setPrefixColumn(columnReference);
            return gee;
        };
    }
    
    private void setPrefixColumn(ColumnReference columnReference)
    {
        savePrefix = new Pair<>(columnReference.getTableId() == null ? "" : (columnReference.getTableId().getRaw() + ":"), r -> new ColumnReference(columnReference.getTableId(), new ColumnId(r), columnReference.getReferenceType()));
    }

    public static SingleLoader<Expression, ExpressionSaver> load(ConstructorExpression constructorExpression)
    {
        return p -> {
            GeneralExpressionEntry gee = new GeneralExpressionEntry(constructorExpression.getName(), p);
            @Nullable TypeId typeName = constructorExpression.getTypeName();
            if (typeName != null)
            {
                gee.setPrefixTag(typeName);
            }
            return gee;
        };
    }
    
    private void setPrefixTag(TypeId typeName)
    {
        savePrefix = new Pair<>(typeName.getRaw() + ":", r -> new ConstructorExpression(getParent().getEditor().getTypeManager(), typeName.getRaw(), r));
        prefix.setText(savePrefix.getFirst());
    }

    public static SingleLoader<Expression, ExpressionSaver> load(StandardFunction standardFunction)
    {
        return p -> {
            GeneralExpressionEntry gee = new GeneralExpressionEntry(standardFunction.getName(), p);
            gee.setPrefixFunction(standardFunction.getFunction());
            return gee;
        };
    }
    
    private void setPrefixFunction(FunctionDefinition standardFunction)
    {
        savePrefix = new Pair<>(standardFunction.getNamespace() + ":", r -> {
            try
            {
                FunctionDefinition functionDefinition = FunctionList.lookup(getParent().getEditor().getTypeManager().getUnitManager(), standardFunction.getNamespace() + ":" + r);
                if (functionDefinition != null)
                    return new StandardFunction(functionDefinition);
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
            return new IdentExpression(r);
        });
        prefix.setText(savePrefix.getFirst());
    }

    public static SingleLoader<Expression, ExpressionSaver> load(Op value)
    {
        return load(value.getContent());
    }

    public static SingleLoader<Expression, ExpressionSaver> load(Keyword value)
    {
        return load(value.getContent());
    }
    
    public static enum Keyword
    {
        OPEN_SQUARE("["), CLOSE_SQUARE("]"), OPEN_ROUND("("), CLOSE_ROUND(")"), ANYTHING("@anything"), QUEST("?"),
        IF("@if"), THEN("@then"), ELSE("@else"), ENDIF("@endif"),
        MATCH("@match"), CASE("@case"), ORCASE("@orcase"), GIVEN("@given"), ENDMATCH("@endmatch");

        private final String op;

        private Keyword(String op)
        {
            this.op = op;
        }

        public String getContent()
        {
            return op;
        }
    }

    @Override
    public boolean availableForFocus()
    {
        // TODO base this on last saved item.
        return true;
    }


    @Override
    public void save(ExpressionSaver saver)
    {
        String text = textField.getText().trim();
        
        if (text.isEmpty())
            return; // Don't save blanks

        for (Op op : Op.values())
        {
            if (op.getContent().equals(text))
            {
                saver.saveOperator(op, this, this::afterSave);
                return;
            }
        }

        for (Keyword keyword : Keyword.values())
        {
            if (keyword.getContent().equals(text))
            {
                saver.saveKeyword(keyword, this, this::afterSave);
                return;
            }
        }
                
        Optional<@Value Number> number = Utility.parseNumberOpt(text);
        if (number.isPresent())
        {
            saver.saveOperand(new NumericLiteral(number.get(), null), this, this::afterSave);
        }
        else if (text.startsWith("$"))
        {
            saver.saveOperand(new VarDeclExpression(text.substring(1)), this, this::afterSave);
        }
        else if (text.equals("true") || text.equals("false"))
        {
            saver.saveOperand(new BooleanLiteral(text.equals("true")), this, this::afterSave);
        }
        else
        {
            if (savePrefix != null)
                saver.saveOperand(savePrefix.getSecond().apply(text), this, this::afterSave);
            else
                saver.saveOperand(new IdentExpression(text), this, this::afterSave);
        }
        
    }
}
