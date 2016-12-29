package records.importers;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import utility.ExFunction;
import utility.ExSupplier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by neil on 29/12/2016.
 */
public class ChoicePoint<R>
{
    public static abstract class Choice { }

    // If none, end of the road (either successful or failure, depending on exception/vakye)
    private final List<Choice> options;
    private final double score;
    private final @Nullable Exception exception;
    private final @Nullable R value;
    // If we take current choice, what's the next choice point?
    private final Map<Choice, ChoicePoint> calculated;

    private ChoicePoint(List<Choice> options, double score, @Nullable Exception exception, @Nullable R value, Map<Choice, ChoicePoint> calculated)
    {
        this.options = options;
        this.score = score;
        this.exception = exception;
        this.value = value;
        this.calculated = calculated;
    }

    public static <C extends Choice, R> ChoicePoint<R> choose(ExFunction<C, ChoicePoint<R>> hereOnwards, C... choices)
    {
        List<Choice> allOptions = new ArrayList<>();
        allOptions.addAll(Arrays.asList(choices));
        ChoicePoint next;
        try
        {
            next = hereOnwards.apply(choices[0]); //TODO try them all and go by score
        }
        catch (UserException e)
        {
            next = failure(e);
        }
        catch (InternalException e)
        {
            next = failure(e);
        }
        return new ChoicePoint<>(allOptions, 0, null, null, Collections.singletonMap(choices[0], next));
    }

    public static <R> ChoicePoint<R> chosen(Choice chosen, ChoicePoint<R> result, ExFunction<Choice, ChoicePoint<R>> chooseOther, Choice... rest)
    {
        List<Choice> allOptions = new ArrayList<>();
        allOptions.add(chosen);
        allOptions.addAll(Arrays.asList(rest));
        return new ChoicePoint<>(allOptions, Double.MAX_VALUE, null, null, Collections.singletonMap(chosen, result));
    }

    public static <R> ChoicePoint<R> possibility(R value, double score)
    {
        return new ChoicePoint<>(Collections.emptyList(), score, null, value, Collections.emptyMap());
    }


    public static <R> ChoicePoint<R> success(R value)
    {
        return new ChoicePoint<>(Collections.emptyList(), Double.MAX_VALUE, null, value, Collections.emptyMap());
    }

    public static <R> ChoicePoint<R> run(ExSupplier<R> supplier)
    {
        try
        {
            return ChoicePoint.success(supplier.get());
        }
        catch (InternalException | UserException e)
        {
            return ChoicePoint.failure(e);
        }
    }


    public static <R> ChoicePoint<R> failure(Exception e)
    {
        return new ChoicePoint<>(Collections.emptyList(), -Double.MAX_VALUE, e, null, Collections.emptyMap());
    }
}
