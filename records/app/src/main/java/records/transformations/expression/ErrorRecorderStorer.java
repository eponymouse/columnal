package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * An implementation of ErrorRecorder which just stores the errors encountered
 * in a list.
 */
public class ErrorRecorderStorer implements ErrorRecorder
{
    private final List<String> errorMessages = new ArrayList<>();

    @Override
    public void recordError(Expression src, String error, List<QuickFix> fixes)
    {
        errorMessages.add(error);
    }

    public Stream<@NonNull String> getAllErrors()
    {
        return errorMessages.stream();
    }

    // If there are any errors, passes first to given action
    public void withFirst(ExConsumer<String> consumer) throws InternalException, UserException
    {
        if (!errorMessages.isEmpty())
            consumer.accept(errorMessages.get(0));
    }
}
