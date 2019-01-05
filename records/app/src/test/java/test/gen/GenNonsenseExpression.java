package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.internal.generator.EnumGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import utility.Either;
import utility.Pair;
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
@SuppressWarnings("recorded")
public class GenNonsenseExpression extends Generator<Expression>
{
    private final GenUnit genUnit = new GenUnit();
    private TableManager tableManager = DummyManager.make();
    
    public GenNonsenseExpression()
    {
        super(Expression.class);
    }

    public void setTableManager(TableManager tableManager)
    {
        this.tableManager = tableManager;
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
            return genTerminal(r, gs, false);
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
                () -> {
                    if (!tagAllowed)
                        return genTerminal(r, gs, false);
                    else
                    {
                        try
                        {
                            Pair<DataType, TagInfo> tag = genTag(r);
                            return TestUtil.tagged(tableManager.getUnitManager(), tag.getSecond(), genDepth(r, depth + 1, gs), tag.getFirst());
                        }
                        catch (InternalException | UserException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                },
                () ->
                {
                    List<Expression> expressions = TestUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs));
                    @SuppressWarnings("unchecked")
                    Generator<AddSubtractOp> opGenerator = (Generator<AddSubtractOp>) (Generator<?>) new EnumGenerator(AddSubtractOp.class);
                    return new AddSubtractExpression(expressions, TestUtil.makeList(expressions.size() - 1, opGenerator, r, gs));
                },
                () -> new DivideExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new RaiseExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new CallExpression(genTerminal(r, gs, true), genDepth(true, r, depth + 1, gs)),
                () -> new MatchExpression(genDepth(false, r, depth + 1, gs), TestUtil.makeList(r, 1, 5, () -> genClause(r, gs, depth + 1))),
                () -> new ArrayExpression(ImmutableList.<Expression>copyOf(TestUtil.makeList(r, 0, 6, () -> genDepth(r, depth + 1, gs)))),
                () -> new TupleExpression(ImmutableList.<Expression>copyOf(TestUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs))))
                /*,
                () ->
                {
                    List<Either<String, Expression>> contents = TestUtil.makeList(r, 3, 6, () -> genDepth(r, depth + 1, gs));
                    return new InvalidOperatorExpression(contents); //expressions, TestUtil.makeList(expressions.size() - 1, new GenRandomOp(), r, gs));
                }*/
            )).get();
        }
    }

    private Expression genTerminal(SourceOfRandomness r, GenerationStatus gs, boolean onlyCallTargets)
    {
        try
        {
            // Call targets:
            ArrayList<Expression> items = new ArrayList<>(Arrays.asList(
                new ConstructorExpression(genTag(r).getSecond()),
                new StandardFunction(r.choose(FunctionList.getAllFunctions(tableManager.getUnitManager()))),
                new IdentExpression(TestUtil.generateVarName(r))
            ));
            // Although unfinished is a valid call target, it doesn't survive a
            // round trip, so we put it below:
            
            if (!onlyCallTargets)
            {
                items.addAll(Arrays.asList(
                    InvalidIdentExpression.identOrUnfinished(TestUtil.makeUnfinished(r)),
                    new NumericLiteral(Utility.parseNumber(r.nextBigInteger(160).toString()), r.nextBoolean() ? null : genUnit(r, gs)),
                    new BooleanLiteral(r.nextBoolean()),
                    new StringLiteral(TestUtil.makeStringV(r, gs)),
                    new ColumnReference(TestUtil.generateColumnId(r), ColumnReferenceType.CORRESPONDING_ROW)  
                ));
            }
            
            return r.<Expression>choose(items);
        }
        catch (UserException | InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    private Pair<DataType, TagInfo> genTag(SourceOfRandomness r) throws InternalException, UserException
    {
        List<Pair<DataType, TagInfo>> list = new ArrayList<>();
        for (TaggedTypeDefinition vs : tableManager.getTypeManager().getKnownTaggedTypes().values())
        {
            for (TagInfo info : vs._test_getTagInfos())
            {
                Pair<DataType, TagInfo> dataTypeTagInfoPair = new Pair<>(vs.instantiate(Utility.mapListI(vs.getTypeArguments(), p -> p.getFirst() == TypeVariableKind.UNIT ? Either.<@NonNull Unit, @NonNull DataType>left(Unit.SCALAR) : Either.<@NonNull Unit, @NonNull DataType>right(DataType.TEXT)), tableManager.getTypeManager()), info);
                list.add(dataTypeTagInfoPair);
            }
        }
        return r.choose(list);
    }

    private UnitExpression genUnit(SourceOfRandomness r, GenerationStatus gs)
    {
        // TODO generate richer expressions that cancel out, etc
        return UnitExpression.load(genUnit.generate(r, gs));
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
            () -> new VarDeclExpression(TestUtil.generateVarName(r).trim()),
            () ->
            {
                try
                {
                    Pair<DataType, TagInfo> tag = genTag(r);
                    return TestUtil.tagged(tableManager.getUnitManager(), tag.getSecond(), r.nextInt(0, 3 - depth) == 0 ? null : genPatternMatch(e, r, gs, depth + 1), tag.getFirst());
                }
                catch (InternalException | UserException ex)
                {
                    throw new RuntimeException(ex);
                }
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
