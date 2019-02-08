package records.gui.expressioneditor;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.Bindings;
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
import records.data.TableId;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeManager;
import records.data.datatype.TypeManager.TagInfo;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.Completion.ShowStatus;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.gui.expressioneditor.ExpressionSaver.Context;
import records.jellytype.JellyType;
import records.transformations.expression.*;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import utility.Either;
import utility.ExFunction;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Anything which fits in a normal text field without special structure, that is:
 *   - Column reference
 *   - Numeric literal
 *   - Boolean literal
 *   - Partial function name (until later transformed to function call)
 *   - Variable reference.
 */
public final class GeneralExpressionEntry extends GeneralOperandEntry<Expression, ExpressionSaver> implements ConsecutiveChild<Expression, ExpressionSaver>, ErrorDisplayer<Expression, ExpressionSaver>
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

    /** Flag used to monitor when the initial content is set */
    private final SimpleBooleanProperty initialContentEntered = new SimpleBooleanProperty(false);

    private final ImmutableList<ColumnReference> availableColumns;
    
    
    GeneralExpressionEntry(String initialValue, ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        super(Expression.class, parent);
        //Log.logStackTrace("Made new GEE with [[" + initialValue + "]]");
        stringCompletion = new KeyShortcutCompletion("autocomplete.string", '\"');
        unitCompletion = new AddUnitCompletion();
        varDeclCompletion = new VarDeclCompletion();
        availableColumns = parent.getEditor().getAvailableColumnReferences().collect(ImmutableList.<ColumnReference>toImmutableList());

        this.autoComplete = new AutoComplete<Completion>(textField, this::getSuggestions, new CompletionListener(), () -> parent.showCompletionImmediately(this), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, ExpressionOps::requiresNewSlot);

        updateNodes();
        updateGraphics();
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), focus -> {
            updateGraphics();
        });
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), t -> {
            if (!completing)
            {
                prefix.setText("");
            }
            completing = false;
            updateGraphics();
            parent.changed(GeneralExpressionEntry.this);
        });
        autoComplete.setContentDirect(initialValue, false);
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
    protected Stream<Node> calculateNodes(@UnknownInitialization(DeepNodeTree.class) GeneralExpressionEntry this)
    {
        return Utility.streamNullable(container);
    }

    @RequiresNonNull({"unitCompletion", "stringCompletion", "varDeclCompletion", "parent", "availableColumns"})
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
        r.add(new NestedLiteralCompletion("type{", () -> new TypeLiteralNode(parent, null)));
        r.add(new NestedLiteralCompletion("date{", () -> new TemporalLiteralNode(parent, DateTimeType.YEARMONTHDAY, null)));
        r.add(new NestedLiteralCompletion("dateym{", () -> new TemporalLiteralNode(parent, DateTimeType.YEARMONTH, null)));
        r.add(new NestedLiteralCompletion("time{", () -> new TemporalLiteralNode(parent, DateTimeType.TIMEOFDAY, null)));
        r.add(new NestedLiteralCompletion("datetime{", () -> new TemporalLiteralNode(parent, DateTimeType.DATETIME, null)));
        r.add(new NestedLiteralCompletion("datetimezoned{", () -> new TemporalLiteralNode(parent, DateTimeType.DATETIMEZONED, null)));
        
        addAllFunctions(r);
        r.add(new SimpleCompletion("true", null));
        r.add(new SimpleCompletion("false", null));
        for (ColumnReference column : availableColumns)
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
        r.add(new PhantomIdentCompletion());

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
        public CompletionContent makeDisplay(ObservableStringValue currentText)
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
        public CompletionContent makeDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(function.getName() + "(...)", function.getMiniDescription());
        }

        @Override
        public String getDisplaySortKey(String text)
        {
            return function.getName();
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
        public CompletionContent makeDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(currentText, null);
        }

        @Override
        public ShowStatus shouldShow(String input)
        {
            // To allow "1.", we add zero at the end before parsing:
            try
            {
                return !input.isEmpty() && Utility.parseAsOne(input + "0", ExpressionLexer::new, ExpressionParser::new, p -> p.completeNumber())
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
                possible = "0123456789._";
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
    
    private class PhantomIdentCompletion extends Completion
    {
        @Override
        public CompletionContent makeDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(Bindings.createStringBinding(() -> currentText.get().trim(), currentText), null);
        }

        @Override
        public ShowStatus shouldShow(String input)
        {
            return GrammarUtility.validIdentifier(input.trim()) ? ShowStatus.PHANTOM : ShowStatus.NO_MATCH;
        }

        @Override
        public boolean features(String curInput, int character)
        {
            return GrammarUtility.validIdentifier(curInput.trim() + Utility.codePointToString(character));
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
        private final Supplier<ConsecutiveChild<Expression, ExpressionSaver>> makeEmptyLiteral;
        
        private NestedLiteralCompletion(String opener, Supplier<ConsecutiveChild<Expression, ExpressionSaver>> makeEmptyLiteral)
        {
            super(opener, null);
            this.opener = opener;
            this.makeEmptyLiteral = makeEmptyLiteral;
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
            return operator != Op.LESS_THAN && operator != Op.GREATER_THAN;
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
        public CompletionContent makeDisplay(ObservableStringValue currentText)
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
        protected String selected(String currentText, @Interned @Nullable Completion c, String rest, OptionalInt positionCaret)
        {
            //Log.normalStackTrace("Selected " + currentText, 4);
            prefix.setText("");
            completing = true;
            
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
            else if (c instanceof NestedLiteralCompletion)
            {
                NestedLiteralCompletion nestedLiteralCompletion = (NestedLiteralCompletion)c; 
                parent.replace(GeneralExpressionEntry.this, focusWhenShown(nestedLiteralCompletion.makeEmptyLiteral.get()));
                return textField.getText();
            }
            else if (Objects.equals(c, unitCompletion))
            {
                parent.ensureOperandToRight(GeneralExpressionEntry.this,  o -> o instanceof UnitLiteralExpressionNode, () -> Stream.of(p -> {
                    UnitLiteralExpressionNode unitLiteralNode = new UnitLiteralExpressionNode(p, InvalidSingleUnitExpression.identOrUnfinished(rest));
                    unitLiteralNode.focusWhenShown();
                    return unitLiteralNode;
                }));
                return currentText.replace("{", "");
            }
            else if (c instanceof KeywordCompletion)
            {
                String content = ((KeywordCompletion) c).keyword.getContent();

                @Nullable BracketBalanceType bracketBalanceType = null;
                if (content.equals(")"))
                    bracketBalanceType = BracketBalanceType.ROUND;
                else if (content.equals("]"))
                    bracketBalanceType = BracketBalanceType.SQUARE;
                
                boolean followedByClose = false;
                ImmutableList<ConsecutiveChild<@NonNull Expression, ExpressionSaver>> siblings = parent.getAllChildren();
                int index = Utility.indexOfRef(siblings, GeneralExpressionEntry.this);
                if (index + 1 < siblings.size() && bracketBalanceType != null)
                    followedByClose = siblings.get(index + 1).closesBracket(bracketBalanceType);
                
                if (bracketBalanceType != null && parent.balancedBrackets(bracketBalanceType) && followedByClose)
                {
                    newText = "";
                    positionCaret = OptionalInt.of(0);
                    completing = false;
                }
                else
                {
                    newText = content;
                    textField.setEditable(false);
                }
            }
            else if (c instanceof OperatorCompletion)
            {
                newText = ((OperatorCompletion) c).operator.getContent();
                textField.setEditable(false);
            }
            else if (c instanceof FunctionCompletion)
            {
                // What to do with rest != "" here? Don't allow? Skip to after args?
                FunctionCompletion fc = (FunctionCompletion)c;
                completing = true;
                parent.ensureOperandToRight(GeneralExpressionEntry.this, GeneralExpressionEntry::isRoundBracket, () -> loadEmptyRoundBrackets());
                return fc.function.getName();
            }
            else if (c instanceof TagCompletion)
            {
                TagCompletion tc = (TagCompletion)c;

                // Important to call this before adding brackets:
                completing = true;

                String tagName = tc.tagInfo.getTagInfo().getName();
                if (parent.getEditor().getTypeManager().ambiguousTagName(tagName))
                    newText = tc.tagInfo.getTypeName().getRaw() + ":" + tagName;
                else
                    newText = tagName;
                
                if (tc.tagInfo.getTagInfo().getInner() != null && rest.isEmpty())
                {
                    parent.ensureOperandToRight(GeneralExpressionEntry.this, GeneralExpressionEntry::isRoundBracket, () -> loadEmptyRoundBrackets());
                    return newText;
                }
                
                
            }
            else if (c instanceof ColumnCompletion)
            {
                ColumnCompletion cc = (ColumnCompletion)c;
                newText = cc.fullText;
            }
            else if (c == varDeclCompletion)
            {
                newText = currentText;
            }
            else if (c instanceof NumericLiteralCompletion)
            {
                newText = currentText;
            }
            else if (c == null || c instanceof SimpleCompletion || c instanceof PhantomIdentCompletion)
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

            // Must do this while completing so that we're not marked as blank:
            if (positionCaret.isPresent() && rest.isEmpty())
            {
                parent.focusRightOf(GeneralExpressionEntry.this, Either.right(positionCaret.getAsInt()), false);
            }
            else if (!rest.isEmpty())
            {
                parent.addOperandToRight(GeneralExpressionEntry.this, rest, positionCaret);
            }
            
            if (newText == null)
                completing = false;
            return newText;
        }

        @Override
        public String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
        {
            if (!(selectedItem instanceof KeyShortcutCompletion) && !completing)
            {
                return selected(currentText, selectedItem, "", OptionalInt.empty());
            }
            return currentText;
        }

        @Override
        public void tabPressed()
        {
            parent.focusRightOf(GeneralExpressionEntry.this, Focus.LEFT, true);
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
        public CompletionContent makeDisplay(ObservableStringValue currentText)
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
        return p -> new GeneralExpressionEntry((columnReference.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN ? "@entire " : "") + columnReference.getColumnId().getRaw(), p);
    }

    public static SingleLoader<Expression, ExpressionSaver> load(ConstructorExpression constructorExpression)
    {
        return p -> new GeneralExpressionEntry(
            p.getEditor().getTypeManager().ambiguousTagName(constructorExpression.getName()) && constructorExpression.getTypeName() != null
                ? constructorExpression.getTypeName().getRaw() + ":" + constructorExpression.getName()
                : constructorExpression.getName(), p);
    }

    public static SingleLoader<Expression, ExpressionSaver> load(StandardFunction standardFunction)
    {
        return p -> new GeneralExpressionEntry(standardFunction.getName(), p);
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
        OPEN_SQUARE("["), CLOSE_SQUARE("]"), OPEN_ROUND("("), CLOSE_ROUND(")"), ANYTHING(ExpressionLexer.ANY), QUEST("?"),
        IF(ExpressionLexer.IF), THEN(ExpressionLexer.THEN), ELSE(ExpressionLexer.ELSE), ENDIF(ExpressionLexer.ENDIF),
        MATCH(ExpressionLexer.MATCH),
        CASE(ExpressionLexer.CASE),
        ORCASE(ExpressionLexer.ORCASE),
        GIVEN(ExpressionLexer.CASEGUARD),
        ENDMATCH(ExpressionLexer.ENDMATCH);

        private final String keyword;

        private Keyword(String keyword)
        {
            this.keyword = keyword;
        }

        private Keyword(int token)
        {
            this.keyword = Utility.literal(ExpressionLexer.VOCABULARY, token);
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

        textField.setEditable(true);
                
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
            for (ColumnReference availableColumn : availableColumns)
            {
                TableId tableId = availableColumn.getTableId();
                String columnIdRaw = availableColumn.getColumnId().getRaw();
                if (availableColumn.getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW &&
                        (
                                (tableId == null && columnIdRaw.equals(text))
                                        || (tableId != null && (tableId.getRaw() + ":" + columnIdRaw).equals(text))
                        ))
                {
                    saver.saveOperand(new ColumnReference(availableColumn), this, this, this::afterSave);
                    return;
                }
                else if (availableColumn.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN &&
                        text.startsWith("@entire ") &&
                        ((tableId == null && ("@entire " + columnIdRaw).equals(text))
                                || (tableId != null && ("@entire " + tableId.getRaw() + ":" + columnIdRaw).equals(text))))
                {
                    saver.saveOperand(new ColumnReference(availableColumn), this, this, this::afterSave);
                    return;
                }
            }

            ImmutableList<FunctionDefinition> allFunctions = ImmutableList.of();
            TypeManager typeManager = parent.getEditor().getTypeManager();
            try
            {
                allFunctions = FunctionList.getAllFunctions(typeManager.getUnitManager());
            }
            catch (InternalException e)
            {
                Log.log(e);
            }

            for (FunctionDefinition function : allFunctions)
            {
                if (function.getName().equals(text))
                {
                    saver.saveOperand(new StandardFunction(function), this, this, this::afterSave);
                    return;
                }
            }

            for (TaggedTypeDefinition taggedType : typeManager.getKnownTaggedTypes().values())
            {
                for (TagType<JellyType> tag : taggedType.getTags())
                {
                    if (tag.getName().equals(text) || text.equals(taggedType.getTaggedTypeName().getRaw() + ":" + tag.getName()))
                    {
                        saver.saveOperand(new ConstructorExpression(typeManager, taggedType.getTaggedTypeName().getRaw(), tag.getName()), this, this, this::afterSave);
                        return;
                    }
                }
            }

            saver.saveOperand(InvalidIdentExpression.identOrUnfinished(text), this, this, this::afterSave);
        }
        
    }

    public boolean isOperator()
    {
        String text = textField.getText().trim();
        return Arrays.stream(Op.values()).anyMatch(op -> op.getContent().equals(text));
    }
}
