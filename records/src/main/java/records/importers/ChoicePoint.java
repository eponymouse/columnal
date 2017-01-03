package records.importers;

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

/**
 * Poor man's list monad.
 */
public class ChoicePoint<R>
{
    public static abstract class Choice {
        // hashCode and equals must be overriden in children:
        @Override
        public abstract int hashCode();

        @Override
        public abstract boolean equals(@Nullable Object obj);
    }

    // If none, end of the road (either successful or failure, depending on exception/vakye)
    private final List<Choice> options;
    private final double score;
    private final @Nullable Exception exception;
    private final @Nullable R value;
    // If we take current choice, what's the next choice point?
    private final Map<Choice, ChoicePoint<R>> calculated;

    private <C extends Choice> ChoicePoint(List<C> options, double score, @Nullable Exception exception, @Nullable R value, Map<C, ChoicePoint<R>> calculated)
    {
        this.options = new ArrayList<>(options);
        this.score = score;
        this.exception = exception;
        this.value = value;
        this.calculated = new HashMap<>(calculated);
    }

    public static <C extends Choice, R> ChoicePoint<R> choose(ExFunction<C, ChoicePoint<R>> hereOnwards, C... choices)
    {
        List<C> allOptions = new ArrayList<>();
        allOptions.addAll(Arrays.<C>asList(choices));
        Map<C, ChoicePoint<R>> calc = new HashMap<>();
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
        return new <C>ChoicePoint<R>(allOptions, 0, null, null, calc);
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

    public static <R> ChoicePoint<R> possibility(R value, double score)
    {
        return new <Choice>ChoicePoint<R>(Collections.<Choice>emptyList(), score, null, value, Collections.<Choice, ChoicePoint<R>>emptyMap());
    }


    public static <R> ChoicePoint<R> success(R value)
    {
        return new <Choice>ChoicePoint<R>(Collections.<Choice>emptyList(), Double.MAX_VALUE, null, value, Collections.<Choice, ChoicePoint<R>>emptyMap());
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
        return new <Choice>ChoicePoint<R>(Collections.<Choice>emptyList(), -Double.MAX_VALUE, e, null, Collections.<Choice, ChoicePoint<R>>emptyMap());
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
                return new <Choice>ChoicePoint<S>(Collections.<Choice>emptyList(), score, exception, null, Collections.<Choice, ChoicePoint<S>>emptyMap());
        }
        else
        {
            // Not a leaf; need to copy and go deeper:
            HashMap<Choice, ChoicePoint<S>> calcCopy = new HashMap<>();
            calculated.forEach((c, p) -> {
                calcCopy.put(c, p.then(then));
            });
            return new <Choice>ChoicePoint<S>(options, score, null, null, calcCopy);
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
        if (!calculated.containsKey(choice))
            throw new InternalException("Picking uncalculated choice: " + choice);
        return calculated.get(choice);
    }
}
