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

package xyz.columnal.utility.adt;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.Covariant;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.function.ConsumerInt;
import xyz.columnal.utility.function.ExConsumer;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.FunctionInt;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Immutable Either class.  Note that A and B are allowed to be Nullable.
 */
@Covariant({0, 1})
public sealed interface Either<A, B> permits Either.Left, Either.Right, ComparableEither
{    
    public static <A, B> Either<A, B> left(A a)
    {
        return new Left<>(a);
    }
    
    public static <A, B> Either<A, B> right(B b)
    {
        return new Right<>(b);
    }

    public static <A, B> ImmutableList<B> getRights(ImmutableList<Either<A, B>> eithers)
    {
        return eithers.stream().flatMap(e -> e.<Stream<B>>either(a -> Stream.<B>of(), b -> Stream.<B>of(b))).collect(ImmutableList.<B>toImmutableList());
    }

    public <R> R either(Function<? super A, R> withLeft, Function<? super B, R> withRight);

    public <R> R eitherInt(FunctionInt<? super A, R> withLeft, FunctionInt<? super B, R> withRight) throws InternalException;

    public <R> R eitherEx(ExFunction<? super A, R> withLeft, ExFunction<? super B, R> withRight) throws InternalException, UserException;

    public <R, E1 extends Exception, E2 extends Exception> R eitherEx2(FunctionEx2<? super A, R, E1, E2> withLeft, FunctionEx2<? super B, R, E1, E2> withRight) throws E1, E2;

    public <R, E1 extends Exception, E2 extends Exception, E3 extends Exception> R eitherEx3(FunctionEx3<? super A, R, E1, E2, E3> withLeft, FunctionEx3<? super B, R, E1, E2, E3> withRight) throws E1, E2, E3;
    
    public static interface FunctionEx2<T, R, E1 extends Exception, E2 extends Exception>
    {
        public R apply(T t) throws E1, E2;
    }

    public static interface FunctionEx3<T, R, E1 extends Exception, E2 extends Exception, E3 extends Exception>
    {
        public R apply(T t) throws E1, E2, E3;
    }

    public void either_(Consumer<? super A> withLeft, Consumer<? super B> withRight);

    public void eitherInt_(ConsumerInt<? super A> withLeft, ConsumerInt<? super B> withRight) throws InternalException;

    @SuppressWarnings("nullness") // No annotation to explain this is safe
    public void eitherEx_(ExConsumer<? super A> withLeft, ExConsumer<? super B> withRight) throws InternalException, UserException;

    // Bit like liftA2/liftM2 for Either monad, but it does examine both Eithers
    // and it concatenates the errors rather than just using the first one
    // Right is only returned if both inputs are right, otherwise Left will be returned.
    public static <E, A, B, C> Either<List<E>, C> combineConcatError(Either<List<E>, ? extends A> ea, Either<List<E>, ? extends B> eb, BiFunction<A, B, C> combine)
    {
        return ea.either(errsA -> Either.<List<E>, C>left(eb.<List<E>>either(errsB -> concat(errsA, errsB), bx -> errsA)),
                  ax -> eb.either(errsB -> Either.<List<E>, C>left(errsB), bx -> Either.<List<E>, C>right(combine.apply(ax, bx))));
    }

    private static <E> List<E> concat(List<E> errsA, List<E> errsB)
    {
        ImmutableList.Builder<E> b = ImmutableList.builderWithExpectedSize(errsA.size() + errsB.size());
        b.addAll(errsA);
        b.addAll(errsB);
        return b.build();
    }

    @SuppressWarnings("nullness")
    public static <E, R, T> Either<E, ImmutableList<R>> mapMInt(List<T> xs, FunctionInt<? super T, Either<E, R>> applyOne) throws InternalException
    {
        ImmutableList.Builder<R> r = ImmutableList.builderWithExpectedSize(xs.size());
        for (T x : xs)
        {
            Either<E, R> y = applyOne.apply(x);
            switch (y)
            {
                case Left<E, R> left -> {return Either.left(left.a);}
                case Right<E, R> right -> r.add(right.b);
                default -> {} // Null impossible
            }
        }
        return Either.right(r.build());
    }

