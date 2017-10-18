package records.importers;

import annotation.help.qual.HelpKey;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.importers.ChoicePoint.Choice;
import utility.Either;
import utility.ExFunction;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Poor man's list monad.
 */
public class ChoicePoint<C extends Choice, R>
{


    public static enum Quality
    {
        // Good candidate, can stop at this one if searching for best
        PROMISING,
        // Poor candidate, search others if looking for best.
        FALLBACK,
        // No way:
        NOTVIABLE;
    }

    public static abstract class Choice {
        // hashCode and equals must be overriden in children:
        @Override
        public abstract int hashCode();

        @Override
        public abstract boolean equals(@Nullable Object obj);

        @Override
        public abstract @Localized String toString();
    }

    /**
     * Contains information about the type C.  A bit like a souped-up
     * Class<C> with useful meta-information (e.g. is free entry allowed,
     * get help on the item, etc).
     */
    public static class ChoiceType<C extends Choice>
    {
        private final Class<C> choiceClass;
        private final @LocalizableKey String labelKey;
        private final @HelpKey String helpKey;

        public ChoiceType(Class<C> choiceClass, @LocalizableKey String labelKey, @HelpKey String helpKey)
        {
            this.choiceClass = choiceClass;
            this.labelKey = labelKey;
            this.helpKey = helpKey;
        }

        public Class<C> getChoiceClass()
        {
            return choiceClass;
        }

        public @LocalizableKey String getLabelKey()
        {
            return labelKey;
        }

        public @HelpKey String getHelpId()
        {
            return helpKey;
        }

        @Override
        public String toString()
        {
            return "ChoiceType{" +
                "choiceClass=" + choiceClass +
                ", labelKey='" + labelKey + '\'' +
                ", helpKey='" + helpKey + '\'' +
                '}';
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ChoiceType<?> that = (ChoiceType<?>) o;

            return choiceClass.equals(that.choiceClass);
        }

        @Override
        public int hashCode()
        {
            return choiceClass.hashCode();
        }

        //TODO: methods for free entry, and for help
    }

    public static class Options<C extends Choice>
    {
        public final ChoiceType<C> choiceType;
        public final ImmutableList<C> quickPicks;
        // If null, quick picks are the only options.  If non-null, offer an "Other" pick
        // in the combo box which shows a text field:
        public final @Nullable Function<String, Either<@Localized String, C>> stringEntry;

        public Options(ChoiceType<C> choiceType, ImmutableList<C> quickPicks, @Nullable Function<String, Either<@Localized String, C>> stringEntry)
        {
            this.choiceType = choiceType;
            this.quickPicks = quickPicks;
            this.stringEntry = stringEntry;
        }

        // Does this item have no possible choices?
        public boolean isEmpty()
        {
            return quickPicks.isEmpty() && stringEntry == null;
        }

        // Note: this uses .equals() for comparison, not reference comparison.
        // This is only a brief sanity check, not a definitive answer
        public boolean canPick(C choice)
        {
            return quickPicks.contains(choice) || stringEntry != null;
        }
    }

    // If null, end of the road (either successful or failure, depending on exception/value)
    private final @Nullable Options<C> options;
    private final Quality quality;
    private final double score;
    private final @Nullable Exception exception;
    private final @Nullable R value;
    // If we take current choice, what's the next choice point?
    private final LoadingCache<C, ChoicePoint<?, R>> calculated;

    private ChoicePoint(@Nullable Options<C> options, Quality quality, double score, @Nullable Exception exception, @Nullable R value, final @Nullable ExFunction<C, ChoicePoint<?, R>> calculate)
    {
        this.options = options;
        this.quality = quality;
        this.score = score;
        this.exception = exception;
        this.value = value;
        if (calculate != null)
        {
            final @Nullable ExFunction<C, ChoicePoint<?, R>> calculateFinal = calculate;
            LoadingCache<C, ChoicePoint<?, R>> calc = CacheBuilder.<C, ChoicePoint<?, R>>newBuilder().maximumSize(20).build(new CacheLoader<C, ChoicePoint<?, R>>()
            {
                @Override
                public ChoicePoint<?, R> load(C c) throws Exception
                {
                    return calculateFinal.apply(c);
                }
            });
            this.calculated = calc;
        }
        else
            this.calculated = CacheBuilder.newBuilder().build(new CacheLoader<Choice, ChoicePoint<?, R>>()
            {
                @Override
                public ChoicePoint<?, R> load(Choice choice) throws Exception
                {
                    return failure(new InternalException("Calculating with no options"));
                }
            });
    }

    public static <C extends Choice, R> ChoicePoint<C, R> choose(Quality quality, double score, ChoiceType<C> choiceType, ExFunction<C, ChoicePoint<?, R>> hereOnwards, List<C> choices, @Nullable Function<String, Either<@Localized String, C>> pickManually)
    {
        //List<C> allOptions = new ArrayList<>();
        //allOptions.addAll(Arrays.<C>asList(choices));
        //Map<C, ChoicePoint<?, R>> calc = new HashMap<>();
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
        return new ChoicePoint<C, R>(new Options<>(choiceType, ImmutableList.copyOf(choices), pickManually), quality, score, null, null, hereOnwards);
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

    public static <R> ChoicePoint<Choice, R> success(Quality quality, double score, R value)
    {
        return new ChoicePoint<Choice, R>(null, quality, score, null, value, null);
    }

    public static <R> ChoicePoint<Choice, R> run(ExSupplier<@NonNull R> supplier)
    {
        try
        {
            return ChoicePoint.<R>success(Quality.PROMISING, Double.MAX_VALUE, supplier.get());
        }
        catch (InternalException | UserException e)
        {
            return ChoicePoint.<R>failure(e);
        }
    }


    public static <R> ChoicePoint<Choice, R> failure(Exception e)
    {
        return new ChoicePoint<Choice, R>(null, Quality.FALLBACK, -Double.MAX_VALUE, e, null, null);
    }

    public <S> ChoicePoint<?, S> then(ExFunction<R, @NonNull S> then)
    {
        if (options == null || options.isEmpty())
        {
            if (value != null)
            {
                final @NonNull R valueFinal = value;
                return ChoicePoint.<S>run(() -> then.apply(valueFinal));
            }
            else
                return new ChoicePoint<Choice, S>(null, quality, score, exception, null, null);
        }
        else
        {
            // Not a leaf; need to copy and go deeper:
            // Options is non-empty so exception and value not null:
            return new ChoicePoint<C, S>(options, quality, score, null, null, (ExFunction<C, ChoicePoint<?, S>>) choice -> {
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


    public @NonNull R get() throws UserException, InternalException
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

    public ChoicePoint<?, R> select(C choice) throws InternalException
    {
        if (options == null || !options.canPick(choice))
            throw new InternalException("Picking unavailable choice: " + choice + " from initial list: " + Utility.listToString(options == null ? Collections.<@NonNull C>emptyList() : options.quickPicks));
        try
        {
            return calculated.get(choice);
        }
        catch (ExecutionException e)
        {
            throw new InternalException("Error picking choice", e);
        }
    }

    public @Nullable Options<C> getOptions()
    {
        return options;
    }

    public Quality getQuality()
    {
        return quality;
    }

    @Override
    public String toString()
    {
        return "ChoicePoint{" +
            "options=" + options +
            ", quality=" + quality +
            ", score=" + score +
            ", exception=" + exception +
            ", value=" + value +
            ", calculated=" + calculated +
            '}';
    }
}
