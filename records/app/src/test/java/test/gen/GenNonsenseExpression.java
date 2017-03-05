package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.internal.generator.EnumGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
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
import utility.Pair;
import utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
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
            return genTerminal(r);
        }
        else
        {
            // Non-terminal:
            return r.choose(Arrays.<Supplier<Expression>>asList(
                () -> new NotEqualExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new EqualExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> {
                    List<Expression> expressions = TestUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs));
                    List<ComparisonOperator> operators = r.nextBoolean() ? Arrays.asList(ComparisonOperator.GREATER_THAN, ComparisonOperator.GREATER_THAN_OR_EQUAL_TO) : Arrays.asList(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO);
                    return new ComparisonExpression(expressions, ImmutableList.copyOf(TestUtil.makeList(expressions.size() - 1, (Generator<ComparisonOperator>)(Generator<?>)new EnumGenerator(ComparisonOperator.class) {
                        @Override
                        public Enum<?> generate(SourceOfRandomness random, GenerationStatus status)
                        {
                            return random.choose(operators);
                        }
                    }, r, gs)));
                },
                () -> new AndExpression(TestUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> new OrExpression(TestUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> new TimesExpression(TestUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> !tagAllowed ? genTerminal(r) : TagExpression._testMake(TestUtil.makeString(r, gs), TestUtil.makeString(r, gs), genDepth(r, depth + 1, gs)),
                () ->
                {
                    List<Expression> expressions = TestUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs));
                    return new AddSubtractExpression(expressions, TestUtil.makeList(expressions.size() - 1, (Generator<Op>)(Generator<?>)new EnumGenerator(Op.class), r, gs));
                },
                () -> new DivideExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new RaiseExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new CallExpression(TestUtil.generateVarName(r), TestUtil.makeList(r.nextInt(0, 2), new GenUnit(), r, gs), genDepth(true, r, depth + 1, gs)),
                () -> new MatchExpression(genDepth(false, r, depth + 1, gs), TestUtil.makeList(r, 1, 5, () -> genClause(r, gs, depth + 1))),
                () -> new ArrayExpression(ImmutableList.copyOf(TestUtil.makeList(r, 0, 6, () -> genDepth(r, depth + 1, gs)))),
                () -> new TupleExpression(ImmutableList.copyOf(TestUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs)))),
                () ->
                {
                    List<Expression> expressions = TestUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs));
                    return new InvalidOperatorExpression(expressions, TestUtil.makeList(expressions.size() - 1, new GenRandomOp(), r, gs));
                }
            )).get();
        }
    }

    private Expression genTerminal(SourceOfRandomness r)
    {
        try
        {
            return r.choose(Arrays.asList(
                new NumericLiteral(Utility.parseNumber(r.nextBigInteger(160).toString()), null), // TODO gen unit
                new BooleanLiteral(r.nextBoolean()),
                new StringLiteral(TestUtil.generateColumnId(r).getOutput()),
                new ColumnReference(TestUtil.generateColumnId(r), ColumnReferenceType.CORRESPONDING_ROW),
                new VarUseExpression(TestUtil.generateVarName(r)),
                new UnfinishedExpression(TestUtil.makeString(r, null))
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
                String typeName = TestUtil.makeNonEmptyString(r, gs);
                String constructorName = TestUtil.makeNonEmptyString(r, gs);
                return new TagExpression(new Pair<>(typeName, constructorName), r.nextInt(0, 3 - depth) == 0 ? null : genPatternMatch(e, r, gs, depth + 1));
            }
        )).get();
    }

    @SuppressWarnings("nullness") // Some problem with Collectors.toList
    @Override
    public List<Expression> doShrink(SourceOfRandomness random, Expression larger)
    {
        return larger._test_childMutationPoints().map(Pair::getFirst).collect(Collectors.toList());
    }

    private class GenRandomOp extends Generator<String>
    {
        public GenRandomOp()
        {
            super(String.class);
        }

        @Override
        public String generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            return sourceOfRandomness.choose(Arrays.asList(
                "<", ">", "+", "-", "*", "/", "<=", ">=", "=", "<>", "&", "|", "^", ","
            ));
        }
    }
}
