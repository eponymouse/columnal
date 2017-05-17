package utility;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Immutable Either class with 3 options
 */
public class Either3<A, B, C>
{
    private final @Nullable A a;
    private final @Nullable B b;
    private final @Nullable C c;
    private final int abc; // 0 =a, 1 = b, 2 = c

    private Either3(@Nullable A a, @Nullable B b, @Nullable C c, int abc)
    {
        this.a = a;
        this.b = b;
        this.c = c;
        this.abc = abc;
    }

    public static <A, B, C> Either3<A, B, C> left(A a)
    {
        return new Either3<>(a, null, null,0);
    }

    public static <A, B, C> Either3<A, B, C> middle(B b)
    {
        return new Either3<>(null, b, null, 1);
    }

    public static <A, B, C> Either3<A, B, C> right(C c)
    {
        return new Either3<>(null, null, c, 2);
    }
}
