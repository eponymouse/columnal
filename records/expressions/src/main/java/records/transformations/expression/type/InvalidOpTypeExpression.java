package records.transformations.expression.type;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
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
import java.util.Objects;

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
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return null;
    }

    @Override
    public boolean isEmpty()
    {
        return operands.size() == 1 && operators.isEmpty() && operands.get(0).isEmpty();
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s("Invalid"); // TODO
    }

    public ImmutableList<TypeExpression> _test_getOperands()
    {
        return operands;
    }
    
    public ImmutableList<String> _test_getOperators()
    {
        return operators;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvalidOpTypeExpression that = (InvalidOpTypeExpression) o;
        return Objects.equals(operands, that.operands) &&
            Objects.equals(operators, that.operators);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(operands, operators);
    }
}
