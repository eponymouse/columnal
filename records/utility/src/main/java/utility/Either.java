package utility;

import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.Nullable;

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
}
