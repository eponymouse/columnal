package records.gui.expressioneditor;

import annotation.recorded.qual.UnknownIfRecorded;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import log.Log;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
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
import records.gui.expressioneditor.GeneralExpressionEntry.GeneralValue;
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
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Anything which fits in a normal text field without special structure, that is:
 *   - Column reference
 *   - Numeric literal
 *   - Boolean literal
 *   - Partial function name (until later transformed to function call)
 *   - Variable reference.
 */
public class GeneralExpressionEntry extends GeneralOperandEntry<Expression, ExpressionSaver, GeneralValue> implements ConsecutiveChild<Expression, ExpressionSaver>, ErrorDisplayer<Expression, ExpressionSaver>
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
    
    // A value object:
    public static interface GeneralValue extends GeneralOperandEntry.OperandValue
    {
        public String getContent();

        default @Nullable GeneralPseudoclass getPseudoclass() {return null;};

        default String getTypeLabel(boolean focused) {return "";};
        
        public void save(ExpressionSaver saver, GeneralExpressionEntry gee);
    }
    
    public abstract static class GeneralOperand implements GeneralValue
    {
        @Override
        public final void save(ExpressionSaver saver, GeneralExpressionEntry gee)
        {
            saver.saveOperand(saveUnrecorded(saver), gee, this::afterSave);
        }

        public abstract @UnknownIfRecorded Expression saveUnrecorded(ErrorAndTypeRecorder onError);
        
        public void afterSave(Context context) {}
    }
    
    public static class Unfinished extends GeneralOperand
    {
        private final String value;

        public Unfinished(String value)
        {
            this.value = value;
        }

        @Override
        public String getContent()
        {
            return value;
        }

        @Override
        public @Nullable GeneralPseudoclass getPseudoclass()
        {
            return GeneralPseudoclass.UNFINISHED;
        }

        @Override
        public String getTypeLabel(boolean focused)
        {
            return focused ? "" : "error";
        }

        @Override
        public @UnknownIfRecorded Expression saveUnrecorded(ErrorAndTypeRecorder onError)
        {
            IdentExpression identExpression = new IdentExpression(value.trim());
            onError.recordError(identExpression, StyledString.concat(StyledString.s("Invalid expression: "), identExpression.toStyledString()));
            return identExpression;
        }
    }


    public static class StdFunc extends GeneralOperand
    {
        private final FunctionDefinition function;

        public StdFunc(FunctionDefinition function)
        {
            this.function = function;
        }

        @Override
        public String getContent()
        {
            return function.getName();
        }

        @Override
        public @Nullable GeneralPseudoclass getPseudoclass()
        {
            return GeneralPseudoclass.FUNCTION;
        }

        @Override
        public String getTypeLabel(boolean focused)
        {
            return "function";
        }

        @Override
        public @UnknownIfRecorded Expression saveUnrecorded(ErrorAndTypeRecorder onError)
        {
            return new StandardFunction(function);
        }
    }
    
    public static class TagName extends GeneralOperand
    {
        private final TagInfo tagInfo;

        public TagName(TagInfo tagInfo)
        {
            this.tagInfo = tagInfo;
        }

        @Override
        public String getContent()
        {
            return tagInfo.getTagInfo().getName();
        }

        @Override
        public @Nullable GeneralPseudoclass getPseudoclass()
        {
            return GeneralPseudoclass.TAG;
        }

        @Override
        public String getTypeLabel(boolean focused)
        {
            return tagInfo.wholeType.getTaggedTypeName().getRaw() + "\\";
        }

        @Override
        public @UnknownIfRecorded Expression saveUnrecorded(ErrorAndTypeRecorder onError)
        {
            return new ConstructorExpression(Either.right(tagInfo));
        }
    }

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

    GeneralExpressionEntry(GeneralValue initialValue, ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        this(Either.right(initialValue), parent);
    }
    
    // If initial value is String, it was user entered.  If GeneralValue, we trust it.
    GeneralExpressionEntry(Either<String, GeneralValue> initialValue, ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        super(Expression.class, parent, initialValue.either(s -> new Unfinished(s), v -> v));
        stringCompletion = new KeyShortcutCompletion("autocomplete.string", '\"');
        unitCompletion = new AddUnitCompletion();
        typeLiteralCompletion = new KeyShortcutCompletion("autocomplete.type", '`');
        varDeclCompletion = new VarDeclCompletion();
        initialValue.ifRight(v -> {
            textField.setText(v.getContent()); // Do before auto complete is on the field
            initialContentEntered.set(true);
        });
        updateNodes();

        this.autoComplete = new AutoComplete<Completion>(textField, this::getSuggestions, new CompletionListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, c -> !Character.isAlphabetic(c) && (parent.operations.isOperatorAlphabet(c) || parent.terminatedByChars().contains(c)));

        updateGraphics();
        FXUtility.addChangeListenerPlatformNN(currentValue, v -> updateGraphics());
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), focus -> {
            updateGraphics();
        });
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), t -> {
            if (!completing)
            {
                currentValue.set(new Unfinished(t));
            }
            completing = false;
            parent.changed(GeneralExpressionEntry.this);
        });
        initialValue.ifLeft(s -> {
            // Do this after auto-complete is set up and we are set as part of parent,
            // in case it finishes a completion:
            FXUtility.runAfter(() -> {
                textField.setText(s);
                initialContentEntered.set(true);
            });
        });
    }

    @RequiresNonNull({"container", "textField", "typeLabel", "currentValue"})
    private void updateGraphics(@UnknownInitialization(Object.class) GeneralExpressionEntry this)
    {
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
        r.add(new SimpleCompletion("", "true", null, new Lit(new BooleanLiteral(true))));
        r.add(new SimpleCompletion("", "false", null, new Lit(new BooleanLiteral(false))));
        for (ColumnReference column : Utility.iterableStream(parent.getEditor().getAvailableColumnReferences()))
        {
            if (column.getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW)
            {
                r.add(new SimpleCompletion(ARROW_SAME_ROW, column.getColumnId().getRaw(), "autocomplete.column.sameRow", new ColumnRef(column)));
            }
            else
            {
                r.add(new SimpleCompletion(ARROW_WHOLE, "@entire " + column.getColumnId().getRaw(), "autocomplete.column.entire", new ColumnRef(column)));
            }
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
            r.add(new FunctionCompletion(function, parent.getEditor().getTypeManager().getUnitManager()));
        }
    }

    private static abstract class GeneralCompletion extends Completion
    {
        abstract GeneralValue getValue(String currentText);
    }

    private static class SimpleCompletion extends GeneralCompletion
    {
        private final String prefix;
        private final String text;
        private final @Nullable @LocalizableKey String descriptionKey;
        private final GeneralValue value;

        public SimpleCompletion(String prefix, String text, @Nullable @LocalizableKey String descriptionKey, GeneralValue value)
        {
            this.prefix = prefix;
            this.descriptionKey = descriptionKey;
            this.text = text;
            this.value = value;
        }


        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(prefix + text, descriptionKey == null ? null : TranslationUtility.getString(descriptionKey));
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

        @Override
        GeneralValue getValue(String currentText)
        {
            return value;
        }

        // For debugging:
        @Override
        public String toString()
        {
            return "SimpleCompletion{" +
                    "prefix='" + prefix + '\'' +
                    ", text='" + text + '\'' +
                    ", description='" + descriptionKey + '\'' +
                    ", value=" + value +
                    '}';
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
        GeneralValue getValue(String currentText)
        {
            return getNumberOrUnfinished(currentText);
        }
    }

    private GeneralValue getNumberOrUnfinished(String currentText)
    {
        try
        {
            return new NumLit(Utility.parseNumber(currentText.replace("_", "")));
        }
        catch (UserException e)
        {
            Log.log(e);
            return new Unfinished(currentText);
        }
    }

    @Override
    public void save(ExpressionSaver saver)
    {
        currentValue.get().save(saver, this);
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
            return input.equals(keyword) ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return keyword.getContent().startsWith(curInput) && keyword.getContent().substring(curInput.length()).contains("" + character);
        }

        public Stream<SingleLoader<Expression, ExpressionSaver>> load()
        {
            return Stream.of(p -> new GeneralExpressionEntry(keyword, p));
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
            return input.equals(operator) ? (onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT) : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return operator.getContent().startsWith(curInput) && operator.getContent().substring(curInput.length()).contains("" + character);
        }

        public Stream<SingleLoader<Expression, ExpressionSaver>> load()
        {
            return Stream.of(p -> new GeneralExpressionEntry(operator, p));
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
                currentValue.setValue(getNumberOrUnfinished(currentText.replace("{", "")));
                parent.ensureOperandToRight(GeneralExpressionEntry.this,  o -> o instanceof UnitLiteralExpressionNode, () -> Stream.of(p -> {
                    UnitLiteralExpressionNode unitLiteralNode = new UnitLiteralExpressionNode(p, new SingleUnitExpression(rest));
                    unitLiteralNode.focusWhenShown();
                    return unitLiteralNode;
                }));
            }
            else if (c instanceof KeywordCompletion)
            {
                parent.replaceLoad(GeneralExpressionEntry.this, ReplacementTarget.CURRENT, ((KeywordCompletion)c).load());
            }
            else if (c instanceof OperatorCompletion)
            {
                parent.replaceLoad(GeneralExpressionEntry.this, ReplacementTarget.CURRENT, ((OperatorCompletion)c).load());
            }
            else if (c instanceof FunctionCompletion)
            {
                // What to do with rest != "" here? Don't allow? Skip to after args?
                FunctionCompletion fc = (FunctionCompletion)c;
                currentValue.setValue(new StdFunc(fc.function));
                parent.ensureOperandToRight(GeneralExpressionEntry.this, GeneralExpressionEntry::isRoundBracket, () -> loadEmptyRoundBrackets());
                return fc.function.getName();
            }
            else if (c instanceof TagCompletion)
            {
                TagCompletion tc = (TagCompletion)c;
                currentValue.setValue(new TagName(tc.tagInfo));
                
                if (tc.tagInfo.getTagInfo().getInner() != null)
                {
                    parent.ensureOperandToRight(GeneralExpressionEntry.this, GeneralExpressionEntry::isRoundBracket, () -> loadEmptyRoundBrackets());
                }
                else
                {
                    if (moveFocus)
                        parent.focusRightOf(GeneralExpressionEntry.this, Focus.LEFT);
                }
            }
            else if (c == varDeclCompletion)
            {
                completing = true;
                currentValue.setValue(new VarDecl(varDeclCompletion.getVarName(currentText)));
                //parent.setOperatorToRight(GeneralExpressionEntry.this, rest);
                if (moveFocus)
                    parent.focusRightOf(GeneralExpressionEntry.this, Focus.RIGHT);
                return currentText;
            }
            else if (c == null || c instanceof GeneralCompletion)
            {
                @Nullable GeneralCompletion gc = (GeneralCompletion) c;
                completing = true;
                //parent.setOperatorToRight(GeneralExpressionEntry.this, rest);
                currentValue.setValue(gc == null ? new Unfinished(currentText) : gc.getValue(currentText));
                // End of following operator, since we pushed rest into there:
                if (moveFocus)
                
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
        return item instanceof GeneralExpressionEntry && ((GeneralExpressionEntry)item).currentValue.get().equals(Keyword.OPEN_ROUND);
    }

    private Stream<SingleLoader<Expression, ExpressionSaver>> loadEmptyRoundBrackets()
    {
        return Stream.of(load(Keyword.OPEN_ROUND), load(new Unfinished("")).focusWhenShown(), load(Keyword.CLOSE_ROUND));
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
            return input.startsWith(VarDecl.PREFIX) && (input.length() == VarDecl.PREFIX.length() || Character.isLetter(input.codePointAt(VarDecl.PREFIX.length())));
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
            return StringUtils.removeStart(currentText, VarDecl.PREFIX);
        }
    }
    
    public static class VarDecl extends GeneralOperand
    {
        private static final String PREFIX = "$";
        private final String varName; // Without prefix

        public VarDecl(String varName)
        {
            this.varName = varName;
        }


        @Override
        public String getContent()
        {
            return PREFIX + varName;
        }

        @Override
        public @Nullable GeneralPseudoclass getPseudoclass()
        {
            return GeneralPseudoclass.VARIABLE_DECL;
        }

        @Override
        public String getTypeLabel(boolean focused)
        {
            return "named match";
        }

        @Override
        public @UnknownIfRecorded Expression saveUnrecorded(ErrorAndTypeRecorder onError)
        {
            return new VarDeclExpression(varName);
        }
    }
    
    public static class Lit extends GeneralOperand
    {
        private final Literal literal;

        public Lit(Literal literal)
        {
            this.literal = literal;
        }

        @Override
        public String getContent()
        {
            return literal.editString();
        }

        @Override
        public @Nullable GeneralPseudoclass getPseudoclass()
        {
            return GeneralPseudoclass.LITERAL;
        }

        @Override
        public String getTypeLabel(boolean focused)
        {
            return "";
        }

        @Override
        public @UnknownIfRecorded Expression saveUnrecorded(ErrorAndTypeRecorder onError)
        {
            return literal;
        }
    }
    
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

    public static class NumLit extends GeneralOperand
    {
        private final Number number;

        public NumLit(Number number)
        {
            this.number = number;
        }

        @Override
        public String getContent()
        {
            return Utility.numberToString(number);
        }

        @Override
        public @Nullable GeneralPseudoclass getPseudoclass()
        {
            return GeneralPseudoclass.LITERAL;
        }

        @Override
        public String getTypeLabel(boolean focused)
        {
            return "";
        }

        @Override
        public @UnknownIfRecorded Expression saveUnrecorded(ErrorAndTypeRecorder onError)
        {
            return new NumericLiteral(number, null);
        }
    }
    
    public static enum Op implements GeneralValue
    {
        AND("&"), OR("|"), MULTIPLY("*"), ADD("+"), SUBTRACT("-"), DIVIDE("/"), STRING_CONCAT(";"), EQUALS("="), NOT_EQUAL("<>"), PLUS_MINUS("\u00B1"), RAISE("^"),
        COMMA(","),
        LESS_THAN("<"), LESS_THAN_OR_EQUAL("<="), GREATER_THAN(">"), GREATER_THAN_OR_EQUAL(">=");

        private final String op;

        private Op(String op)
        {
            this.op = op;
        }

        @Override
        public String getContent()
        {
            return op;
        }

        @Override
        public void save(ExpressionSaver saver, GeneralExpressionEntry gee)
        {
            saver.saveOperator(this, gee, c -> {});
        }
    }
    
    public static SingleLoader<Expression, ExpressionSaver> load(GeneralValue value)
    {
        return p -> new GeneralExpressionEntry(value, p);
    }
    
    public static enum Keyword implements GeneralValue
    {
        OPEN_SQUARE("["), CLOSE_SQUARE("]"), OPEN_ROUND("("), CLOSE_ROUND(")"), ANYTHING("@anything"), QUEST("?"),
        IF("@if"), THEN("@then"), ELSE("@else"), ENDIF("@endif"),
        MATCH("@match"), CASE("@case"), ORCASE("@orcase"), GIVEN("@given"), ENDMATCH("@endmatch");

        private final String op;

        private Keyword(String op)
        {
            this.op = op;
        }

        @Override
        public String getContent()
        {
            return op;
        }

        @Override
        public void save(ExpressionSaver saver, GeneralExpressionEntry gee)
        {
            saver.saveKeyword(this, gee, c -> {});
        }


    }
}
