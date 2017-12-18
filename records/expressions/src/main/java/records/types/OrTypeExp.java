package records.types;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.transformations.expression.Expression;
import utility.Either;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OrTypeExp extends TypeExp
{
    // Should always be size 2+:
    public final ImmutableList<TypeExp> options;

    public OrTypeExp(Expression src, ImmutableList<TypeExp> options)
    {
        super(src);
        this.options = options;
    }

    @Override
    public TypeExp prune()
    {
        return new OrTypeExp(src, options.stream().flatMap(t -> {
            t = t.prune();
            if (t instanceof OrTypeExp)
                return ((OrTypeExp)t).options.stream();
            else
                return Stream.of(t);
        }).collect(ImmutableList.toImmutableList()));
    }

    @Override
    public @Nullable TypeExp withoutMutVar(MutVar mutVar)
    {
        ImmutableList.Builder<TypeExp> remaining = ImmutableList.builder();
        for (TypeExp option : options)
        {
            @Nullable TypeExp t = option.withoutMutVar(mutVar);
            if (t != null)
                remaining.add(t);
        }
        
        ImmutableList<TypeExp> remainingOptions = remaining.build();
        if (remainingOptions.isEmpty())
            return null;
        else if (remainingOptions.size() == 1)
            return remainingOptions.get(0);
        else
            return new OrTypeExp(src, remainingOptions);
    }

    @Override
    protected Either<String, DataType> _concrete(TypeManager typeManager)
    {
        return Either.left("Ambiguous type - could be any of: " + options.stream().limit(5).map(o -> o.toString()).collect(Collectors.joining(" or ")) + (options.size() > 5 ? (" or " + (options.size() + 5) + " more") : ""));
    }

    // Note: this is potentially explosive in the number of
    // possibilities.  So we add an arbitrary threshold to limit this
    @Override
    public Either<String, TypeExp> _unify(TypeExp b) throws InternalException
    {
        ImmutableList<TypeExp> bOpts;
        if (b instanceof OrTypeExp)
            bOpts = ((OrTypeExp)b).options;
        else
            bOpts = ImmutableList.of(b);

        if (options.size() * bOpts.size() > 100)
        {
            return Either.left("Too many type possibilities to check: " + options.size() * bOpts.size());
        }

        ImmutableList.Builder<TypeExp> remaining = ImmutableList.builder();
        for (TypeExp optionA : options)
        {
            for (TypeExp optionB : bOpts)
            {
                Either<String, TypeExp> ab = optionA.unifyWith(optionB);
                ab.either_(err -> {}, t -> remaining.add(t));
            }
        }

        ImmutableList<TypeExp> remainingOptions = remaining.build();
        if (remainingOptions.isEmpty())
            return Either.left("No possible type options found");
        else if (remainingOptions.size() == 1)
            return Either.right(remainingOptions.get(0));
        else
            return Either.right(new OrTypeExp(src, remainingOptions));
    }
}
