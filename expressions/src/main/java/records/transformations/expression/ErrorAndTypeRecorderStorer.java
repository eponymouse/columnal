package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.FunctionLookup;
import records.typeExp.TypeConcretisationError;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import utility.Either;
import utility.ExConsumer;
import utility.Pair;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of ErrorAndTypeRecorder which just stores the errors encountered
 * in a list.
 */
public class ErrorAndTypeRecorderStorer implements ErrorAndTypeRecorder
{
    private final List<StyledString> errorMessages = new ArrayList<>();
    private final IdentityHashMap<Expression, TypeExp> types = new IdentityHashMap<>();

    @Override
    public <E> void recordError(E src, StyledString error)
    {
        errorMessages.add(error);
    }

    @Override
    public <EXPRESSION extends StyledShowable> void recordInformation(EXPRESSION src, Pair<StyledString, @Nullable QuickFix<EXPRESSION>> error)
    {
    }

    @Override
    public <EXPRESSION extends StyledShowable> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION>> quickFixes)
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
        types.put(expression, typeExp);
        return typeExp;
    }

    // Don't require @Recorded on src
    @SuppressWarnings("recorded")
    @Override
    public <T> @Nullable T recordLeftError(TypeManager typeManager, FunctionLookup functionLookup, Expression src, Either<TypeConcretisationError, T> errorOrVal)
    {
        return ErrorAndTypeRecorder.super.recordLeftError(typeManager, functionLookup, src, errorOrVal);
    }
}
