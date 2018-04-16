package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.CompletionQuery;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.UnfinishedExpression;
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

public class TypeEntry extends GeneralOperandEntry<TypeExpression, TypeParent>
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
    private final TypeCompletion listCompletion = new TypeCompletion("[", 0);
    private final ImmutableList<TypeCompletion> allCompletions;

    public TypeEntry(ConsecutiveBase<TypeExpression, TypeParent> parent, TypeParent typeParent, String initialContent)
    {
        super(TypeExpression.class, parent);
        this.semanticParent = typeParent;
        this.allCompletions = Utility.concatStreams(
            Stream.of(listCompletion),
            PRIMITIVE_TYPES.stream().map(d -> new TypeCompletion(d.toString(), 0)),
            parent.getEditor().getTypeManager().getKnownTaggedTypes().values().stream().map(t -> new TypeCompletion(t.getTaggedTypeName().getRaw(), t.getTypeArguments().size()))
        ).collect(ImmutableList.toImmutableList());
        
        FXUtility.sizeToFit(textField, 30.0, 30.0);
        new AutoComplete<TypeCompletion>(textField, Utility.later(this)::calculateCompletions, Utility.later(this).getListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, c -> ",-[](){}".contains("" + c));
                
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
                if (typeCompletion == listCompletion)
                {
                    parent.replace(TypeEntry.this, focusWhenShown(new SquareBracketedTypeNode(parent, null)));
                    return null;
                }
                else if (typeCompletion != null && typeCompletion.numTypeParams > 0)
                {
                    // TODO Tell parent to add them
                    //parent.addOperandToRight(TypeEntry.this, "-", "", true);
                }
                
                return typeCompletion == null ? null : typeCompletion.completion;
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
            return completion.equals(input) ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.NONE;
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
        return Stream.of(textField);
    }

    @Override
    public TypeExpression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return Stream.<TypeExpression>concat(
            PRIMITIVE_TYPES.stream()
                .filter(t -> t.toString().equals(textField.getText().trim()))
                .map(t -> new TypePrimitiveLiteral(t)), 
            parent.getEditor().getTypeManager().getKnownTaggedTypes().keySet().stream()
                .filter(t -> t.getRaw().equals(textField.getText().trim()))
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
}
