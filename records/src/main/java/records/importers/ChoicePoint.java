package records.importers;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import utility.ExFunction;
import utility.ExSupplier;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Poor man's list monad.
 */
public class ChoicePoint<R>
{
    public static enum Quality
    {
        // Good candidate, can stop at this one if searching for best
        PROMISING,
        // Poor candidate, search others if looking for best.
        FALLBACK;
    }

    public static abstract class Choice {
        // hashCode and equals must be overriden in children:
        @Override
        public abstract int hashCode();

        @Override
        public abstract boolean equals(@Nullable Object obj);
    }

    // If none, end of the road (either successful or failure, depending on exception/vakye)
    private final List<Choice> options;
    private final Quality quality;
    private final double score;
    private final @Nullable Exception exception;
    private final @Nullable R value;
    // If we take current choice, what's the next choice point?
    private final LoadingCache<Choice, ChoicePoint<R>> calculated;

    private <C extends Choice> ChoicePoint(List<C> options, Quality quality, double score, @Nullable Exception exception, @Nullable R value, final @Nullable ExFunction<C, ChoicePoint<R>> calculate)
    {
        this.options = new ArrayList<>(options);
        this.quality = quality;
        this.score = score;
        this.exception = exception;
        this.value = value;
        if (calculate != null)
        {
            final @Nullable ExFunction<C, ChoicePoint<R>> calculateFinal = calculate;
            LoadingCache<Choice, ChoicePoint<R>> calc = CacheBuilder.<Choice, ChoicePoint<R>>newBuilder().maximumSize(20).build(new CacheLoader<Choice, ChoicePoint<R>>()
            {
                @Override
                public ChoicePoint<R> load(Choice c) throws Exception
                {
                    return calculateFinal.apply((C)c);
                }
            });
            this.calculated = calc;
        }
        else
            this.calculated = CacheBuilder.newBuilder().build(new CacheLoader<Choice, ChoicePoint<R>>()
            {
                @Override
                public ChoicePoint<R> load(Choice choice) throws Exception
                {
                    return failure(new InternalException("Calculating with no options"));
                }
            });
    }

    public static <C extends Choice, R> ChoicePoint<R> choose(Quality quality, double score, ExFunction<C, ChoicePoint<R>> hereOnwards, C... choices)
    {
        List<C> allOptions = new ArrayList<>();
        allOptions.addAll(Arrays.<C>asList(choices));
        Map<C, ChoicePoint<R>> calc = new HashMap<>();
        /*
        for (C choice : choices)
        {
            ChoicePoint<R> next;
            try
            {
                next = hereOnwards.apply(choice);
            }
            catch (UserException e)
            {
                next = failure(e);
            }
            catch (InternalException e)
            {
                next = failure(e);
            }
            calc.put(choice, next);
        }
        */
        return new <C>ChoicePoint<R>(allOptions, quality, score, null, null, hereOnwards);
    }

    /*
    public static <C extends Choice, R> ChoicePoint<R> chosen(C chosen, ChoicePoint<R> result, ExFunction<Choice, ChoicePoint<R>> chooseOther, C... rest)
    {
        List<C> allOptions = new ArrayList<>();
        allOptions.add(chosen);
        allOptions.addAll(Arrays.<C>asList(rest));
        return new <C>ChoicePoint<R>(allOptions, Double.MAX_VALUE, null, null, Collections.<C, ChoicePoint<R>>singletonMap(chosen, result));
    }
    */
    /*
    public static <R> ChoicePoint<R> possibility(R value, double score)
    {
        return new <Choice>ChoicePoint<R>(Collections.<Choice>emptyList(), score, null, value, Collections.<Choice, ChoicePoint<R>>emptyMap());

    }*/

    public static <R> ChoicePoint<R> success(R value)
    {
        return new <Choice>ChoicePoint<R>(Collections.<Choice>emptyList(), Quality.PROMISING, Double.MAX_VALUE, null, value, null);
    }

    public static <R> ChoicePoint<R> run(ExSupplier<@NonNull R> supplier)
    {
        try
        {
            return ChoicePoint.<R>success(supplier.get());
        }
        catch (InternalException | UserException e)
        {
            return ChoicePoint.<R>failure(e);
        }
    }


    public static <R> ChoicePoint<R> failure(Exception e)
    {
        return new <Choice>ChoicePoint<R>(Collections.<Choice>emptyList(), Quality.FALLBACK, -Double.MAX_VALUE, e, null, null);
    }

    public <S> ChoicePoint<S> then(ExFunction<R, @NonNull S> then)
    {
        if (options.isEmpty())
        {
            if (value != null)
            {
                final @NonNull R valueFinal = value;
                return ChoicePoint.<S>run(() -> then.apply(valueFinal));
            }
            else
                return new <Choice>ChoicePoint<S>(Collections.<Choice>emptyList(), quality, score, exception, null, null);
        }
        else
        {
            // Not a leaf; need to copy and go deeper:
            // Options is non-empty so exception and value not null:
            return new <Choice>ChoicePoint<S>(options, quality, score, null, null, (ExFunction<Choice, ChoicePoint<S>>) choice -> {
                try
                {
                    return calculated.get(choice).then(then);
                }
                catch (ExecutionException e)
                {
                    throw new InternalException("Execution issue", e);
                }
            });
        }
    }

    public @Nullable Class<?> _test_getChoiceClass()
    {
        if (options.isEmpty())
            return null;
        else
            return options.get(0).getClass();
    }


    public R get() throws UserException, InternalException
    {
        if (value != null)
            return value;
        else if (exception != null && exception instanceof UserException)
            throw (UserException)exception;
        else if (exception != null && exception instanceof InternalException)
            throw (InternalException)exception;
        else
            throw new InternalException("Called get but still choices to make");
    }

    public ChoicePoint<R> select(Choice choice) throws InternalException
    {
        if (!options.contains(choice))
            throw new InternalException("Picking unavailable choice: " + choice + " from: " + Utility.listToString(options));
        try
        {
            return calculated.get(choice);
        }
        catch (ExecutionException e)
        {
            throw new InternalException("Error picking choice", e);
        }
    }
}
