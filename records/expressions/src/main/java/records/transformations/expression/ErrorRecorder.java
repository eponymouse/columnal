package records.transformations.expression;

import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.dataflow.qual.Pure;
import utility.ExConsumer;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A listener that records where errors have occurred when checking an expression.
 */
public interface ErrorRecorder
{
    /**
     * Records an error source and error message, with no available quick fixes
     */
    public default void recordError(Expression src, String error)
    {
        recordError(src, error, Collections.emptyList());
    }

    /**
     * A curried version of the two-arg function of the same name.
     */
    @Pure
    public default Consumer<String> recordError(Expression src)
    {
        return errMsg -> recordError(src, errMsg);
    }

    /**
     * Records an error source and error message, and a list of possible quick fixes
     */
    // TODO make the String @Localized
    public void recordError(Expression src, String error, List<QuickFix> fixes);

    /**
     * A quick fix for an error.  Has a title to display, and a thunk to run
     * to get a replacement expression for the error source.
     */
    public static class QuickFix
    {
        private final @Localized String title;
        private final Supplier<Expression> fixedReplacement;

        public QuickFix(@Localized String title, Supplier<Expression> fixedReplacement) {
            this.title = title;
            this.fixedReplacement = fixedReplacement;
        }

        public @Localized String getTitle() {
            return title;
        }
    }
}
