package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.Expression.LocationInfo;
import test.TestUtil.SingleTableLookup;
import records.transformations.expression.Expression._test_TypeVary;
import records.transformations.expression.TypeState;
import records.typeExp.TypeExp;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenTypecheckFail.TypecheckInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by neil on 10/12/2016.
 */
public class GenTypecheckFail extends Generator<TypecheckInfo>
{
    private GenExpressionValueForwards gen;

    @SuppressWarnings("initialization")
    public GenTypecheckFail()
    {
        super(TypecheckInfo.class);
    }

    public static class TypecheckInfo extends SingleTableLookup implements ColumnLookup
    {
        public final Expression original;
        public final RecordSet recordSet;
        public final List<Expression> expressionFailures;
        public final GenExpressionValueForwards gen;
        public final TypeManager typeManager;

        public TypecheckInfo(Expression original, RecordSet recordSet, List<Expression> expressionFailures, TypeManager typeManager, GenExpressionValueForwards gen)
        {
            super(recordSet);
            this.original = original;
            this.recordSet = recordSet;
            this.expressionFailures = expressionFailures;
            this.typeManager = typeManager;
            this.gen = gen;
        }

        public String getDisplay(Expression expression) throws InternalException, UserException
        {
            StringBuilder s = new StringBuilder("Expression : " + expression);
            s.append(" col types: ");
            @OnThread(Tag.Any) List<Column> columns = recordSet.getColumns();
            for (int i = 0; i < columns.size(); i++)
            {
                Column column = columns.get(i);
                s.append(i + "|" + column.getType().toString() + "  ");
            }
            s.append("(Original: " + original + ")");
            return s.toString();
        }
    }


    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    @SuppressWarnings("nullness")
    public TypecheckInfo generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        gen = new GenExpressionValueForwards();
        ExpressionValue valid = gen.generate(r, generationStatus);
        Expression expression = valid.expression;
        try
        {
            if (null == expression.check(valid, new TypeState(valid.typeManager), LocationInfo.UNIT_DEFAULT, TestUtil.excOnError()))
                throw new RuntimeException("Original did not type check: " + expression);
        }
        catch (InternalException | UserException | RuntimeException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return getTypecheckInfo(r, gen, valid.typeManager, expression);
    }

    @SuppressWarnings("nullness") // Some problem with Collectors.toList
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    private TypecheckInfo getTypecheckInfo(final SourceOfRandomness r, final GenExpressionValueForwards gen, TypeManager typeManager, Expression expression)
    {
        try
        {
            List<Expression> failures = expression._test_allMutationPoints().<@Nullable Expression>map(p -> {
                try
                {
                    @Nullable Expression failedCopy = p.getFirst()._test_typeFailure(r.toJDKRandom(), new _test_TypeVary()
                    {
                        @Override
                        @OnThread(value = Tag.Simulation, ignoreParent = true)
                        public Expression getDifferentType(@Nullable TypeExp type) throws InternalException, UserException
                        {
                            if (type == null)
                                throw new RuntimeException("Getting different type than failure?");
                            DataType newType = pickTypeOtherThan(type, r);
                            //System.err.println("Changed old type " + type + " into " + newType + " when replacing " + p.getFirst());
                            Pair<List<@Value Object>, Expression> replacement = gen.makeOfType(newType);
                            // Special case: don't let it be empty array because it may type check
                            // against type even though we don't want it to:
                            while (replacement.getSecond().equals(new ArrayExpression(ImmutableList.of())))
                            {
                                replacement = gen.makeOfType(newType);
                            }
                            return replacement.getSecond();
                        }

                        @Override
                        @OnThread(value = Tag.Simulation, ignoreParent = true)
                        public Expression getAnyType() throws UserException, InternalException
                        {
                            return gen.makeOfType(pickType(r)).getSecond();
                        }

                        @Override
                        @OnThread(value = Tag.Simulation, ignoreParent = true)
                        public Expression getNonNumericType() throws InternalException, UserException
                        {
                            DataType newType = pickNonNumericType(r);
                            //System.err.println("Changed old type " + type + " into " + newType + " when replacing " + p.getFirst());
                            return gen.makeOfType(newType).getSecond();
                        }

                        @Override
                        @OnThread(value = Tag.Simulation, ignoreParent = true)
                        public Expression getType(Predicate<DataType> mustMatch) throws InternalException, UserException
                        {
                            for (int i = 0; i < 100; i++)
                            {
                                DataType newType = pickType(r);
                                if (mustMatch.test(newType))
                                    return gen.makeOfType(newType).getSecond();
                            }
                            throw new RuntimeException("Type predicate too hard to satisfy");
                        }

                        @Override
                        @OnThread(value = Tag.Simulation, ignoreParent = true)
                        public List<Expression> getTypes(int amount, ExFunction<List<DataType>, Boolean> mustMatch) throws InternalException, UserException
                        {
                            for (int i = 0; i < 100; i++)
                            {
                                List<DataType> types = new ArrayList<>();
                                for (int j = 0; j < amount; j++)
                                    types.add(pickType(r));
                                if (mustMatch.apply(types))
                                {
                                    List<Expression> expressions = new ArrayList<>();
                                    for (int j = 0; j < amount; j++)
                                        expressions.add(gen.makeOfType(types.get(j)).getSecond());
                                    return expressions;
                                }
                            }
                            throw new RuntimeException("Type predicate too hard to satisfy");
                        }

                        @SuppressWarnings("recorded")
                        @Override
                        public Expression makeArrayExpression(ImmutableList<Expression> items)
                        {
                            return new ArrayExpression(items);
                        }

                        @Override
                        public TypeManager getTypeManager()
                        {
                            return DummyManager.make().getTypeManager();
                        }
                    }, DummyManager.make().getUnitManager());
                    if (failedCopy == null)
                        return null;
                    else
                    {
                        Expression failedFull = p.getSecond().apply(failedCopy);
                        if (failedFull.toString().equals(expression.toString()))
                        {
                            System.err.println("Error in GenTypecheckFail: converted expression to itself");
                        }
                        return failedFull;
                    }
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }).<@NonNull Expression>filter(p -> p != null).collect(Collectors.toList());

            //for (Expression failure : failures)
            //{
            //    System.err.println("Transformed " + valid.expression + " into failure " + failure);
            //}

            return new TypecheckInfo(expression, gen.getRecordSet(), failures, typeManager, gen);
        }
        catch (InternalException | UserException | RuntimeException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public List<TypecheckInfo> doShrink(SourceOfRandomness random, TypecheckInfo larger)
    {
        return larger.original._test_childMutationPoints().map(p -> p.getFirst())
            .map(e -> getTypecheckInfo(random, larger.gen, larger.typeManager, e)).collect(Collectors.<TypecheckInfo>toList());
    }

    private DataType pickType(SourceOfRandomness r)
    {
        return gen.makeType(r);
    }

    private DataType pickTypeOtherThan(@Nullable TypeExp type, SourceOfRandomness r) throws InternalException, UserException
    {
        DataType picked;
        do
        {
            picked = pickType(r);
        }
        while (type != null && TypeExp.unifyTypes(type, TypeExp.fromDataType(null, picked)).isRight());
        return picked;
    }

    private DataType pickNonNumericType(SourceOfRandomness r)
    {
        DataType picked;
        do
        {
            picked = pickType(r);
        }
        while (picked.isNumber());
        return picked;

    }
}
