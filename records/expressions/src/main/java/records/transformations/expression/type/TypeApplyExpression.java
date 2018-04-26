package records.transformations.expression.type;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.BracketedTypeNode;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static records.transformations.expression.LoadableExpression.SingleLoader.withSemanticParent;

// An Nary expression applying a tagged type, e.g. Either-Int-String.  That would have three args.
public class TypeApplyExpression extends TypeExpression
{
    // To be valid, first one must be TaggedTypeNameExpression, but we don't enforce that
    // because it may be an incomplete expression, etc.
    private final ImmutableList<TypeExpression> arguments;

    public TypeApplyExpression(ImmutableList<TypeExpression> arguments)
    {
        this.arguments = arguments;
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        return arguments.stream().map(x -> x.save(renames)).collect(Collectors.joining("-"));
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        if (arguments.size() > 1 && arguments.get(0) instanceof TaggedTypeNameExpression)
        {
            TaggedTypeDefinition def = typeManager.getKnownTaggedTypes().get(((TaggedTypeNameExpression)arguments.get(0)).getTypeName());
            if (def == null)
                return null; // It should give error by itself anyway
            if (def.getTypeArguments().size() != arguments.size() - 1)
            {
                // Wrong number of type arguments
                return null;
            }
            
            // If we're here, right number of arguments!
            List<DataType> typeArgs = new ArrayList<>();
            // Start at one:
            for (int i = 1; i < arguments.size(); i++)
            {
                DataType dataType = arguments.get(i).toDataType(typeManager);
                if (dataType == null)
                    return null;
                typeArgs.add(dataType);
            }
            try
            {
                return def.instantiate(ImmutableList.copyOf(typeArgs));
            }
            catch (UserException | InternalException e)
            {
                return null;
            }
        }
        // TODO issue an error about invalid expression
        return null;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>> loadAsSingle()
    {
        return (p, s) -> new BracketedTypeNode(p, withSemanticParent(loadAsConsecutive(false), s));
    }

    @Override
    public Pair<List<SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>>>, List<SingleLoader<TypeExpression, TypeParent, OperatorEntry<TypeExpression, TypeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Utility.mapList(arguments, (TypeExpression o) -> o.loadAsSingle()), Utility.replicateM(arguments.size() - 1, () -> new SingleLoader<TypeExpression, TypeParent, OperatorEntry<TypeExpression, TypeParent>>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public OperatorEntry<TypeExpression, TypeParent> load(ConsecutiveBase<TypeExpression, TypeParent> p, TypeParent s)
            {
                return new OperatorEntry<>(TypeExpression.class, "-", false, p);
            }
        }));
    }

    @Override
    public StyledString toStyledString()
    {
        return arguments.stream().map(x -> x.toStyledString()).collect(StyledString.joining("-"));
    }

    public ImmutableList<TypeExpression> _test_getOperands()
    {
        return arguments;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeApplyExpression that = (TypeApplyExpression) o;
        return Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(arguments);
    }
}
