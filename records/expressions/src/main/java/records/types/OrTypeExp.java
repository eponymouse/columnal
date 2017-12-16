package records.types;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import utility.Either;

import java.util.stream.Stream;

public class OrTypeExp extends TypeExp
{
    // Should always be size 2+:
    public final ImmutableList<TypeExp> options;

    public OrTypeExp(ImmutableList<TypeExp> options)
    {
        this.options = options;
    }

    @Override
    public TypeExp prune()
    {
        return new OrTypeExp(options.stream().flatMap(t -> {
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
            return new OrTypeExp(remainingOptions);
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
            return Either.right(new OrTypeExp(remainingOptions));
    }
}
