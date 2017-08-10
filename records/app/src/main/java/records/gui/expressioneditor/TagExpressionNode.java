package records.gui.expressioneditor;

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
public class TagExpressionNode extends SurroundNode
{
    private final TypeId typeName;
    private final TagType<DataType> tagType;

    @SuppressWarnings({"initialization", "i18n"}) // Because LeaveableTextField gets marked uninitialized, and because of header
    public TagExpressionNode(ConsecutiveBase<Expression> parent, TypeId typeName, TagType<DataType> tagType)
    {
        super(parent, "tag", TranslationUtility.getString("tag") + " " + typeName.getRaw(), tagType.getName(), tagType.getInner() != null, null);
        this.typeName = typeName;
        this.tagType = tagType;
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(ExpressionNode child) throws InternalException, UserException
    {
        List<Pair<DataType, List<String>>> context = parent.getSuggestedContext(this);
        for (Pair<DataType, List<String>> option : context)
        {
            try
            {
                if (option.getFirst().isTagged() && option.getFirst().getTaggedTypeName().equals(typeName))
                {
                    // It matches us, so the item inside will be for us
                    @Nullable DataType inner = tagType.getInner();
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
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        return parent.getAvailableVariables(this);
    }

    @Override
    public @Nullable DataType inferType()
    {
        return null;
    }

    @Override
    public OperandNode prompt(String prompt)
    {
        return this;
    }

    @Override
    public Expression save(ErrorDisplayerRecord<Expression> errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        Expression innerExp;
        if (contents == null)
            innerExp = null;
        else
        {
            innerExp = contents.save(errorDisplayer, onError);
        }
        return errorDisplayer.record(this, new TagExpression(new Pair<>(typeName.getRaw(), tagType.getName()), innerExp));
    }
}
