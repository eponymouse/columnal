package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.UnfinishedExpression;
import records.transformations.expression.UnfinishedUnitExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.TaggedTypeNameExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import records.transformations.expression.type.TypePrimitiveLiteral;
import records.transformations.expression.type.UnfinishedTypeExpression;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class TypeEntry extends GeneralOperandEntry<TypeExpression, TypeParent> implements EEDisplayNodeParent, UnitNodeParent
{
    // Number is not included as that is done separately:
    private static final ImmutableList<DataType> PRIMITIVE_TYPES = ImmutableList.of(
        DataType.BOOLEAN,
        DataType.TEXT,
        DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
        DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)),
        DataType.date(new DateTimeInfo(DateTimeType.DATETIME)),
        DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)),
        DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)),
        DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED))
    );
    private final TypeParent semanticParent;
    private final TypeCompletion bracketCompletion = new TypeCompletion("(", 0);
    private final TypeCompletion listCompletion = new TypeCompletion("[", 0);
    private final TypeCompletion plainNumberCompletion = new TypeCompletion("Number", 0);
    private final TypeCompletion numberWithUnitsCompletion = new TypeCompletion("Number{", 0);
    private final ImmutableList<TypeCompletion> allCompletions;

    /**
     * An optional component appearing after the text field, for specifying units.
     * Surrounded by curly brackets.
     */
    private @Nullable UnitCompoundBase unitSpecifier;

    public TypeEntry(ConsecutiveBase<TypeExpression, TypeParent> parent, TypeParent typeParent, String initialContent)
    {
        super(TypeExpression.class, parent);
        this.semanticParent = typeParent;
        this.allCompletions = Utility.concatStreams(
            Stream.of(listCompletion, bracketCompletion, plainNumberCompletion, numberWithUnitsCompletion),
            PRIMITIVE_TYPES.stream().map(d -> new TypeCompletion(d.toString(), 0)),
            parent.getEditor().getTypeManager().getKnownTaggedTypes().values().stream().map(t -> new TypeCompletion(t.getTaggedTypeName().getRaw(), t.getTypeArguments().size()))
        ).collect(ImmutableList.toImmutableList());
        
        FXUtility.sizeToFit(textField, 30.0, 30.0);
        new AutoComplete<TypeCompletion>(textField, Utility.later(this)::calculateCompletions, Utility.later(this).getListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, c -> parent.operations.isOperatorAlphabet(c, parent.getThisAsSemanticParent()) || parent.terminatedByChars().contains(c));
                
        textField.setText(initialContent);
        updateNodes();
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), text -> {
            parent.changed(this);
        });
    }

    private List<TypeCompletion> calculateCompletions(String s, CompletionQuery completionQuery)
    {
        return allCompletions.stream().filter(c -> c.completion.toLowerCase().startsWith(s.toLowerCase())).collect(ImmutableList.toImmutableList());
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
                    parent.replace(TypeEntry.this, focusWhenShown(new SquareBracketedTypeNode(parent, null)));
                    return null;
                }
                else if (typeCompletion == bracketCompletion)
                {
                    parent.replace(TypeEntry.this, focusWhenShown(new BracketedTypeNode(parent, null)));
                    return null;
                }
                else if (typeCompletion == numberWithUnitsCompletion)
                {
                    if (unitSpecifier == null)
                    {
                        addUnitSpecifier(new UnfinishedUnitExpression(rest));
                    }
                    else
                    {
                        // If it's null and we're at the end, move into it:
                        if (rest.isEmpty())
                            unitSpecifier.focus(Focus.LEFT);
                    }
                    keep = "Number";
                }
                else if (typeCompletion == plainNumberCompletion)
                {
                    if (unitSpecifier != null)
                    {
                        unitSpecifier = null;
                        updateNodes();
                        updateListeners();
                    }
                    keep = "Number";
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
                parent.setOperatorToRight(TypeEntry.this, rest);
                parent.focusRightOf(TypeEntry.this, Focus.RIGHT);
                
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

    @Override
    public UnitManager getUnitManager()
    {
        return getEditor().getTypeManager().getUnitManager();
    }

    private class TypeCompletion extends Completion
    {
        private final String completion;
        private final int numTypeParams;

        protected TypeCompletion(String completion, int numTypeParams)
        {
            this.completion = completion;
            this.numTypeParams = numTypeParams;
        }

        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(completion, null);
        }

        @Override
        public boolean shouldShow(String input)
        {
            return completion.toLowerCase().startsWith(input.toLowerCase());
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return completion.equals(input) ? (onlyAvailableCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT) : CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return completion.contains("" + character);
        }
    }


    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.concat(Stream.of(textField), unitSpecifier == null ? Stream.empty() : unitSpecifier.nodes().stream());
    }
    
    @Override
    public TypeExpression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        // Number is special case:
        String content = textField.getText().trim();
        if (content.equals("Number"))
        {
            if (unitSpecifier != null)
            {
                return new NumberTypeExpression(unitSpecifier.saveUnrecorded(errorDisplayer, onError));
            }
            else
                return new NumberTypeExpression(null);
        }
        
        return Stream.<TypeExpression>concat(
            PRIMITIVE_TYPES.stream()
                .filter(t -> t.toString().equals(content))
                .map(t -> new TypePrimitiveLiteral(t)), 
            parent.getEditor().getTypeManager().getKnownTaggedTypes().keySet().stream()
                .filter(t -> t.getRaw().equals(content))
                .map(t -> new TaggedTypeNameExpression(t))
        ).findFirst().orElseGet(() -> new UnfinishedTypeExpression(textField.getText()));
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
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
                unitSpecifier = focusWhenShown(new UnitCompoundBase(this, true, SingleLoader.withSemanticParent(unitExpression.loadAsConsecutive(true), this)));
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
        public boolean shouldShow(String input)
        {
            return input.equalsIgnoreCase("number");
        }
    }
    
}
