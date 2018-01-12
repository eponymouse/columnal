package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.types.MutVar;
import records.types.TypeConcretisationError;
import records.types.TypeExp;
import utility.Either;
import utility.ExConsumer;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A listener that records where errors have occurred when checking an expression.
 */
public interface ErrorAndTypeRecorder
{
    /**
     * Records an error source and error message, with no available quick fixes
     */
    public default void recordError(Expression src, String error)
    {
        recordError(src, error, Collections.emptyList());
    }

    /**
     * Records an error source and error message, with no available quick fixes
     */
    public default @Nullable TypeExp recordError(Expression src, Either<String, TypeExp> errorOrType)
    {
        return errorOrType.<@Nullable TypeExp>either(err -> {
            recordError(src, err);
            return null;
        }, val -> val);
    }

    /**
     * Records an error source and error message, with no available quick fixes
     */
    public default <T> @Nullable T recordLeftError(Expression src, Either<TypeConcretisationError, T> errorOrVal)
    {
        return errorOrVal.<@Nullable T>either(err -> {
            recordError(src, err.getErrorText());
            return null;
        }, val -> val);
    }

    /**
     * Records the type for a particular expression.
     */
    public default @Recorded @Nullable TypeExp recordType(Expression expression, @Nullable TypeExp typeExp)
    {
        if (typeExp != null)
            return recordTypeNN(expression, typeExp);
        else
            return null;
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

    public default @Recorded @Nullable TypeExp recordTypeAndError(Expression expression, Either<String, TypeExp> typeOrError)
    {
        return recordType(expression, recordError(expression, typeOrError));
    }

    public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp);

    /**
     * A quick fix for an error.  Has a title to display, and a thunk to run
     * to get a replacement expression for the error source.
     */
    public final static class QuickFix
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