    // Maps the items to Either, but stops at the first Left and returns it.  If no Lefts, returns all the Rights as list.
    @SuppressWarnings("nullness")
    public static <E, R, T> Either<E, ImmutableList<R>> mapMEx(List<T> xs, ExFunction<? super T, Either<E, R>> applyOne) throws InternalException, UserException
    {
        ImmutableList.Builder<R> r = ImmutableList.builderWithExpectedSize(xs.size());
        for (T x : xs)
        {
            Either<E, R> y = applyOne.apply(x);
            switch (y)
            {
                case Left<E, R> left -> {return Either.left(left.a);}
                case Right<E, R> right -> r.add(right.b);
                default -> {} // Null impossible
            }
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
            switch (y)
            {
                case Left<E, R> left -> {return Either.left(left.a);}
                case Right<E, R> right -> r.add(right.b);
                default -> {} // Null impossible
            }
        }
        return Either.right(r.build());
    }

    // Equivalent to either(Either::left, Either.right . applyRight)

    public default <R> Either<A, R> map(Function<? super B, R> applyRight)
    {
        return either(Either::left, b -> Either.right(applyRight.apply(b)));
    }

    // Equivalent to eitherInt(Either::left, Either.right . applyRight)
    public default <R> Either<A, R> mapInt(FunctionInt<? super B, R> applyRight) throws InternalException
    {
        return eitherInt(Either::left, b -> Either.right(applyRight.apply(b)));
    }

    // Equivalent to eitherEx(Either::left, Either.right . applyRight)

    public default <R> Either<A, R> mapEx(ExFunction<? super B, R> applyRight) throws InternalException, UserException
    {
        return eitherEx(Either::left, b -> Either.right(applyRight.apply(b)));
    }
    
    // Equivalent to either(Either::left, applyRight)
    public default <R> Either<A, R> flatMap(Function<? super B, Either<A, R>> bind)
    {
        return either(Either::left, bind::apply);
    }

    // Equivalent to eitherInt(Either::left, applyRight)
    public default <R> Either<A, R> flatMapInt(FunctionInt<? super B, Either<A, R>> bind) throws InternalException
    {
        return eitherInt(Either::left, bind::apply);
    }
    // Equivalent to eitherEx(Either::left, applyRight)

    public default <R> Either<A, R> flatMapEx(ExFunction<? super B, Either<A, R>> bind) throws InternalException, UserException
    {
        return eitherEx(Either::left, bind::apply);
    }
    

    //Use either/either_ instead if at all possible
    public default A getLeft(String error) throws InternalException
    {
        return eitherInt(a -> a, b -> {throw new InternalException(error);});
    }

    //Use either/either_ instead if at all possible
    public default B getRight(String error) throws InternalException
    {
        return eitherInt(a -> {throw new InternalException(error);}, b -> b);
    }

    //Use either/either_ instead if at all possible
    public boolean isLeft();

    //Use either/either_ instead if at all possible
    public boolean isRight();
    

    // Equivalent to either(l -> null, r -> r), but saves adding the annoying
    // type annotations.
    public default @Nullable B leftToNull()
    {
        return this.<@Nullable B>either(l -> null, r -> r);
    }
    
    public default void ifLeft(Consumer<A> withLeft)
    {
        either_(withLeft, b -> {});
    }

    public default void ifRight(Consumer<B> withRight)
    {
        either_(a -> {}, withRight);
    }

