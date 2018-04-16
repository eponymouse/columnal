package records.transformations.expression.type;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.gui.expressioneditor.BracketedTypeNode;
import records.gui.expressioneditor.Consecutive.ConsecutiveStartContent;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import styled.StyledString;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TupleTypeExpression extends TypeExpression
{
    private final ImmutableList<TypeExpression> members;

    public TupleTypeExpression(ImmutableList<TypeExpression> members)
    {
        this.members = members;
    }

    @Override
    public SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>> loadAsSingle()
    {
        return (outerParent, s) -> {
            List<FXPlatformFunction<ConsecutiveBase<TypeExpression, TypeParent>, OperandNode<TypeExpression, TypeParent>>> operands = new ArrayList<>();
            List<FXPlatformFunction<ConsecutiveBase<TypeExpression, TypeParent>, OperatorEntry<TypeExpression, TypeParent>>> operators = new ArrayList<>();
            for (int i = 0; i < members.size(); i++)
            {
                Pair<List<SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>>>, List<SingleLoader<TypeExpression, TypeParent, OperatorEntry<TypeExpression, TypeParent>>>> items = members.get(i).loadAsConsecutive(members.size() == 1);
                operators.addAll(Utility.mapList(items.getSecond(), f -> p -> f.load(p, p.getThisAsSemanticParent())));
                operands.addAll(Utility.mapList(items.getFirst(), f -> p -> f.load(p, p.getThisAsSemanticParent())));
                // Now we must add the comma:
                if (i < members.size() - 1)
                    operators.add(p -> new OperatorEntry<>(TypeExpression.class, ",", false, p));
            }

            return new BracketedTypeNode(outerParent, new ConsecutiveStartContent<>(operands, operators));
        };
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.roundBracket(members.stream().map(s -> s.toStyledString()).collect(StyledString.joining(", ")));
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        return "(" + members.stream().map(m -> m.save(renames)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        ImmutableList.Builder<DataType> memberTypes = ImmutableList.builderWithExpectedSize(members.size());
        for (TypeExpression member : members)
        {
            DataType memberType = member.toDataType(typeManager);
            if (memberType == null)
                return null;
            memberTypes.add(memberType);
        }
        return DataType.tuple(memberTypes.build());
    }

    public ImmutableList<TypeExpression> _test_getItems()
    {
        return members;
    }
}
