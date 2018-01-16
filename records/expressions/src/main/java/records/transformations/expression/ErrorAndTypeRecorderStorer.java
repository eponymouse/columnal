package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.types.TypeExp;
import styled.StyledString;
import utility.ExConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * An implementation of ErrorAndTypeRecorder which just stores the errors encountered
 * in a list.
 */
public class ErrorAndTypeRecorderStorer implements ErrorAndTypeRecorder
{
    private final List<StyledString> errorMessages = new ArrayList<>();

    @Override
    public <E> void recordError(E src, StyledString error)
    {
        errorMessages.add(error);
    }

    @Override
    public <EXPRESSION> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION>> quickFixes)
    {
        // Ignore them, just interested in errors
    }

    public Stream<@NonNull StyledString> getAllErrors()
    {
        return errorMessages.stream();
    }

    // If there are any errors, passes first to given action
    public void withFirst(ExConsumer<StyledString> consumer) throws InternalException, UserException
    {
        if (!errorMessages.isEmpty())
            consumer.accept(errorMessages.get(0));
    }

    @SuppressWarnings("recorded")
    @Override
    public @Recorded TypeExp recordTypeNN(Expression expression, TypeExp typeExp)
    {
        // We don't care about types:
        return typeExp;
    }
}
