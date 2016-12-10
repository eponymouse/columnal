package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import edu.emory.mathcs.backport.java.util.Collections;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.InternalException;
import records.transformations.expression.BinaryOpExpression;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StringLiteral;
import test.TestUtil;
import utility.Utility;

import java.util.ArrayList;
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
                () -> new MatchExpression(genDepth(r, depth + 1, gs), TestUtil.makeList(r, 1, 5, () -> genClause(r, gs, depth + 1)))
            )).get();
        }
    }

    private Expression genTerminal(SourceOfRandomness r)
    {
        return r.choose(Arrays.asList(
            new NumericLiteral(r.nextBigInteger(160)),
            new BooleanLiteral(r.nextBoolean()),
            new StringLiteral(TestUtil.generateColumnId(r).getOutput()),
            new ColumnReference(TestUtil.generateColumnId(r))
        ));
    }

    private Function<MatchExpression, MatchExpression.MatchClause> genClause(SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return e -> e.new MatchClause(TestUtil.makeList(r, 1, 4, () -> genPattern(e, r, gs, depth)), genDepth(r, depth, gs));
    }

    private MatchExpression.Pattern genPattern(MatchExpression e, SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return new MatchExpression.Pattern(genPatternMatch(e, r, gs, depth), TestUtil.makeList(r, 0, 3, () -> genDepth(r, depth, gs)));
    }

    private MatchExpression.PatternMatch genPatternMatch(MatchExpression e, SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return r.choose(Arrays.<Supplier<MatchExpression.PatternMatch>>asList(
            () -> new MatchExpression.PatternMatchExpression(genDepth(r, depth, gs)),
            () -> {
                try
                {
                    return e.new PatternMatchVariable(TestUtil.makeUnquotedIdent(r, gs));
                }
                catch (InternalException ex)
                {
                    throw new RuntimeException(ex);
                }
            },
            () -> e.new PatternMatchConstructor(TestUtil.makeString(r, gs), r.nextInt(0, 3 - depth) == 0 ? null : genPatternMatch(e, r, gs, depth + 1))
        )).get();
    }

    @Override
    public List<Expression> doShrink(SourceOfRandomness random, Expression larger)
    {
        ArrayList<Expression> alt = new ArrayList<>();
        alt.add(genTerminal(random));
        if (larger instanceof BinaryOpExpression)
        {
            alt.add(((BinaryOpExpression)larger).copy(null, genTerminal(random)));
            alt.add(((BinaryOpExpression)larger).copy(genTerminal(random), null));
        }
        if (larger instanceof MatchExpression)
        {
            MatchExpression e = (MatchExpression)larger;
            alt.add(e.getExpression());
            for (Expression shrunk : doShrink(random, e.getExpression()))
                alt.add(new MatchExpression(shrunk, Utility.<MatchExpression.MatchClause, Function<MatchExpression, MatchExpression.MatchClause>>mapList(e.getClauses(), c -> c::copy)));
            alt.add(new MatchExpression(e.getExpression(), e.getClauses().stream().<Function<MatchExpression, MatchExpression.MatchClause>>map((MatchExpression.MatchClause c) -> {
                alt.add(c.getOutcome());
                return (MatchExpression ne) -> ne.new MatchClause(Utility.<MatchExpression.Pattern, MatchExpression.Pattern>mapList(c.getPatterns(), p -> shrinkPattern(random, p, ne)), random.choose(doShrink(random, c.getOutcome())));
            }).collect(Collectors.<Function<MatchExpression, MatchExpression.MatchClause>>toList())));
        }
        //TODO n-ary
        return alt;
    }

    private MatchExpression.Pattern shrinkPattern(SourceOfRandomness random, MatchExpression.Pattern p, MatchExpression ne)
    {
        return new MatchExpression.Pattern(p.getPattern(), java.util.Collections.emptyList());
    }
}
