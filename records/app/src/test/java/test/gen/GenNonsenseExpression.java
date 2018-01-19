package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.internal.generator.EnumGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.UserException;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.AndExpression;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.OrExpression;
import records.transformations.expression.RaiseExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TagExpression;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.UnfinishedExpression;
import records.transformations.expression.VarDeclExpression;
import records.transformations.expression.VarUseExpression;
import test.TestUtil;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Generates arbitrary expression which probably won't type check.
 * Useful for testing loading and saving of expressions.
 */
public class GenNonsenseExpression extends Generator<Expression>
{
    public GenNonsenseExpression()
    {
        super(Expression.class);
    }

    @Override
    public Expression generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return genDepth(sourceOfRandomness, 0, generationStatus);
    }

    private Expression genDepth(SourceOfRandomness r, int depth, GenerationStatus gs)
    {
        return genDepth(true, r, depth, gs);
    }

    private Expression genDepth(boolean tagAllowed, SourceOfRandomness r, int depth, GenerationStatus gs)
    {
        // Make terminals more likely as we get further down:
        if (r.nextInt(0, 3 - depth) == 0)
        {
            // Terminal:
            return genTerminal(r, gs);
        }
        else
        {
            // Non-terminal:
            return r.<Supplier<Expression>>choose(Arrays.<Supplier<Expression>>asList(
                () -> new NotEqualExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new EqualExpression(TestUtil.<Expression>makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs))),
                () -> {
                    List<Expression> expressions = TestUtil.<Expression>makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs));
                    List<ComparisonOperator> operators = r.nextBoolean() ? Arrays.asList(ComparisonOperator.GREATER_THAN, ComparisonOperator.GREATER_THAN_OR_EQUAL_TO) : Arrays.asList(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO);
                    @SuppressWarnings("unchecked")
                    Generator<ComparisonOperator> comparisonOperatorGenerator = (Generator) new EnumGenerator(ComparisonOperator.class)
                    {
                        @Override
                        public Enum<ComparisonOperator> generate(SourceOfRandomness random, GenerationStatus status)
                        {
                            return random.choose(operators);
                        }
                    };
                    return new ComparisonExpression(expressions, ImmutableList.<ComparisonOperator>copyOf(TestUtil.<ComparisonOperator>makeList(expressions.size() - 1, comparisonOperatorGenerator, r, gs)));
                },
                () -> new AndExpression(TestUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> new OrExpression(TestUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> new TimesExpression(TestUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> !tagAllowed ? genTerminal(r, gs) : new TagExpression(Either.left(TestUtil.makeString(r, gs).trim()), genDepth(r, depth + 1, gs)),
                () ->
                {
                    List<Expression> expressions = TestUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs));
                    @SuppressWarnings("unchecked")
                    Generator<Op> opGenerator = (Generator<Op>) (Generator<?>) new EnumGenerator(Op.class);
                    return new AddSubtractExpression(expressions, TestUtil.makeList(expressions.size() - 1, opGenerator, r, gs));
                },
                () -> new DivideExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new RaiseExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new CallExpression(TestUtil.generateVarName(r), null, TestUtil.makeList(r.nextInt(0, 2), new GenUnit(), r, gs), genDepth(true, r, depth + 1, gs)),
                () -> new MatchExpression(genDepth(false, r, depth + 1, gs), TestUtil.makeList(r, 1, 5, () -> genClause(r, gs, depth + 1))),
                () -> new ArrayExpression(ImmutableList.<Expression>copyOf(TestUtil.makeList(r, 0, 6, () -> genDepth(r, depth + 1, gs)))),
                () -> new TupleExpression(ImmutableList.<Expression>copyOf(TestUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs)))),
                () ->
                {
                    List<Expression> expressions = TestUtil.makeList(r, 3, 6, () -> genDepth(r, depth + 1, gs));
                    return new InvalidOperatorExpression(expressions, TestUtil.makeList(expressions.size() - 1, new GenRandomOp(), r, gs));
                }
            )).get();
        }
    }

    private Expression genTerminal(SourceOfRandomness r, GenerationStatus gs)
    {
        try
        {
            return r.<Expression>choose(Arrays.asList(
                new NumericLiteral(Utility.parseNumber(r.nextBigInteger(160).toString()), null), // TODO gen unit
                new BooleanLiteral(r.nextBoolean()),
                new StringLiteral(TestUtil.makeStringV(r, gs)),
                new ColumnReference(TestUtil.generateColumnId(r), ColumnReferenceType.CORRESPONDING_ROW),
                new VarUseExpression(TestUtil.generateVarName(r)),
                new UnfinishedExpression(TestUtil.makeUnfinished(r))
            ));
        }
        catch (UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private Function<MatchExpression, MatchExpression.MatchClause> genClause(SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return e -> e.new MatchClause(TestUtil.makeList(r, 1, 4, () -> genPattern(e, r, gs, depth)), genDepth(r, depth, gs));
    }

    private MatchExpression.Pattern genPattern(MatchExpression e, SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return new MatchExpression.Pattern(genPatternMatch(e, r, gs, depth), r.nextBoolean() ? null : genDepth(r, depth, gs));
    }

    private Expression genPatternMatch(MatchExpression e, SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return r.choose(Arrays.<Supplier<Expression>>asList(
            () -> genDepth(false, r, depth, gs),
            () -> new VarDeclExpression(TestUtil.makeUnquotedIdent(r, gs)),
            () ->
            {
                String constructorName = TestUtil.makeNonEmptyString(r, gs).trim();
                return new TagExpression(Either.left(constructorName), r.nextInt(0, 3 - depth) == 0 ? null : genPatternMatch(e, r, gs, depth + 1));
            }
        )).get();
    }

    @SuppressWarnings("nullness") // Some problem with Collectors.toList
    @Override
    public List<Expression> doShrink(SourceOfRandomness random, Expression larger)
    {
        return larger._test_childMutationPoints().map(p -> p.getFirst()).collect(Collectors.<Expression>toList());
    }

    /**
     * Note: this generator is stateful, to make sure it doesn't accidentally generate a series of of operators
     * which are valid!
     */
    private class GenRandomOp extends Generator<String>
    {
        int indexOfFirstOp = -1;

        public GenRandomOp()
        {
            super(String.class);
        }

        @Override
        public String generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            // To be invalid, can't pick only same group, so we just avoid picking first group again:
            List<List<String>> opGroups = new ArrayList<>(ImmutableList.of(
                ImmutableList.of("<", "<="),
                ImmutableList.of(">", ">="),
                ImmutableList.of("+", "-"),
                ImmutableList.of("*"),
                ImmutableList.of("/"),
                ImmutableList.of("<>"),
                ImmutableList.of("="),
                ImmutableList.of("&"),
                ImmutableList.of("|"),
                ImmutableList.of("^"),
                ImmutableList.of(",")
            ));
            if (indexOfFirstOp >= 0)
                opGroups.remove(indexOfFirstOp);

            int group = sourceOfRandomness.nextInt(opGroups.size());
            if (indexOfFirstOp < 0)
                indexOfFirstOp = group;

            return sourceOfRandomness.<@NonNull String>choose(opGroups.get(group));
        }
    }
}
