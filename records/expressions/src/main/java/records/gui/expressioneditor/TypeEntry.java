package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeId;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.Completion.ShowStatus;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.InvalidSingleUnitExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypePrimitiveLiteral;
import records.transformations.expression.type.TypeSaver;
import records.transformations.expression.type.IdentTypeExpression;
import utility.Either;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.OptionalInt;
import java.util.stream.Stream;

public final class TypeEntry extends GeneralOperandEntry<TypeExpression, TypeSaver> implements EEDisplayNodeParent
{
    // Number is not included as that is done separately:
    private static final ImmutableList<Pair<DataType, @LocalizableKey String>> PRIMITIVE_TYPES = ImmutableList.of(
        _t(DataType.BOOLEAN, "type.entry.boolean.short"),
        _t(DataType.TEXT, "type.entry.text.short"),
        _t(DataType.NUMBER,"type.entry.number.short"),
        _t(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), "type.entry.ymd.short"),
        _t(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), "type.entry.ym.short"),
        _t(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)), "type.entry.datetime.short"),
        _t(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), "type.entry.datetimezoned.short"),
        _t(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), "type.entry.time.short")
        //DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED))
    );
    private static Pair<DataType, @LocalizableKey String> _t(DataType dataType, @LocalizableKey String key)
    {
        return new Pair<>(dataType, key);
    }
    
    
    private final TypeCompletion bracketCompletion = new BracketCompletion("(", "type.entry.tuple.short");
    private final TypeCompletion listCompletion = new BracketCompletion("[", "type.entry.list.short");
    private final TypeCompletion unitBracketCompletion = new UnitCompletion();
    private final Completion endCompletion = new BracketCompletion("}", "type.entry.type.end.short"); //"autocomplete.end");
    private final Completion endBracketCompletion = new BracketCompletion(")", "type.entry.tuple.end.short"); //"autocomplete.end");
    private final Completion endListCompletion = new BracketCompletion("]", "type.entry.list.end.short"); //"autocomplete.end");
    private final ImmutableList<Completion> allCompletions;

    /**
     * An optional component appearing after the text field, for specifying units.
     * Surrounded by curly brackets.
     */
    private @Nullable UnitCompoundBase unitSpecifier;

    public TypeEntry(ConsecutiveBase<TypeExpression, TypeSaver> parent, String initialContent)
    {
        super(TypeExpression.class, parent);
        this.allCompletions = Utility.<Completion>concatStreams(
            Stream.<Completion>of(listCompletion, bracketCompletion, unitBracketCompletion, endBracketCompletion, endListCompletion),
            Stream.<Completion>of(endCompletion), // TODO hide this if we are not last in a type
            PRIMITIVE_TYPES.stream().<Completion>map(d -> new TypeCompletion(d.getFirst().toString(), d.getSecond(), d.getFirst().equals(DataType.NUMBER) || d.getFirst().equals(DataType.TEXT))),
            parent.getEditor().getTypeManager().getKnownTaggedTypes().values().stream()
                // Don't show phantom types like Void, Unit:
                .filter(t -> !t.getTags().isEmpty())
                .<Completion>map(t -> new TypeCompletion(t.getTaggedTypeName().getRaw(), null))
        ).collect(ImmutableList.<Completion>toImmutableList());
        
        this.autoComplete = new AutoComplete<Completion>(textField, Utility.later(this)::calculateCompletions, Utility.later(this).getListener(), () -> parent.showCompletionImmediately(this), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, TypeExpressionOps::differentAlphabet);

        updateNodes();
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), text -> {
            completing = false;
            parent.changed(this);
        });
        autoComplete.setContentDirect(initialContent);
    }

    private Stream<Completion> calculateCompletions(String s, CompletionQuery completionQuery)
    {
        return allCompletions.stream().filter(c -> c.shouldShow(s) != ShowStatus.NO_MATCH);
    }

    private CompletionListener<Completion> getListener()
    {
        return new SimpleCompletionListener<Completion>()
        {
            @Override
            protected @Nullable String selected(String currentText, @Nullable Completion typeCompletion, String rest, OptionalInt positionCaret)
            {
                @Nullable String keep = null;

                if (typeCompletion == endCompletion)
                {
                    if (positionCaret.isPresent())
                    {
                        parent.parentFocusRightOfThis(Either.right(positionCaret.getAsInt()), false);
                        // Don't move focus again:
                        positionCaret = OptionalInt.empty();
                    }
                    keep = "";
                }
                else if (typeCompletion == listCompletion)
                {
                    parent.replace(TypeEntry.this, loadBrackets(Keyword.OPEN_SQUARE, rest, Keyword.CLOSE_SQUARE));
                    return null;
                }
                else if (typeCompletion == bracketCompletion)
                {
                    parent.replace(TypeEntry.this, loadBrackets(Keyword.OPEN_ROUND, rest, Keyword.CLOSE_ROUND));
                    return null;
                }
                else if (typeCompletion == unitBracketCompletion)
                {
                    SingleLoader<TypeExpression, TypeSaver> load = p -> new UnitLiteralTypeNode(p, new InvalidSingleUnitExpression(rest));
                    parent.replace(TypeEntry.this, Stream.of(load.focusWhenShown()));
                    return null;
                }
                else if (typeCompletion == endBracketCompletion || typeCompletion == endListCompletion)
                {
                    // TODO check operator to right is actually a bracket
                    if ((typeCompletion == endBracketCompletion && parent.balancedBrackets(BracketBalanceType.ROUND)) || (typeCompletion == endListCompletion && parent.balancedBrackets(BracketBalanceType.SQUARE)))
                    {
                        keep = "";
                        positionCaret = OptionalInt.of(0);
                        completing = false;
                    }
                    else
                    {
                        completing = true;
                        keep = typeCompletion == endBracketCompletion ? ")" : "]";
                    } 
                }
                /*
                else if (typeCompletion != null && typeCompletion.numTypeParams > 0)
                {
                    // Should we auto-add operator ready for type-argument?
                    keep = typeCompletion.completion;
                }
                */
                else if (typeCompletion != null && typeCompletion instanceof TypeCompletion)
                {
                    completing = true;
                    keep = ((TypeCompletion)typeCompletion).completion;
                }
                else
                {
                    keep = null;
                }

                // Must do this while completing so that we're not marked as blank:
                if (positionCaret.isPresent())
                {
                    if (rest.isEmpty())
                        parent.focusRightOf(TypeEntry.this, Either.right(positionCaret.getAsInt()), false);
                    else
                        parent.addOperandToRight(TypeEntry.this, rest);
                }
                return keep;
            }

            @Override
            public @Nullable String focusLeaving(String currentText, @Nullable Completion selectedItem)
            {
                return currentText;
            }

            @Override
            public void tabPressed()
            {
                parent.focusRightOf(TypeEntry.this, Focus.LEFT, true);
            }

            @Override
            protected boolean isFocused()
            {
                return TypeEntry.this.isFocused();
            }
        };
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child, Either<Focus, Integer> position, boolean becauseOfTab)
    {
        // Child is bound to be units:
        parent.focusRightOf(this, position, becauseOfTab);
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
        return Stream.of();
    }

    @Override
    public TopLevelEditor<?, ?> getEditor()
    {
        return parent.getEditor();
    }

    private class TypeCompletion extends SimpleCompletion
    {
        private final boolean showAtTop;

        protected TypeCompletion(String completion, @LocalizableKey @Nullable String shortKey, boolean showAtTop)
        {
            super(completion, shortKey == null ? null : TranslationUtility.getString(shortKey));
            this.showAtTop = showAtTop;
        }

        protected TypeCompletion(String completion, @LocalizableKey @Nullable String shortKey)
        {
            this(completion, shortKey, false);
        }

        @Override
        public String getDisplaySortKey(String text)
        {
            // Leading space will make it sort to top:
            return (showAtTop ? " " : "") + super.getDisplaySortKey(text);
        }
    }


    @Override
    protected Stream<Node> calculateNodes(@UnknownInitialization(DeepNodeTree.class) TypeEntry this)
    {
        return Stream.<Node>concat(Utility.<Node>streamNullable(container), unitSpecifier == null ? Stream.<Node>empty() : unitSpecifier.nodes().stream());
    }
    
    @Override
    public void save(TypeSaver typeSaver)
    {
        String content = textField.getText().trim();

        if (content.isEmpty())
            return; // Don't save blanks
        
        for (Keyword keyword : Keyword.values())
        {
            if (content.equals(keyword.getContent()))
            {
                typeSaver.saveKeyword(keyword, this, c -> {});
                return;
            }
        }

        for (Operator operator : Operator.values())
        {
            if (content.equals(operator.getContent()))
            {
                typeSaver.saveOperator(operator, this, c -> {});
                return;
            }
        }

        for (Pair<DataType, @LocalizableKey String> pair : PRIMITIVE_TYPES)
        {
            DataType primitiveType = pair.getFirst();
            if (content.equals(primitiveType.toString()))
            {
                if (primitiveType.equals(DataType.NUMBER))
                    typeSaver.saveOperand(new NumberTypeExpression(null), this, this, c -> {});
                else
                    typeSaver.saveOperand(new TypePrimitiveLiteral(primitiveType), this, this, c -> {});
                return;
            }
        }
        
        // Fallback:
        typeSaver.saveOperand(InvalidIdentTypeExpression.identOrUnfinished(content), this, this, c -> {});
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return child == this;
    }

    public void addUnitSpecifier(@Nullable UnitExpression unitExpression)
    {
        if (unitSpecifier == null)
        {
            if (unitExpression == null)
                unitSpecifier = focusWhenShown(new UnitCompoundBase(this, true, this, null));
            else
                unitSpecifier = focusWhenShown(new UnitCompoundBase(this, true, this, unitExpression.loadAsConsecutive(BracketedStatus.TOP_LEVEL)));
        }
        updateNodes();
        updateListeners();
    }

    @Override
    public void focusChanged()
    {
        if (unitSpecifier != null)
            unitSpecifier.focusChanged();
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.RIGHT && unitSpecifier != null)
            unitSpecifier.focus(Focus.RIGHT);
        else
        {
            super.focus(side);
            /*
            FXUtility.onceTrue(initialContentEntered, () -> {
                // Only if we haven't lost focus in the mean time, adjust ours:
                if (isFocused())
                    super.focus(side);
            });
            */
        }
    }
    
    @Override
    protected Stream<EEDisplayNode> calculateChildren(@UnknownInitialization(DeepNodeTree.class) TypeEntry this)
    {
        return Utility.streamNullable(unitSpecifier);
    }

    private class UnitCompletion extends TypeCompletion
    {
        public UnitCompletion()
        {
            super("{", "type.entry.unit.short");
        }
        
        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
        }
    }
    
    public static SingleLoader<TypeExpression, TypeSaver> load(String value)
    {
        return p -> new TypeEntry(p, value);
    }

    private static Stream<SingleLoader<TypeExpression, TypeSaver>> loadBrackets(Keyword open, String content, Keyword close)
    {
        return Stream.of(load(open), load(content).focusWhenShown(), load(close));
    }

    public static SingleLoader<TypeExpression, TypeSaver> load(Keyword value)
    {
        return load(value.getContent());
    }

    @Override
    public boolean availableForFocus()
    {
        for (Keyword keyword : Keyword.values())
        {
            if (keyword.getContent().equals(textField.getText()))
                return false;
        }
        for (Operator operator : Operator.values())
        {
            if (operator.getContent().equals(textField.getText()))
                return false;
        }
        return true;
    }

    public static enum Keyword
    {
        OPEN_ROUND("("), CLOSE_ROUND(")"), OPEN_SQUARE("["), CLOSE_SQUARE("]");

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
    
    public static enum Operator
    {
        COMMA(",");

        private final String op;

        private Operator(String op)
        {
            this.op = op;
        }

        public String getContent()
        {
            return op;
        }
    }

    private class BracketCompletion extends TypeCompletion
    {
        public BracketCompletion(String s, @LocalizableKey String shortKey)
        {
            super(s, shortKey);
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
        }
    }
}
