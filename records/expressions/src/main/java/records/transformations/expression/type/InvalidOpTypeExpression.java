package records.transformations.expression.type;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.gui.expressioneditor.BracketedTypeNode;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.loadsave.OutputBuilder;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;

import static records.transformations.expression.LoadableExpression.SingleLoader.withSemanticParent;

public class InvalidOpTypeExpression extends TypeExpression
{
    private final ImmutableList<TypeExpression> operands;
    private final ImmutableList<String> operators;

    public InvalidOpTypeExpression(ImmutableList<TypeExpression> operands, List<String> ops)
    {
        this.operands = operands;
        this.operators = ImmutableList.copyOf(ops);
    }

    @Override
    public SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>> loadAsSingle()
    {
        return (p, s) -> new BracketedTypeNode(p, withSemanticParent(loadAsConsecutive(true), s));
    }

    @Override
    public Pair<List<SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>>>, List<SingleLoader<TypeExpression, TypeParent, OperatorEntry<TypeExpression, TypeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Utility.mapList(operands, (TypeExpression o) -> o.loadAsSingle()), Utility.mapList(operators, o -> new SingleLoader<TypeExpression, TypeParent, OperatorEntry<TypeExpression, TypeParent>>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public OperatorEntry<TypeExpression, TypeParent> load(ConsecutiveBase<TypeExpression, TypeParent> p, TypeParent s)
            {
                return new OperatorEntry<>(TypeExpression.class, o, false, p);
            }
        }));
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        StringBuilder s = new StringBuilder("@INVALIDOPS (");
        for (int i = 0; i < operands.size(); i++)
        {
            s.append(operands.get(i).save(renames));
            if (i < operators.size())
                s.append(OutputBuilder.quoted(operators.get(i)));
        }
        return s.append(")").toString();
    }

    @Override
    public @Nullable DataType toDataType()
    {
        return null;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s("Invalid"); // TODO
    }
}
