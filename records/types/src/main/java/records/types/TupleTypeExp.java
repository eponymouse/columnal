package records.types;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import utility.Either;

import java.util.Objects;

public class TupleTypeExp extends TypeExp
{
    public final ImmutableList<TypeExp> knownMembers;
    // If not complete, can have many more members than knownMembers
    // e.g. "first" uses this, has single knownMembers and complete==false
    public final boolean complete;

    public TupleTypeExp(@Nullable ExpressionBase src, ImmutableList<TypeExp> knownMembers, boolean complete)
    {
        super(src);
        this.knownMembers = knownMembers;
        this.complete = complete;
    }

    @Override
    public @Nullable TypeExp withoutMutVar(MutVar mutVar)
    {
        ImmutableList.Builder<TypeExp> members = ImmutableList.builder();
        for (TypeExp knownMember : knownMembers)
        {
            TypeExp t = knownMember.withoutMutVar(mutVar);
            if (t == null)
                return null;
            members.add(t);
        }
        return new TupleTypeExp(src, members.build(), complete);
    }

    @Override
    protected Either<String, DataType> _concrete(TypeManager typeManager) throws InternalException
    {
        if (!complete)
            return Either.left("Error: tuple of indeterminate size");
        return Either.mapMInt(knownMembers, (TypeExp t) -> t.toConcreteType(typeManager)).map(ts -> DataType.tuple(ts));
    }

    @Override
    public Either<String, TypeExp> _unify(TypeExp b) throws InternalException
    {
        if (!(b instanceof TupleTypeExp))
            return typeMismatch(b);

        TupleTypeExp bt = (TupleTypeExp) b;
        
        // Length mismatch is allowed only if the shorter one is marked as
        // incomplete.  Check for the bad case (shorter is marked as complete)
        if (knownMembers.size() < bt.knownMembers.size() && complete)
        {
            return Either.left("Mismatch: tuple of size at least " + bt.knownMembers.size() + " expected, vs tuple of size: " + knownMembers.size());
        }
        else if (knownMembers.size() > bt.knownMembers.size() && bt.complete)
        {
            return Either.left("Mismatch: tuple of size at least " + knownMembers.size() + " expected, vs tuple of size: " + knownMembers.size());
        }
        // Other bad case: both are complete, and different sizes:
        else if (knownMembers.size() != bt.knownMembers.size() && complete && bt.complete)
        {
            return Either.left("Mismatch: tuple of size " + knownMembers.size() + " expected, vs tuple of size: " + bt.knownMembers.size());
        }
        
        // If we're still here, we can just unify along the length of the shortest.  Then we fill in remaining args from the longer side
        ImmutableList.Builder<TypeExp> unified = ImmutableList.builder();
        int longer = Math.max(knownMembers.size(), bt.knownMembers.size());
        for (int i = 0; i < longer; i++)
        {
            if (i < knownMembers.size() && i < bt.knownMembers.size())
            {
                Either<String, TypeExp> result = knownMembers.get(i).unifyWith(bt.knownMembers.get(i));
                if (result.isLeft())
                    return result;
                unified.add(result.getRight());
            }
            else if (i < knownMembers.size())
            {
                unified.add(knownMembers.get(i));
            }
            else
            {
                unified.add(bt.knownMembers.get(i));
            }
        }
        return Either.right(new TupleTypeExp(src != null ? src : b.src, unified.build(), complete || bt.complete));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TupleTypeExp that = (TupleTypeExp) o;
        return complete == that.complete &&
            Objects.equals(knownMembers, that.knownMembers);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(knownMembers, complete);
    }
}

