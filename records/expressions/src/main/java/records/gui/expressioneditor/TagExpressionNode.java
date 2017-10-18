package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.TagExpression;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 21/01/2017.
 */
public class TagExpressionNode extends SurroundNode implements ExpressionNodeParent
{
    private final String typeName;
    private final String tagName;

    @SuppressWarnings({"initialization", "i18n"}) // Because LeaveableTextField gets marked uninitialized, and because of header
    public TagExpressionNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent, String typeName, String tagName, @Nullable Expression innerContent)
    {
        super(parent, semanticParent, "tag", TranslationUtility.getString("tag") + " " + typeName, tagName, innerContent != null, innerContent);
        this.typeName = typeName;
        this.tagName = tagName;
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        List<Pair<DataType, List<String>>> context = parent.getThisAsSemanticParent().getSuggestedContext(this);
        for (Pair<DataType, List<String>> option : context)
        {
            try
            {
                if (option.getFirst().isTagged() && option.getFirst().getTaggedTypeName().getRaw().equals(typeName))
                {
                    // It matches us, so the item inside will be for us
                    @Nullable DataType inner = option.getFirst().getTagTypes().stream().filter(tt -> tt.getName().equals(tagName)).<@Nullable DataType>map(tt -> tt.getInner()).findFirst().orElse(null);
                    if (inner != null) // Shouldn't be null if our child is asking for context...
                        return Collections.singletonList(new Pair<>(inner, option.getSecond()));
                }
            }
            catch (InternalException e)
            {
                // This shouldn't happen because we have the tagged option
                Utility.log(e);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(@UnknownInitialization EEDisplayNode child)
    {
        return semanticParent.getAvailableVariables(this);
    }

    @Override
    public boolean canDeclareVariable(@UnknownInitialization EEDisplayNode chid)
    {
        // Sub item can declare variables as pattern match if we could:
        return semanticParent.canDeclareVariable(this);
    }

    @Override
    public void prompt(String prompt)
    {
    }

    @Override
    public Expression save(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        Expression innerExp;
        if (contents == null)
            innerExp = null;
        else
        {
            innerExp = contents.save(errorDisplayer, onError);
        }
        return errorDisplayer.record(this, new TagExpression(new Pair<>(typeName, tagName), innerExp));
    }
}
