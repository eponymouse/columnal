package records.types;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.error.InternalException;
import utility.Either;

public class TypeCons extends TypeExp
{
    public final String name;
    // Can be size 0+:
    public final ImmutableList<TypeExp> operands;

    public TypeCons(String name, ImmutableList<TypeExp> operands)
    {
        this.name = name;
        this.operands = operands;
    }

    @Override
    public Either<String, TypeExp> _unify(TypeExp b) throws InternalException
    {
        if (!(b instanceof TypeCons))
            return typeMismatch(b);
        
        TypeCons bt = (TypeCons) b;
        if (!name.equals(bt.name))
            return typeMismatch(b);
        
        // This probably shouldn't happen in our editor, as it suggests
        // an incoherent expression:
        if (operands.size() != bt.operands.size())
            return typeMismatch(b);

        ImmutableList.Builder<TypeExp> unifiedOperands = ImmutableList.builder();
        for (int i = 0; i < operands.size(); i++)
        {
            Either<String, TypeExp> sub = operands.get(i).unifyWith(bt.operands.get(i));
            if (sub.isLeft())
                return sub;
            unifiedOperands.add(sub.getRight());
        }
        return Either.right(new TypeCons(name, unifiedOperands.build()));
    }

    @Override
    public @Nullable TypeExp withoutMutVar(MutVar mutVar)
    {
        ImmutableList.Builder<TypeExp> without = ImmutableList.builder();
        for (TypeExp operand : operands)
        {
            TypeExp t = operand.withoutMutVar(mutVar);
            if (t == null)
                return null;
            without.add(t);
        }
        return new TypeCons(name, without.build());
    }
}
