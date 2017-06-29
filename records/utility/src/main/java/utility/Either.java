package utility;

import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.Nullable;

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
    public <R> R either(Function<A, R> withLeft, Function<B, R> withRight)
    {
        if (isA)
            return withLeft.apply(a);
        else
            return withRight.apply(b);
    }

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public void either_(Consumer<A> withLeft, Consumer<B> withRight)
    {
        if (isA)
            withLeft.accept(a);
        else
            withRight.accept(b);
    }

    // Bit like liftA2/liftM2 for Either monad, but it does examine both Eithers
    // and it concatenates the errors rather than just using the first one
    // Right is only returned if both inputs are right, otherwise Left will be returned.
    public static <E, A, B, C> Either<List<E>, C> combineConcatError(Either<List<E>, A> ea, Either<List<E>, B> eb, BiFunction<A, B, C> combine)
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
}
