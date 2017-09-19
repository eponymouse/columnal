package records.importers;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.importers.ChoicePoint.Choice;
import records.importers.ChoicePoint.ChoiceType;

/**
 * Think of this as like Collection<Choice>, but each choice is stored with
 * a type index so you can look them up using ChoiceType<C>.  It also records
 * whether this is a partial item (i.e. we reached a dead end where no choices
 * were possible) or finished (we reached the end).
 *
 * Class is immutable.
 */
public class Choices
{
    private static class SingleChoice<C extends Choice>
    {
        private final ChoiceType<C> choiceType;
        private final C choice;

        public SingleChoice(ChoiceType<C> choiceType, C choice)
        {
            this.choiceType = choiceType;
            this.choice = choice;
        }

        public <T extends Choice> @Nullable T getIfTypeIs(ChoiceType<T> searchingFor)
        {
            if (searchingFor.equals(choiceType))
                return searchingFor.getChoiceClass().cast(choice);
            else
                return null;
        }
    }

    private final ImmutableList<SingleChoice<?>> choices;
    private final boolean finished;

    public static final Choices FINISHED = new Choices(ImmutableList.of(), true);
    public static final Choices UNFINISHED = new Choices(ImmutableList.of(), false);

    private Choices(ImmutableList<SingleChoice<?>> choices, boolean finished)
    {
        this.choices = choices;
        this.finished = finished;
    }

    public <C extends Choice> @Nullable C getChoice(ChoiceType<C> choiceType)
    {
        for (SingleChoice<?> choice : choices)
        {
            C c = choice.getIfTypeIs(choiceType);
            if (c != null)
                return c;
        }
        return null;
    }

    public <C extends Choice> Choices with(ChoiceType<C> choiceType, C choice)
    {
        ImmutableList.Builder<SingleChoice<?>> appended = ImmutableList.builder();
        appended.addAll(this.choices);
        appended.add(new SingleChoice<>(choiceType, choice));
        return new Choices(appended.build(), this.finished);
    }

    public boolean isFinished()
    {
        return finished;
    }

}
