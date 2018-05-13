package utility;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.Covariant;
import records.error.InternalException;
import records.error.UserException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Immutable Either class
 */
@Covariant({0, 1})
public class Either<A, B>
{
    private final @Nullable A a;
    private final @Nullable B b;
    private final boolean isA;

    // For ComparableEither, this is package-private
    Either(@Nullable A a, @Nullable B b, boolean isA)
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
    public <R> R eitherEx(ExFunction<? super A, R> withLeft, ExFunction<? super B, R> withRight) throws InternalException, UserException
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
    
    @SuppressWarnings("nullness")
    public static <E, R, T> Either<E, List<R>> mapMInt(List<T> xs, FunctionInt<? super T, Either<E, R>> applyOne) throws InternalException
    {
        List<R> r = new ArrayList<>(xs.size());
        for (T x : xs)
        {
            Either<E, R> y = applyOne.apply(x);
            if (y.a != null)
                return Either.left(y.a);
            else
                r.add(y.b);
        }
        return Either.right(r);
    }

    // Maps the items to Either, but stops at the first Left and returns it.  If no Lefts, returns all the Rights as list.
    @SuppressWarnings("nullness")
    public static <E, R, T> Either<E, List<R>> mapMEx(List<T> xs, ExFunction<? super T, Either<E, R>> applyOne) throws InternalException, UserException
    {
        List<R> r = new ArrayList<>(xs.size());
        for (T x : xs)
        {
            Either<E, R> y = applyOne.apply(x);
            if (y.a != null)
                return Either.left(y.a);
            else
                r.add(y.b);
        }
        return Either.right(r);
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

    // Equivalent to either(Either::left, Either.right . applyRight)
    @SuppressWarnings("nullness")
    public <R> Either<A, R> mapInt(FunctionInt<? super B, R> applyRight) throws InternalException
    {
        if (isA)
            return Either.left(a);
        else
            return Either.right(applyRight.apply(b));
    }

    // Equivalent to either(Either::left, Either.right . applyRight)
    @SuppressWarnings("nullness")
    public <R> Either<A, R> mapEx(ExFunction<? super B, R> applyRight) throws InternalException, UserException
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

    // Equivalent to either(Either::left, applyRight)
    @SuppressWarnings("nullness")
    public <R> Either<A, R> flatMapInt(FunctionInt<? super B, Either<A, R>> bind) throws InternalException
    {
        if (isA)
            return Either.left(a);
        else
            return bind.apply(b);
    }
    // Equivalent to either(Either::left, applyRight)
    @SuppressWarnings("nullness")
    public <R> Either<A, R> flatMapEx(ExFunction<? super B, Either<A, R>> bind) throws InternalException, UserException
    {
        if (isA)
            return Either.left(a);
        else
            return bind.apply(b);
    }
    

    //Use either/either_ instead if at all possible
    public A getLeft(String error) throws InternalException
    {
        if (a != null)
            return a;
        else
            throw new InternalException(error);
    }

    //Use either/either_ instead if at all possible
    public B getRight(String error) throws InternalException
    {
        if (b != null)
            return b;
        else
            throw new InternalException(error);
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

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Either<?, ?> either = (Either<?, ?>) o;

        if (isA != either.isA) return false;
        if (a != null ? !a.equals(either.a) : either.a != null) return false;
        return b != null ? b.equals(either.b) : either.b == null;
    }

    @Override
    public int hashCode()
    {
        int result = a != null ? a.hashCode() : 0;
        result = 31 * result + (b != null ? b.hashCode() : 0);
        result = 31 * result + (isA ? 1 : 0);
        return result;
    }
    
    @Override
    public String toString()
    {
        return isA ? ("Left(" + a + ")") : ("Right(" + b + ")"); 
    }

    // Equivalent to either(l -> null, r -> r), but saves adding the annoying
    // type annotations.
    public @Nullable B leftToNull()
    {
        return this.<@Nullable B>either(l -> null, r -> r);
    }
    
    public void ifLeft(Consumer<A> withLeft)
    {
        either_(withLeft, b -> {});
    }

    public void ifRight(Consumer<B> withRight)
    {
        either_(a -> {}, withRight);
    }

    public <C, D> Either<C, D> mapBoth(Function<A, C> withLeft, Function <B, D> withRight)
    {
        return either(a -> Either.left(withLeft.apply(a)), b -> Either.right(withRight.apply(b)));
    }

    public <C, D> Either<C, D> mapBothInt(FunctionInt<A, C> withLeft, FunctionInt<B, D> withRight) throws InternalException
    {
        return eitherInt(a -> Either.left(withLeft.apply(a)), b -> Either.right(withRight.apply(b)));
    }
    
    public <C, D> Either<C, D> mapBothEx(ExFunction<A, C> withLeft, ExFunction<B, D> withRight) throws InternalException, UserException
    {
        return eitherEx(a -> Either.left(withLeft.apply(a)), b -> Either.right(withRight.apply(b)));
    }

    // If the value in the either is null, return null, else return a new either without the nullable qualifier
    public static <A, B> @Nullable Either<@NonNull A, @NonNull B> surfaceNull(Either<@Nullable A, @Nullable B> e)
    {
        return e.<@Nullable Either<@NonNull A, @NonNull B>>either((@Nullable A l) -> l == null ? null : Either.<@NonNull A, @NonNull B>left(l), (@Nullable B r) -> r == null ? null : Either.<@NonNull A, @NonNull B>right(r));
    }
}