    public default <C, D> Either<C, D> mapBoth(Function<A, C> withLeft, Function <B, D> withRight)
    {
        return either(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }

    public default  <C, D> Either<C, D> mapBothInt(FunctionInt<A, C> withLeft, FunctionInt<B, D> withRight) throws InternalException
    {
        return eitherInt(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }
    
    public default <C, D> Either<C, D> mapBothEx(ExFunction<A, C> withLeft, ExFunction<B, D> withRight) throws InternalException, UserException
    {
        return eitherEx(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }

    public default <C, D, E1 extends Exception, E2 extends Exception> Either<C, D> mapBothEx2(FunctionEx2<A, C, E1, E2> withLeft, FunctionEx2<B, D, E1, E2> withRight) throws E1, E2
    {
        return this.<Either<C, D>, E1, E2>eitherEx2(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }

    public default <C, D, E1 extends Exception, E2 extends Exception, E3 extends Exception> Either<C, D> mapBothEx3(FunctionEx3<A, C, E1, E2, E3> withLeft, FunctionEx3<B, D, E1, E2, E3> withRight) throws E1, E2, E3
    {
        return this.<Either<C, D>, E1, E2, E3>eitherEx3(a -> Either.<C, D>left(withLeft.apply(a)), b -> Either.<C, D>right(withRight.apply(b)));
    }

    // If the value in the either is null, return null, else return a new either without the nullable qualifier
    public static <A, B> @Nullable Either<@NonNull A, @NonNull B> surfaceNull(Either<@Nullable A, @Nullable B> e)
    {
        return e.<@Nullable Either<@NonNull A, @NonNull B>>either((@Nullable A l) -> l == null ? null : Either.<@NonNull A, @NonNull B>left(l), (@Nullable B r) -> r == null ? null : Either.<@NonNull A, @NonNull B>right(r));
    }
    
    public sealed static class Left<A, B> implements Either<A, B> permits ComparableEither.Left
    {
        public final A a;

        // Use Either.left
        Left(A a)
        {
            this.a = a;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (!(o instanceof Left<?, ?> left)) return false;

            return Objects.equals(a, left.a);
        }

        @Override
        public int hashCode()
        {
            return a != null ? a.hashCode() : 0;
        }

        @Override
        public <R> R either(Function<? super A, R> withLeft, Function<? super B, R> withRight)
        {
            return withLeft.apply(a);
        }

        @Override
        public <R> R eitherInt(FunctionInt<? super A, R> withLeft, FunctionInt<? super B, R> withRight) throws InternalException
        {
            return withLeft.apply(a);
        }

        @Override
        public <R> R eitherEx(ExFunction<? super A, R> withLeft, ExFunction<? super B, R> withRight) throws InternalException, UserException
        {
            return withLeft.apply(a);
        }

        @Override
        public <R, E1 extends Exception, E2 extends Exception> R eitherEx2(FunctionEx2<? super A, R, E1, E2> withLeft, FunctionEx2<? super B, R, E1, E2> withRight) throws E1, E2
        {
            return withLeft.apply(a);
        }

        @Override
        public <R, E1 extends Exception, E2 extends Exception, E3 extends Exception> R eitherEx3(FunctionEx3<? super A, R, E1, E2, E3> withLeft, FunctionEx3<? super B, R, E1, E2, E3> withRight) throws E1, E2, E3
        {
            return withLeft.apply(a);
        }

        @Override
        public void either_(Consumer<? super A> withLeft, Consumer<? super B> withRight)
        {
            withLeft.accept(a);
        }

        @Override
        public void eitherInt_(ConsumerInt<? super A> withLeft, ConsumerInt<? super B> withRight) throws InternalException
        {
            withLeft.accept(a);
        }

        @Override
        public void eitherEx_(ExConsumer<? super A> withLeft, ExConsumer<? super B> withRight) throws InternalException, UserException
        {
            withLeft.accept(a);
        }

        @Override
        public boolean isLeft()
        {
            return true;
        }

        @Override
        public boolean isRight()
        {
            return false;
        }
    }

    public sealed static class Right<A, B> implements Either<A, B> permits ComparableEither.Right
    {
        public final B b;

        // Use Either.right
        Right(B b)
        {
            this.b = b;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (!(o instanceof Right<?, ?> right)) return false;

            return Objects.equals(b, right.b);
        }

        @Override
        public int hashCode()
        {
            return b != null ? b.hashCode() : 0;
        }

        @Override
        public <R> R either(Function<? super A, R> withLeft, Function<? super B, R> withRight)
        {
            return withRight.apply(b);
        }

        @Override
        public <R> R eitherInt(FunctionInt<? super A, R> withLeft, FunctionInt<? super B, R> withRight) throws InternalException
        {
            return withRight.apply(b);
        }

        @Override
        public <R> R eitherEx(ExFunction<? super A, R> withLeft, ExFunction<? super B, R> withRight) throws InternalException, UserException
        {
            return withRight.apply(b);
        }

        @Override
        public <R, E1 extends Exception, E2 extends Exception> R eitherEx2(FunctionEx2<? super A, R, E1, E2> withLeft, FunctionEx2<? super B, R, E1, E2> withRight) throws E1, E2
        {
            return withRight.apply(b);
        }

        @Override
        public <R, E1 extends Exception, E2 extends Exception, E3 extends Exception> R eitherEx3(FunctionEx3<? super A, R, E1, E2, E3> withLeft, FunctionEx3<? super B, R, E1, E2, E3> withRight) throws E1, E2, E3
        {
            return withRight.apply(b);
        }

        @Override
        public void either_(Consumer<? super A> withLeft, Consumer<? super B> withRight)
        {
            withRight.accept(b);
        }

        @Override
        public void eitherInt_(ConsumerInt<? super A> withLeft, ConsumerInt<? super B> withRight) throws InternalException
        {
            withRight.accept(b);
        }

        @Override
        public void eitherEx_(ExConsumer<? super A> withLeft, ExConsumer<? super B> withRight) throws InternalException, UserException
        {
            withRight.accept(b);
        }

        @Override
        public boolean isLeft()
        {
            return false;
        }

        @Override
        public boolean isRight()
        {
            return true;
        }
    }
}
