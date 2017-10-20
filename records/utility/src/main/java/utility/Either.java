package utility;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Immutable Either class
 */
public class Either<A, B>
{
    private final @Nullable A a;
    private final @Nullable B b;
    private final boolean isA;

    private Either(@Nullable A a, @Nullable B b, boolean isA)
    {
        this.a = a;
        this.b = b;
        this.isA = isA;
    }

    public static <A, B> Either<A, B> left(A a)
    {
        return new Either<>(a, null, true);
    }

    public static <A, B> Either<A, B> right(B b)
    {
        return new Either<>(null, b, false);
    }

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public <R> R either(Function<? super A, R> withLeft, Function<? super B, R> withRight)
    {
        if (isA)
            return withLeft.apply(a);
        else
            return withRight.apply(b);
    }

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public <R> R eitherInt(FunctionInt<? super A, R> withLeft, FunctionInt<? super B, R> withRight) throws InternalException
    {
        if (isA)
            return withLeft.apply(a);
        else
            return withRight.apply(b);
    }

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public void either_(Consumer<? super A> withLeft, Consumer<? super B> withRight)
    {
        if (isA)
            withLeft.accept(a);
        else
            withRight.accept(b);
    }

    // Bit like liftA2/liftM2 for Either monad, but it does examine both Eithers
    // and it concatenates the errors rather than just using the first one
    // Right is only returned if both inputs are right, otherwise Left will be returned.
    public static <E, A, B, C> Either<List<E>, C> combineConcatError(Either<List<E>, ? extends A> ea, Either<List<E>, ? extends B> eb, BiFunction<A, B, C> combine)
    {
        return ea.either(errsA -> eb.either(errsB -> Either.left(Utility.concat(errsA, errsB)), bx -> Either.left(errsA)),
                  ax -> eb.either(errsB -> Either.left(errsB), bx -> Either.right(combine.apply(ax, bx))));
    }

    // Equivalent to either(Either::left, Either.right . applyRight)
    @SuppressWarnings("nullness")
    public <R> Either<A, R> map(Function<? super B, R> applyRight)
    {
        if (isA)
            return Either.left(a);
        else
            return Either.right(applyRight.apply(b));
    }

    // Equivalent to either(Either::left, applyRight)
    @SuppressWarnings("nullness")
    public <R> Either<A, R> flatMap(Function<? super B, Either<A, R>> bind)
    {
        if (isA)
            return Either.left(a);
        else
            return bind.apply(b);
    }

    //Use either/either_ instead if at all possible
    public A getLeft() throws InternalException
    {
        if (a != null)
            return a;
        else
            throw new InternalException("Getting left out of right");
    }

    //Use either/either_ instead if at all possible
    public B getRight() throws InternalException
    {
        if (b != null)
            return b;
        else
            throw new InternalException("Getting left out of right");
    }

    //Use either/either_ instead if at all possible
    public boolean isLeft()
    {
        return isA;
    }

    //Use either/either_ instead if at all possible
    public boolean isRight()
    {
        return !isA;
    }
}
