package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeId;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.InvalidOperatorUnitExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TaggedTypeNameExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypePrimitiveLiteral;
import records.transformations.expression.type.TypeSaver;
import records.transformations.expression.type.UnfinishedTypeExpression;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.stream.Stream;

public class TypeEntry extends GeneralOperandEntry<TypeExpression, TypeSaver> implements EEDisplayNodeParent
{
    // Number is not included as that is done separately:
    private static final ImmutableList<DataType> PRIMITIVE_TYPES = ImmutableList.of(
        DataType.BOOLEAN,
        DataType.TEXT,
        DataType.NUMBER,
        DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
        DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)),
        DataType.date(new DateTimeInfo(DateTimeType.DATETIME)),
        DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)),
        DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY))
        //DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED))
    );
    private final TypeCompletion bracketCompletion = new TypeCompletion("(", 0);
    private final TypeCompletion listCompletion = new TypeCompletion("[", 0);
    private final TypeCompletion unitBracketCompletion = new TypeCompletion("{", 0);
    private final ImmutableList<TypeCompletion> allCompletions;

    /**
     * An optional component appearing after the text field, for specifying units.
     * Surrounded by curly brackets.
     */
    private @Nullable UnitCompoundBase unitSpecifier;

    public TypeEntry(ConsecutiveBase<TypeExpression, TypeSaver> parent, String initialContent)
    {
        super(TypeExpression.class, parent);
        this.allCompletions = Utility.concatStreams(
            Stream.of(listCompletion, bracketCompletion, unitBracketCompletion),
            PRIMITIVE_TYPES.stream().map(d -> new TypeCompletion(d.toString(), 0)),
            parent.getEditor().getTypeManager().getKnownTaggedTypes().values().stream().map(t -> new TypeCompletion(t.getTaggedTypeName().getRaw(), t.getTypeArguments().size()))
        ).collect(ImmutableList.toImmutableList());
        
        FXUtility.sizeToFit(textField, 30.0, 30.0);
        this.autoComplete = new AutoComplete<TypeCompletion>(textField, Utility.later(this)::calculateCompletions, Utility.later(this).getListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, TypeExpressionOps::differentAlphabet);

        updateNodes();
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), text -> {
            parent.changed(this);
        });
        textField.setText(initialContent);
    }

    private Stream<TypeCompletion> calculateCompletions(String s, CompletionQuery completionQuery)
    {
        return allCompletions.stream().filter(c -> c.completion.toLowerCase().startsWith(s.toLowerCase()));
    }

    private CompletionListener<TypeCompletion> getListener()
    {
        return new SimpleCompletionListener<TypeCompletion>()
        {
            @Override
            protected @Nullable String selected(String currentText, @Nullable TypeCompletion typeCompletion, String rest)
            {
                @Nullable String keep = null;

                if (typeCompletion == listCompletion)
                {
                    parent.replace(TypeEntry.this, loadEmptyBrackets(Keyword.OPEN_SQUARE, Keyword.CLOSE_SQUARE));
                    return null;
                }
                else if (typeCompletion == bracketCompletion)
                {
                    parent.replace(TypeEntry.this, loadEmptyBrackets(Keyword.OPEN_ROUND, Keyword.CLOSE_ROUND));
                    return null;
                }
                else if (typeCompletion == unitBracketCompletion)
                {
                    SingleLoader<TypeExpression, TypeSaver> load = p -> new UnitLiteralTypeNode(p, new InvalidOperatorUnitExpression(ImmutableList.of()));
                    parent.replace(TypeEntry.this, Stream.of(load.focusWhenShown()));
                }
                else if (typeCompletion != null && typeCompletion.numTypeParams > 0)
                {
                    // Should we auto-add operator ready for type-argument?
                    keep = typeCompletion.completion;
                }
                else if (typeCompletion != null)
                {
                    keep = typeCompletion.completion;
                }
                else
                {
                    keep = null;
                }
                
                boolean moveFocus = true;

                completing = true;
                // Must do this while completing so that we're not marked as blank:
                if (moveFocus)
                {
                    if (rest.isEmpty())
                        parent.focusRightOf(TypeEntry.this, Focus.LEFT);
                    else
                        parent.addOperandToRight(TypeEntry.this, rest, true);
                }
                
                return keep;
            }

            @Override
            public @Nullable String focusLeaving(String currentText, @Nullable TypeCompletion selectedItem)
            {
                return currentText;
            }

            @Override
            public void tabPressed()
            {
                parent.focusRightOf(TypeEntry.this, Focus.LEFT);
            }
        };
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
        return Stream.of();
    }

    @Override
    public TopLevelEditor<?, ?> getEditor()
    {
        return parent.getEditor();
    }

    private class TypeCompletion extends SimpleCompletion
    {
        private final int numTypeParams;

        protected TypeCompletion(String completion, int numTypeParams)
        {
            super(completion, null);
            this.numTypeParams = numTypeParams;
        }
    }


    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.concat(Stream.of(textField), unitSpecifier == null ? Stream.empty() : unitSpecifier.nodes().stream());
    }
    
    @Override
    public void save(TypeSaver typeSaver)
    {
        String content = textField.getText().trim();
        for (Keyword keyword : Keyword.values())
        {
            if (content.equals(keyword.getContent()))
            {
                typeSaver.saveKeyword(keyword, this, c -> {});
                return;
            }
        }

        for (DataType primitiveType : PRIMITIVE_TYPES)
        {
            if (content.equals(primitiveType.toString()))
            {
                typeSaver.saveOperand(new TypePrimitiveLiteral(primitiveType), this, c -> {});
                return;
            }
        }

        for (TypeId typeId : parent.getEditor().getTypeManager().getKnownTaggedTypes().keySet())
        {
            if (typeId.getRaw().equals(content))
            {
                typeSaver.saveOperand(new TaggedTypeNameExpression(typeId), this, c -> {});
                return;
            }
        }
        
        // Fallback:
        typeSaver.saveOperand(new UnfinishedTypeExpression(content), this, c -> {});
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
                unitSpecifier = focusWhenShown(new UnitCompoundBase(this, true, null));
            else
                unitSpecifier = focusWhenShown(new UnitCompoundBase(this, true, unitExpression.loadAsConsecutive(BracketedStatus.TOP_LEVEL)));
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
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return Utility.streamNullable(unitSpecifier);
    }

    private class UnitCompletion extends TypeCompletion
    {
        public UnitCompletion()
        {
            super("{", 0);
        }

        @Override
        public ShowStatus shouldShow(String input)
        {
            return input.equalsIgnoreCase("number") ? ShowStatus.EXTENSION : ShowStatus.NO_MATCH;
        }
    }
    
    public static SingleLoader<TypeExpression, TypeSaver> load(String value)
    {
        return p -> new TypeEntry(p, value);
    }

    private static Stream<SingleLoader<TypeExpression, TypeSaver>> loadEmptyBrackets(Keyword open, Keyword close)
    {
        return Stream.of(load(open), load("").focusWhenShown(), load(close));
    }

    public static SingleLoader<TypeExpression, TypeSaver> load(Keyword value)
    {
        return load(value.getContent());
    }
    
    public static enum Keyword
    {
        OPEN_ROUND("("), CLOSE_ROUND(")"), OPEN_SQUARE("["), CLOSE_SQUARE("]"), COMMA(",");

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
}
