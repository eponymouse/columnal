package test.gen.backwards;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import javafx.beans.property.adapter.ReadOnlyJavaBeanObjectPropertyBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.value.qual.IntRange;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.transformations.expression.visitor.ExpressionVisitorFlat;
import styled.StyledString;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
            Pair<ImmutableList<Expression>, Function<ImmutableList<Expression>, Expression>> paramsAndBody = expression.visit(new Functioniser(new Random(r.nextLong())));
            
            String funcName = "func " + r.nextInt(1000000);
            Expression function;
            if (r.nextBoolean())
                function = paramsAndBody.getSecond().apply(Utility.replicateM(paramsAndBody.getFirst().size(), () -> new ImplicitLambdaArg()));
            else
            {
                ImmutableList<String> paramNames = IntStream.range(0, paramsAndBody.getFirst().size()).mapToObj(n -> funcName + " param " + n).collect(ImmutableList.toImmutableList());
                function = new LambdaExpression(Utility.mapListI(paramNames, name -> new VarDeclExpression(name)), paramsAndBody.getSecond().apply(Utility.mapListI(paramNames, name -> new IdentExpression(name))));
            }
            
            //Either.left(new HasTypeExpression(funcName, new TypeLiteralExpression(TypeExpression.fromDataType(DataType.function(ImmutableList.of())))))
            return new DefineExpression(ImmutableList.of(Either.right(new EqualExpression(ImmutableList.of(new VarDeclExpression(funcName), function)))), new CallExpression(new IdentExpression(funcName), paramsAndBody.getFirst()));
        });
    }
    
    @OnThread(Tag.Any)
    private class Functioniser extends ExpressionVisitorFlat<Pair<ImmutableList<Expression>, Function<ImmutableList<Expression>, Expression>>>
    {
        private final Random random;

        public Functioniser(Random random)
        {
            this.random = random;
        }
        
        private Pair<ImmutableList<Expression>, Function<ImmutableList<Expression>, Expression>> pick(ImmutableList<Expression> expressions, Function<ImmutableList<Expression>, Expression> make)
        {
            int numPicked = 1 + r.nextInt(expressions.size() - 1);

            ArrayList<Boolean> picked = new ArrayList<>(Utility.concatI(Utility.replicate(numPicked, true), Utility.replicate(expressions.size() - numPicked, false)));
            Collections.shuffle(picked, random);
            
            ImmutableList.Builder<Expression> substitutions = ImmutableList.builder();
            for (int i = 0; i < picked.size(); i++)
            {
                if (picked.get(i))
                    substitutions.add(expressions.get(i));
            }
            
            return new Pair<>(substitutions.build(), subs -> {
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
        protected Pair<ImmutableList<Expression>, Function<ImmutableList<Expression>, Expression>> makeDef(Expression expression)
        {
            return new IfThenElseExpression(new BooleanLiteral(true), expression, expression).visit(this);
        }

        @Override
        public Pair<ImmutableList<Expression>, Function<ImmutableList<Expression>, Expression>> ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
        {
            return new Pair<>(ImmutableList.of(condition), ps -> new IfThenElseExpression(ps.get(0), thenExpression, elseExpression));
        }

        @Override
        public Pair<ImmutableList<Expression>, Function<ImmutableList<Expression>, Expression>> addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops)
        {
            return pick(expressions, es -> new AddSubtractExpression(es, ops));
        }
        
        // TODO lots more, call pick
    }
}
