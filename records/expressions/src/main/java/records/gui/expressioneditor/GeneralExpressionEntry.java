package records.gui.expressioneditor;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import log.Log;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.ColumnId;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager.TagInfo;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.Completion.ShowStatus;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ExpressionSaver.Context;
import records.jellytype.JellyType;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ConstructorExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.StandardFunction;
import records.transformations.expression.VarDeclExpression;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
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
        STANDARD("ps-standard"),
        KEYWORD("ps-keyword"),
        OP("ps-op");

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
        //Log.logStackTrace("Made new GEE with [[" + initialValue + "]]");
        stringCompletion = new KeyShortcutCompletion("autocomplete.string", '\"');
        unitCompletion = new AddUnitCompletion();
        typeLiteralCompletion = new NestedLiteralCompletion("type{");
        varDeclCompletion = new VarDeclCompletion();
        updateNodes();

        this.autoComplete = new AutoComplete<Completion>(textField, this::getSuggestions, new CompletionListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, ExpressionOps::differentAlphabet);

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
    private Stream<Completion> getSuggestions(@UnknownInitialization(EntryNode.class) GeneralExpressionEntry this, String text, CompletionQuery completionQuery) throws UserException, InternalException
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

        // TODO: use type and completion status to prioritise and to filter
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
        r.removeIf(c -> c.shouldShow(text) == ShowStatus.NO_MATCH);
        return r.stream();
    }

    @RequiresNonNull("parent")
    private void addAllFunctions(@UnknownInitialization(EntryNode.class)GeneralExpressionEntry this, ArrayList<Completion> r) throws InternalException
    {
        for (FunctionDefinition function : FunctionList.getAllFunctions(parent.getEditor().getTypeManager().getUnitManager()))
        {
            r.add(new FunctionCompletion(function));
        }
    }

    private static class ColumnCompletion extends Completion
    {
        private final ColumnReference columnReference;
        private final String fullText;

        public ColumnCompletion(ColumnReference columnReference)
        {
            this.columnReference = columnReference;
            if (columnReference.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN)
                fullText = "@entire " + columnReference.getColumnId().getRaw();
            else
                fullText = columnReference.getColumnId().getRaw();
        }


        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(fullText, getDescription());
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
        public ShowStatus shouldShow(String input)
        {
            ShowStatus fallback = ShowStatus.NO_MATCH;
            for (String possible : ImmutableList.of(fullText)) // TODO also support scoped name?
            {
                if (possible.equals(input))
                    return ShowStatus.DIRECT_MATCH;
                else if (possible.startsWith(input))
                    fallback = ShowStatus.START_DIRECT_MATCH;
            }
            return fallback;
        }

        @Override
        public boolean features(String curInput, int character)
        {
            return Utility.containsCodepoint(fullText, character);
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
        public ShowStatus shouldShow(String input)
        {
            if (input.equals(function.getName() + "("))
                return ShowStatus.DIRECT_MATCH;
            else if (function.getName().startsWith(input))
                return ShowStatus.START_DIRECT_MATCH;
            else
                return ShowStatus.NO_MATCH;
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true; // If it includes the bracket, it's a direct match, and complete immmediately
        }

        @Override
        public boolean features(String curInput, int character)
        {
            return Utility.containsCodepoint(function.getName(), character) || (curInput.equals(function.getName()) && character == '(');
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

    private class NumericLiteralCompletion extends Completion
    {
        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(currentText, null);
        }

        @Override
        public ShowStatus shouldShow(String input)
        {
            // To allow "+", "-", or "1.", we add zero at the end before parsing:
            try
            {
                return Utility.parseAsOne(input + "0", ExpressionLexer::new, ExpressionParser::new, p -> p.numericLiteral())
                    != null ? ShowStatus.PHANTOM : ShowStatus.NO_MATCH;
            }
            catch (InternalException | UserException e)
            {
                return ShowStatus.NO_MATCH;
            }
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return false;
        }

        @Override
        public boolean features(String curInput, int character)
        {
            final String possible;
            if (curInput.isEmpty())
                possible = "0123456789+-._";
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
    
    private static class NestedLiteralCompletion extends SimpleCompletion
    {
        private final String opener;
        
        private NestedLiteralCompletion(String opener)
        {
            super(opener, null);
            this.opener = opener;
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
        }
    }

    private static class KeywordCompletion extends SimpleCompletion
    {
        private final Keyword keyword;

        private KeywordCompletion(Keyword keyword)
        {
            super(keyword.getContent(), null);
            this.keyword = keyword;
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
        }

        public Stream<SingleLoader<Expression, ExpressionSaver>> load()
        {
            return Stream.of(GeneralExpressionEntry.load(keyword));
        }
    }

    private static class OperatorCompletion extends SimpleCompletion
    {
        private final Op operator;

        private OperatorCompletion(Op operator)
        {
            super(operator.getContent(), null);
            this.operator = operator;
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
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
        public ShowStatus shouldShow(String input)
        {
            ShowStatus fallback = ShowStatus.NO_MATCH;
            for (String possible : ImmutableList.of(getScopedName(), tagInfo.getTagInfo().getName()))
            {
                if (possible.equals(input))
                    return ShowStatus.DIRECT_MATCH;
                else if (possible.startsWith(input))
                    fallback = ShowStatus.START_DIRECT_MATCH;
            }
            
            return fallback;
        }

        @NonNull
        private String getScopedName()
        {
            return tagInfo.getTypeName().getRaw() + ":" + tagInfo.getTagInfo().getName();
        }

        @Override
        public boolean features(String curInput, int character)
        {
            // Important to check type first.  If type is same as tag,
            // type will permit more characters to follow than tag alone:
            if (tagInfo.getTypeName().getRaw().startsWith(curInput))
            {
                // It is part/all of the type, what's left is colon
                return character == ':' || Utility.containsCodepoint(tagInfo.getTagInfo().getName(), character);
            }
            else if (getScopedName().startsWith(curInput))
            {
                // Since type name didn't start with input, this must include middle
                // colon and then some:
                return Utility.containsCodepoint(getScopedName().substring(curInput.length()), character);
            }
            else if (tagInfo.getTagInfo().getName().startsWith(curInput))
            {
                return Utility.containsCodepoint(tagInfo.getTagInfo().getName().substring(curInput.length()), character);
            }
            return false;
        }
    }

    private class CompletionListener extends SimpleCompletionListener<Completion>
    {
        @Override
        protected String selected(String currentText, @Interned @Nullable Completion c, String rest, boolean moveFocus)
        {
            savePrefix = null;
            prefix.setText("");
            
            final String newText;
            
            if (c instanceof KeyShortcutCompletion)
            {
                @Interned KeyShortcutCompletion ksc = (@Interned KeyShortcutCompletion) c;
                if (ksc == stringCompletion)
                {
                    parent.replace(GeneralExpressionEntry.this, focusWhenShown(new StringLiteralNode("", parent)));
                }
                return textField.getText();
            }
            else if (c == typeLiteralCompletion)
            {
                parent.replace(GeneralExpressionEntry.this, focusWhenShown(new TypeLiteralNode(parent, null)));
                return textField.getText();
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
                newText = ((KeywordCompletion) c).keyword.getContent();
            }
            else if (c instanceof OperatorCompletion)
            {
                newText = ((OperatorCompletion) c).operator.getContent();
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
                setPrefixColumn(cc.columnReference);
                newText = cc.columnReference.getColumnId().getRaw();
            }
            else if (c == varDeclCompletion)
            {
                newText = varDeclCompletion.getVarName(currentText);
            }
            else if (c instanceof NumericLiteralCompletion)
            {
                newText = currentText;
            }
            else if (c == null || c instanceof SimpleCompletion)
            {
                if (c instanceof SimpleCompletion)
                {
                    newText = ((SimpleCompletion)c).completion;
                }
                else // Numeric literal or unfinished:
                {
                    prefix.setText("");
                    newText = currentText;
                }
            }
            else
            {
                Log.logStackTrace("Unsupported completion: " + c.getClass());
                return textField.getText();
            }

            completing = true;
            // Must do this while completing so that we're not marked as blank:
            if (moveFocus)
            {
                if (rest.isEmpty())
                    parent.focusRightOf(GeneralExpressionEntry.this, Focus.LEFT);
                else
                    parent.addOperandToRight(GeneralExpressionEntry.this, rest, true);
            }
            return newText;
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

        @Override
        protected boolean isFocused()
        {
            return GeneralExpressionEntry.this.isFocused();
        }
    }
    
    private static boolean isRoundBracket(ConsecutiveChild<Expression, ExpressionSaver> item)
    {
        return item instanceof GeneralExpressionEntry && ((GeneralExpressionEntry)item).textField.getText().equals(Keyword.OPEN_ROUND.getContent());
    }

    private static Stream<SingleLoader<Expression, ExpressionSaver>> loadEmptyRoundBrackets()
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

    private class AddUnitCompletion extends SimpleCompletion
    {
        public AddUnitCompletion()
        {
            super("{", TranslationUtility.getString("expression.autocomplete.units"));
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
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
        public ShowStatus shouldShow(String input)
        {
            return input.startsWith("$") && (input.length() == "$".length() || Character.isLetter(input.codePointAt("$".length())))
                ? ShowStatus.PHANTOM : ShowStatus.NO_MATCH;
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
        }

        @Override
        public boolean features(String curInput, int character)
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

    /**
     * An Op, unlike a Keyword, may have a longer alternative available, so should not
     * complete on direct match (unless it is the only possible direct match).
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
        typeLabel.setText(savePrefix.getFirst());
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
        typeLabel.setText(savePrefix.getFirst());
    }

    public static SingleLoader<Expression, ExpressionSaver> load(Op value)
    {
        return load(value.getContent());
    }

    public static SingleLoader<Expression, ExpressionSaver> load(Keyword value)
    {
        return load(value.getContent());
    }

    /**
     * The difference between a Keyword and Op is that a Keyword is never a prefix of a longer
     * item, and thus always completes immediately when directly matched.
     */
    public static enum Keyword
    {
        OPEN_SQUARE("["), CLOSE_SQUARE("]"), OPEN_ROUND("("), CLOSE_ROUND(")"), ANYTHING("@anything"), QUEST("?"),
        IF("@if"), THEN("@then"), ELSE("@else"), ENDIF("@endif"),
        MATCH("@match"), CASE("@case"), ORCASE("@orcase"), GIVEN("@given"), ENDMATCH("@endmatch");

        private final String keyword;

        private Keyword(String keyword)
        {
            this.keyword = keyword;
        }

        public String getContent()
        {
            return keyword;
        }
    }

    @Override
    public boolean availableForFocus()
    {
        return textField.isEditable();
    }

    private void setPseudoClass(GeneralPseudoclass selected)
    {
        for (GeneralPseudoclass pseudoclass : GeneralPseudoclass.values())
        {
            FXUtility.setPseudoclass(textField, pseudoclass.name, pseudoclass == selected);
        }
    }

    @Override
    public void save(ExpressionSaver saver)
    {
        String text = textField.getText().trim();
        textField.setEditable(true);
        setPseudoClass(GeneralPseudoclass.STANDARD);
        
        if (text.isEmpty())
            return; // Don't save blanks

        for (Op op : Op.values())
        {
            if (op.getContent().equals(text))
            {
                saver.saveOperator(op, this, this::afterSave);
                if (!textField.isFocused())
                    textField.setEditable(false);
                setPseudoClass(GeneralPseudoclass.OP);
                return;
            }
        }

        for (Keyword keyword : Keyword.values())
        {
            if (keyword.getContent().equals(text))
            {
                saver.saveKeyword(keyword, this, this::afterSave);
                if (!textField.isFocused())
                    textField.setEditable(false);
                setPseudoClass(GeneralPseudoclass.KEYWORD);
                return;
            }
        }
                
        Optional<@Value Number> number = Utility.parseNumberOpt(text);
        if (number.isPresent())
        {
            saver.saveOperand(new NumericLiteral(number.get(), null), this, this, this::afterSave);
        }
        else if (text.startsWith("$"))
        {
            saver.saveOperand(new VarDeclExpression(text.substring(1)), this, this, this::afterSave);
        }
        else if (text.equals("true") || text.equals("false"))
        {
            saver.saveOperand(new BooleanLiteral(text.equals("true")), this, this, this::afterSave);
        }
        else
        {
            if (savePrefix != null)
                saver.saveOperand(savePrefix.getSecond().apply(text), this, this, this::afterSave);
            else
                saver.saveOperand(new IdentExpression(text), this, this, this::afterSave);
        }
        
    }
}
