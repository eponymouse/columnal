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

package test.gen.backwards;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.*;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.DefineExpression.Definition;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorFlat;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

public class BackwardsFunction extends BackwardsProvider
{
    public BackwardsFunction(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of();
    }

    @SuppressWarnings("identifier")
    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of(() -> {
            Expression expression = parent.make(targetType, targetValue, maxLevels - 1);
            Functioned paramsAndBody = expression.visit(new Functioniser(new Random(r.nextLong()), targetType));
            
            String funcName = "func " + r.nextInt(1000000);
            Expression function;
            if (r.nextBoolean())
                function = paramsAndBody.turnFormalNamesIntoBody.apply(Utility.replicateM(paramsAndBody.callActualParameters.size(), () -> new ImplicitLambdaArg()));
            else
            {
                ImmutableList<String> paramNames = IntStream.range(0, paramsAndBody.callActualParameters.size()).mapToObj(n -> funcName + " param " + n).collect(ImmutableList.toImmutableList());
                function = new LambdaExpression(Utility.mapListI(paramNames, name -> IdentExpression.load(name)), paramsAndBody.turnFormalNamesIntoBody.apply(Utility.mapListI(paramNames, name -> IdentExpression.load(name))));
            }
            
            Either<@Recorded HasTypeExpression, Definition> definition = Either.right(new Definition(IdentExpression.load(funcName), function));
            return DefineExpression.unrecorded(paramsAndBody.actualParameterTypes == null ? ImmutableList.of(definition)
                : ImmutableList.of(Either.left(new HasTypeExpression(funcName, new TypeLiteralExpression(TypeExpression.fromDataType(DataType.function(paramsAndBody.actualParameterTypes, targetType))))), definition)
                , new CallExpression(IdentExpression.load(funcName), paramsAndBody.callActualParameters));
        });
    }
    
    class Functioned
    {
        private final ImmutableList<Expression> callActualParameters;
        private final @Nullable ImmutableList<DataType> actualParameterTypes;
        private final Function<ImmutableList<Expression>, Expression> turnFormalNamesIntoBody;

        public Functioned(ImmutableList<Expression> callActualParameters, @Nullable ImmutableList<DataType> actualParameterTypes, Function<ImmutableList<Expression>, Expression> turnFormalNamesIntoBody)
        {
            this.callActualParameters = callActualParameters;
            this.actualParameterTypes = actualParameterTypes;
            this.turnFormalNamesIntoBody = turnFormalNamesIntoBody;
        }
    }
    
    // Gives back list of expressions, and a function that takes replacements for those expressions and reassembles the original.
    // The idea being that you swap the originals for function parameters.
    @OnThread(Tag.Simulation)
    private class Functioniser extends ExpressionVisitorFlat<Functioned>
    {
        private final Random random;
        private final DataType targetType;

        public Functioniser(Random random, DataType targetType)
        {
            this.random = random;
            this.targetType = targetType;
        }
        
        // Given a list of expressions, picks N of them (1 <= N <= list size) and returns those expressions, plus a function that given replacements for those expressions,
        // calls make with the right ones in the list replaced.
        private Functioned pick(ImmutableList<Expression> expressions, @Nullable ImmutableList<DataType> paramTypes, Function<ImmutableList<Expression>, Expression> make)
        {
            int numPicked = 1 + r.nextInt(expressions.size() - 1);

            ArrayList<Boolean> picked = new ArrayList<>(Utility.concatI(Utility.replicate(numPicked, true), Utility.replicate(expressions.size() - numPicked, false)));
            Collections.shuffle(picked, random);
            
            ImmutableList.Builder<Expression> substitutions = ImmutableList.builder();
            ImmutableList.Builder<DataType> substitutedTypes = ImmutableList.builder();
            for (int i = 0; i < picked.size(); i++)
            {
                if (picked.get(i))
                {
                    substitutions.add(expressions.get(i));
                    if (paramTypes != null)
                        substitutedTypes.add(paramTypes.get(i));
                }
            }
            
            return new Functioned(substitutions.build(), paramTypes == null ? null : substitutedTypes.build(), subs -> {
                ImmutableList.Builder<Expression> substituted = ImmutableList.builder();
                int nextParam = 0;
                for (int i = 0; i < picked.size(); i++)
                {
                    if (picked.get(i))
                        substituted.add(subs.get(nextParam++));
                    else
                        substituted.add(expressions.get(i));
                }
                return make.apply(substituted.build());
            });
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        protected Functioned makeDef(Expression expression)
        {
            return new Functioned(ImmutableList.of(new BooleanLiteral(true)), ImmutableList.of(DataType.BOOLEAN), ps -> IfThenElseExpression.unrecorded(ps.get(0), expression, expression));
        }

        // Note: can't do if-then-else because its condition may define variables that are used in the body.
        /*
        @Override
        public Pair<ImmutableList<Expression>, Function<ImmutableList<Expression>, Expression>> ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
        {
            return new Pair<>(ImmutableList.of(condition), ps -> new IfThenElseExpression(ps.get(0), thenExpression, elseExpression));
        }
        */

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public Functioned addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops)
        {
            return pick(expressions, ImmutableList.copyOf(Utility.replicate(expressions.size(), targetType)), es -> new AddSubtractExpression(es, ops));
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public Functioned notEqual(NotEqualExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
        {
            return pick(ImmutableList.of(lhs, rhs), null, es -> new NotEqualExpression(es.get(0), es.get(1)));
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public Functioned divide(DivideExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
        {
            return pick(ImmutableList.of(lhs, rhs), null, es -> new DivideExpression(es.get(0), es.get(1)));
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public Functioned or(OrExpression self, ImmutableList<@Recorded Expression> expressions)
        {
            return pick(expressions, ImmutableList.copyOf(Utility.replicate(expressions.size(), DataType.BOOLEAN)), es -> new OrExpression(es));
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public Functioned comparison(ComparisonExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators)
        {
            return pick(expressions, null, es -> new ComparisonExpression(es, operators));
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public Functioned multiply(TimesExpression self, ImmutableList<@Recorded Expression> expressions)
        {
            return pick(expressions, null, es -> new TimesExpression(es));
        }

        // TODO lots more, call pick
    }
}
