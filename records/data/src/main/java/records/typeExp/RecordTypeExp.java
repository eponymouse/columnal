package records.typeExp;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class RecordTypeExp extends TypeExp
{
    public final ImmutableMap<@ExpressionIdentifier String, TypeExp> knownMembers;
    // If not complete, can have many more members than knownMembers
    // e.g. "first" uses this, has single knownMembers and complete==false
    public final boolean complete;

    // The type classes required by this record, if it is not complete:
    private TypeClassRequirements requiredTypeClasses = TypeClassRequirements.empty();
    
    public RecordTypeExp(@Nullable ExpressionBase src, ImmutableMap<@ExpressionIdentifier String, TypeExp> knownMembers, boolean complete)
    {
        super(src);
        this.knownMembers = knownMembers;
        this.complete = complete;
    }

    @Override
    public Either<StyledString, TypeExp> _unify(TypeExp b) throws InternalException
    {
        if (!(b instanceof RecordTypeExp))
            return typeMismatch(b);

        RecordTypeExp bt = (RecordTypeExp) b;
        
        // Here's the approach to unification.
        // Any elements which are only in one of the records are fine iff:
        //  - they meet any pending type class requirements from the other side.
        //  - the other side is not marked complete.
        // Any elements which are in both are fine iff:
        //  - they have a unifiable type on both sides
        // Afterwards, the pending type classes are union-ed.
        
        // LHS of pair is us, RHS is b
        HashMap<@ExpressionIdentifier String, Pair<@Nullable TypeExp, @Nullable TypeExp>> occurs = new HashMap<>();
        knownMembers.forEach((k, v) -> occurs.put(k, new Pair<>(v, null)));
        bt.knownMembers.forEach((k, v) -> occurs.merge(k, new Pair<>(null, v), (oldVal, newVal) -> new Pair<>(oldVal.getFirst(), newVal.getSecond())));

        HashMap<@ExpressionIdentifier String, TypeExp> unified = new HashMap<>();
        
        for (Entry<@ExpressionIdentifier String, Pair<@Nullable TypeExp, @Nullable TypeExp>> entry : occurs.entrySet())
        {
            Pair<@Nullable TypeExp, @Nullable TypeExp> p = entry.getValue();
            if (p.getFirst() == null || p.getSecond() == null)
            {
                // Only occurs in one side, check completeness:
                if ((p.getSecond() != null && complete) || (p.getFirst() != null && bt.complete))
                    return Either.left(StyledString.s("Field \"" + entry.getKey() + "\" occurs in one record but not in the other complete record"));
                // Check type classes:
                @Nullable StyledString typeClassErr = null;
                if (p.getFirst() != null)
                {
                    typeClassErr = p.getFirst().requireTypeClasses(bt.requiredTypeClasses);
                    unified.put(entry.getKey(), p.getFirst());
                }
                else if (p.getSecond() != null)
                {
                    typeClassErr = p.getSecond().requireTypeClasses(requiredTypeClasses);
                    unified.put(entry.getKey(), p.getSecond());
                }
                if (typeClassErr != null)
                    return Either.left(typeClassErr);
                
            }
            else
            {
                //Occurs in both sides, unify:
                Either<StyledString, TypeExp> result = p.getFirst().unifyWith(p.getSecond());
                if (result.isLeft())
                    return result;
                result.ifRight(t -> unified.put(entry.getKey(), t));
            }
        }
        
        return Either.right(new RecordTypeExp(src != null ? src : b.src, ImmutableMap.copyOf(unified), complete || bt.complete));
    }

    @Override
    public boolean containsMutVar(MutVar mutVar)
    {
        return knownMembers.values().stream().anyMatch(t -> t.containsMutVar(mutVar));
    }

    @Override
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager, boolean substituteDefaultIfPossible) throws InternalException, UserException
    {
        Map<@ExpressionIdentifier String, DataType> fields = new HashMap<>();
        for (Entry<@ExpressionIdentifier String, TypeExp> entry : knownMembers.entrySet())
        {
            Either<TypeConcretisationError, DataType> r = entry.getValue().toConcreteType(typeManager, substituteDefaultIfPossible);
            if (r.isLeft())
                return r;
            r.ifRight(t -> fields.put(entry.getKey(), t));
        }
        return Either.right(DataType.record(fields));
    }

    @Override
    public @Nullable StyledString requireTypeClasses(TypeClassRequirements typeClasses, IdentityHashSet<MutVar> visitedMutVar)
    {
        for (TypeExp member : knownMembers.values())
        {
            @Nullable StyledString err = member.requireTypeClasses(typeClasses, visitedMutVar);
            if (err != null)
                return err;
        }
        if (!complete)
        {
            this.requiredTypeClasses = TypeClassRequirements.union(this.requiredTypeClasses, typeClasses);
        }
        return null;
    }

    @Override
    protected StyledString toStyledString(int maxDepth)
    {
        return StyledString.concat(StyledString.s("("),
            StyledString.intercalate(StyledString.s(", "), knownMembers.entrySet().stream().map(e -> StyledString.concat(StyledString.s(e.getKey() + ": "), e.getValue().toStyledString(maxDepth))).collect(ImmutableList.<StyledString>toImmutableList())),
            StyledString.s(complete ? ")" : ", ...)"));
    }
}
