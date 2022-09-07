/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.utility;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.Covariant;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

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

    public static <A, B> ImmutableList<B> getRights(ImmutableList<Either<A, B>> eithers)
    {
        return eithers.stream().flatMap(e -> e.<Stream<B>>either(a -> Stream.<B>of(), b -> Stream.<B>of(b))).collect(ImmutableList.<B>toImmutableList());
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
    public <R, E1 extends Exception, E2 extends Exception> R eitherEx2(FunctionEx2<? super A, R, E1, E2> withLeft, FunctionEx2<? super B, R, E1, E2> withRight) throws E1, E2
    {
        if (isA)
            return withLeft.apply(a);
        else
            return withRight.apply(b);
    }

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public <R, E1 extends Exception, E2 extends Exception, E3 extends Exception> R eitherEx3(FunctionEx3<? super A, R, E1, E2, E3> withLeft, FunctionEx3<? super B, R, E1, E2, E3> withRight) throws E1, E2, E3
    {
        if (isA)
            return withLeft.apply(a);
        else
            return withRight.apply(b);
    }
    
    public static interface FunctionEx2<T, R, E1 extends Exception, E2 extends Exception>
    {
        public R apply(T t) throws E1, E2;
    }

    public static interface FunctionEx3<T, R, E1 extends Exception, E2 extends Exception, E3 extends Exception>
    {
        public R apply(T t) throws E1, E2, E3;
    }

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public void either_(Consumer<? super A> withLeft, Consumer<? super B> withRight)
    {
        if (isA)
            withLeft.accept(a);
        else
            withRight.accept(b);
    }

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public void eitherInt_(ConsumerInt<? super A> withLeft, ConsumerInt<? super B> withRight) throws InternalException
    {
        if (isA)
            withLeft.accept(a);
        else
            withRight.accept(b);
    }

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public void eitherEx_(ExConsumer<? super A> withLeft, ExConsumer<? super B> withRight) throws InternalException, UserException
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
        return ea.either(errsA -> Either.<List<E>, C>left(eb.<List<E>>either(errsB -> Utility.<E>concat(errsA, errsB), bx -> errsA)),
                  ax -> eb.either(errsB -> Either.<List<E>, C>left(errsB), bx -> Either.<List<E>, C>right(combine.apply(ax, bx))));
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
    public static <E, R, T> Either<E, ImmutableList<R>> mapMEx(List<T> xs, ExFunction<? super T, Either<E, R>> applyOne) throws InternalException, UserException
    {
        ImmutableList.Builder<R> r = ImmutableList.builderWithExpectedSize(xs.size());
        for (T x : xs)
        {
            Either<E, R> y = applyOne.apply(x);
            if (y.a != null)
                return Either.left(y.a);
            else
                r.add(y.b);
        }
        return Either.right(r.build());
    }

    // Maps the items to Either, but stops at the first Left and returns it.  If no Lefts, returns all the Rights as list.
    @SuppressWarnings("nullness")
    public static <E, R, T> Either<E, ImmutableList<R>> mapM(List<T> xs, Function<? super T, Either<E, R>> applyOne)
    {
        ImmutableList.Builder<R> r = ImmutableList.builderWithExpectedSize(xs.size());
        for (T x : xs)
        {
            Either<E, R> y = applyOne.apply(x);
            if (y.a != null)
                return Either.left(y.a);
            else
                r.add(y.b);
        }
        return Either.right(r.build());
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
        return either(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }

    public <C, D> Either<C, D> mapBothInt(FunctionInt<A, C> withLeft, FunctionInt<B, D> withRight) throws InternalException
    {
        return eitherInt(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }
    
    public <C, D> Either<C, D> mapBothEx(ExFunction<A, C> withLeft, ExFunction<B, D> withRight) throws InternalException, UserException
    {
        return eitherEx(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }

    public <C, D, E1 extends Exception, E2 extends Exception> Either<C, D> mapBothEx2(FunctionEx2<A, C, E1, E2> withLeft, FunctionEx2<B, D, E1, E2> withRight) throws E1, E2
    {
        return this.<Either<C, D>, E1, E2>eitherEx2(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }

    public <C, D, E1 extends Exception, E2 extends Exception, E3 extends Exception> Either<C, D> mapBothEx3(FunctionEx3<A, C, E1, E2, E3> withLeft, FunctionEx3<B, D, E1, E2, E3> withRight) throws E1, E2, E3
    {
        return this.<Either<C, D>, E1, E2, E3>eitherEx3(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }

    // If the value in the either is null, return null, else return a new either without the nullable qualifier
    public static <A, B> @Nullable Either<@NonNull A, @NonNull B> surfaceNull(Either<@Nullable A, @Nullable B> e)
    {
        return e.<@Nullable Either<@NonNull A, @NonNull B>>either((@Nullable A l) -> l == null ? null : Either.<@NonNull A, @NonNull B>left(l), (@Nullable B r) -> r == null ? null : Either.<@NonNull A, @NonNull B>right(r));
    }
}
