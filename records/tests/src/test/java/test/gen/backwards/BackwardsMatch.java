package test.gen.backwards;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExSupplier;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("recorded")
public class BackwardsMatch extends BackwardsProvider
{
    private static class VarInfo
    {
        private final @ExpressionIdentifier String name;
        private final DataType type;
        private final @Value Object value;

        public VarInfo(@ExpressionIdentifier String name, DataType type, @Value Object value)
        {
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }
    
    private int nextVar = 0;
    private ArrayList<ArrayList<VarInfo>> varContexts = new ArrayList<>();

    public BackwardsMatch(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }
    
    private @Nullable VarInfo findVarOfType(Predicate<DataType> typePred)
    {
        ArrayList<VarInfo> possibles = new ArrayList<>();
        for (ArrayList<VarInfo> varContext : varContexts)
        {
            for (VarInfo varInfo : varContext)
            {
                if (typePred.test(varInfo.type))
                {
                    possibles.add(varInfo); 
                }
            }
        }
        return possibles.isEmpty() ? null : r.choose(possibles);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        // For temporal, numerical, boolean and text, we manipulate them to be what we want.
        // For boolean, we may use an equals expression.
        
        return targetType.apply(new DataTypeVisitor<List<ExpressionMaker>>()
        {
            @Override
            public List<ExpressionMaker> number(NumberInfo numberInfo) throws InternalException, UserException
            {
                @Nullable VarInfo numVar = findVarOfType(DataType::isNumber);
                if (numVar == null)
                    return ImmutableList.of();
                @NonNull VarInfo numVarFinal = numVar;
                IdentExpression varRef = new IdentExpression(numVar.name);
                return ImmutableList.of(() -> {
                    return new AddSubtractExpression(ImmutableList.of(
                        new TimesExpression(ImmutableList.of(varRef, new NumericLiteral(1, parent.makeUnitExpression(numberInfo.getUnit().divideBy(numVarFinal.type.getNumberInfo().getUnit()))))),
                            new NumericLiteral(Utility.addSubtractNumbers((Number)targetValue, Utility.cast(numVarFinal.value, Number.class), false), parent.makeUnitExpression(numberInfo.getUnit()))
                    ), ImmutableList.of(AddSubtractOp.ADD));
                });
            }

            @Override
            public List<ExpressionMaker> text() throws InternalException, UserException
            {
                // TODO include text replace
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                // TODO include date manipulation
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> bool() throws InternalException, UserException
            {
                @Nullable VarInfo boolVar = findVarOfType(t -> t.equals(DataType.BOOLEAN));
                if (boolVar == null)
                    return ImmutableList.of();
                else
                {
                    @ExpressionIdentifier String name = boolVar.name;
                    // need to negate the value if it doesn't match:
                    if (boolVar.value.equals(targetValue))
                        return ImmutableList.of(() -> new IdentExpression(name));
                    else
                    {
                        return ImmutableList.of(() -> new CallExpression(parent.getTypeManager().getUnitManager(), "not", new IdentExpression(name)),
                            () -> new EqualExpression(ImmutableList.of(new BooleanLiteral(false), new IdentExpression(name)))
                        );
                    }
                }
            }

            @Override
            public List<ExpressionMaker> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> array(DataType inner) throws InternalException, UserException
            {
                return ImmutableList.of();
            }
        }).stream().map(m -> m.withBias(10)).collect(ImmutableList.toImmutableList());
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of(
            () -> makeMatch(maxLevels, targetType, targetValue),
            () -> makeMatchIf(maxLevels, targetType, targetValue)
        );
    }

    /**
     * Make a match expression with a match.
     *
     * @return A MatchExpression that evaluates to the correct outcome.
     * @throws InternalException
     * @throws UserException
     */
    @OnThread(Tag.Simulation)
    private IfThenElseExpression makeMatchIf(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        DataType t = parent.makeType();
        @Value Object actual = parent.makeValue(t);
        // Make a bunch of guards which won't fire:
        List<Pair<PatternInfo, Expression>> clauses = new ArrayList<>(Utility.filterOptional(TestUtil.<Optional<Pair<PatternInfo, Expression>>>makeList(r, 0, 4, () -> {
            @Nullable PatternInfo nonMatch = makeNonMatchingPattern(maxLevels - 1, t, actual);
            return nonMatch == null ? Optional.empty() : Optional.of(new Pair<>(nonMatch,
                    parent.make(targetType, parent.makeValue(targetType), maxLevels - 1)));
        }).stream()).collect(Collectors.toList()));

        // Add var context for successful pattern:
        varContexts.add(new ArrayList<>());
        PatternInfo match = makePatternMatch(maxLevels - 1, t, actual, true);
        Expression correctOutcome = parent.make(targetType, targetValue, maxLevels - 1);
        @Nullable Expression guard = r.nextBoolean() ? null : parent.make(DataType.BOOLEAN, true, maxLevels - 1);
        @Nullable Expression extraGuard = match.guard;
        if (extraGuard != null)
            guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(extraGuard, guard)));
        PatternInfo successful = new PatternInfo(match.pattern, guard);
        clauses.add(r.nextInt(0, clauses.size()), new Pair<>(successful, correctOutcome));
        // Remove for successful pattern:
        varContexts.remove(varContexts.size() -1);

        Expression toMatch = parent.make(t, actual, maxLevels - 1);
        IfThenElseExpression cur = new IfThenElseExpression(clauses.get(0).getFirst().toEquals(toMatch), clauses.get(0).getSecond(), parent.make(targetType, parent.makeValue(targetType), maxLevels - 1));

        for (int i = 1; i < clauses.size(); i++)
        {
            cur = new IfThenElseExpression(clauses.get(i).getFirst().toEquals(toMatch), clauses.get(i).getSecond(), cur);
        }
        
        return cur;
    }

    /**
     * Make a match expression with a match.
     * 
     * @return A MatchExpression that evaluates to the correct outcome.
     * @throws InternalException
     * @throws UserException
     */
    @OnThread(Tag.Simulation)
    private MatchExpression makeMatch(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        DataType t = parent.makeType();
        @Value Object actual = parent.makeValue(t);
        // Make a bunch of guards which won't fire:
        List<Function<MatchExpression, MatchClause>> clauses = new ArrayList<>(TestUtil.makeList(r, 0, 4, (ExSupplier<Optional<Function<MatchExpression, MatchClause>>>)() -> {
            // Generate a bunch which can't match the item:
            List<PatternInfo> patterns = makeNonMatchingPatterns(maxLevels - 1, t, actual);
            Expression outcome = parent.make(targetType, parent.makeValue(targetType), maxLevels - 1);
            if (patterns.isEmpty())
                return Optional.<Function<MatchExpression, MatchClause>>empty();
            return Optional.<Function<MatchExpression, MatchClause>>of((MatchExpression me) -> {
                try
                {
                    return me.new MatchClause(Utility.mapListExI(patterns, p -> p.toPattern()), outcome);
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }).stream().<Function<MatchExpression, MatchClause>>flatMap(o -> o.isPresent() ? Stream.<Function<MatchExpression, MatchClause>>of(o.get()) : Stream.<Function<MatchExpression, MatchClause>>empty()).collect(Collectors.<Function<MatchExpression, MatchClause>>toList()));
        List<PatternInfo> patterns = new ArrayList<>(makeNonMatchingPatterns(maxLevels - 1, t, actual));

        Expression toMatch = parent.make(targetType, targetValue, maxLevels - 1);
        
        // Add var context for successful pattern:
        varContexts.add(new ArrayList<>());
        PatternInfo match = makePatternMatch(maxLevels - 1, t, actual, true);
        
        @Nullable Expression guard = r.nextBoolean() ? null : parent.make(DataType.BOOLEAN, true, maxLevels - 1);
        @Nullable Expression extraGuard = match.guard;
        if (extraGuard != null)
            guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(extraGuard, guard)));
        PatternInfo successful = new PatternInfo(match.pattern, guard);
        patterns.add(r.nextInt(0, patterns.size()), successful);
        // Remove for successful pattern:
        varContexts.remove(varContexts.size() -1);
        clauses.add(r.nextInt(0, clauses.size()), me -> {
            try
            {
                return me.new MatchClause(Utility.mapListExI(patterns, p -> p.toPattern()), toMatch);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        });
        return new MatchExpression(parent.make(t, actual, maxLevels - 1), clauses);
    }
    
    class PatternInfo
    {
        private final Expression pattern;
        private final @Nullable Expression guard;

        public PatternInfo(Expression pattern, @Nullable Expression guard)
        {
            this.pattern = pattern;
            this.guard = guard;
        }

        public Pattern toPattern()
        {
            return new Pattern(pattern, guard);
        }

        public Expression toEquals(Expression matchAgainst)
        {
            EqualExpression equalExpression = new EqualExpression(
                    r.nextBoolean() ? ImmutableList.of(matchAgainst, pattern) : ImmutableList.of(pattern, matchAgainst)
            );
            return guard == null ? equalExpression : new AndExpression(
                ImmutableList.of(equalExpression, guard)
            );
        }
    }

    // Pattern and an optional guard
    @NonNull
    private PatternInfo makePatternMatch(int maxLevels, DataType t, @Value Object actual, boolean canMatchMore)
    {
        try
        {
            //TODO
            return t.apply(new DataTypeVisitor<PatternInfo>()
            {
                @Override
                public PatternInfo number(NumberInfo numberInfo) throws InternalException, UserException
                {
                    return general();
                }

                @Override
                public PatternInfo text() throws InternalException, UserException
                {
                    return general();
                }

                @Override
                public PatternInfo date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return general();
                }

                @Override
                public PatternInfo bool() throws InternalException, UserException
                {
                    return general();
                }

                @Override
                public PatternInfo tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    if (r.nextBoolean())
                    {
                        TaggedValue p = (TaggedValue) actual;
                        return t.apply(new SpecificDataTypeVisitor<PatternInfo>()
                        {
                            @Override
                            @OnThread(value = Tag.Simulation, ignoreParent = true)
                            public PatternInfo tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes) throws InternalException, UserException
                            {
                                TagType<DataType> tagType = tagTypes.get(p.getTagIndex());
                                @Nullable DataType inner = tagType.getInner();
                                @Nullable TaggedTypeDefinition typeDefinition = parent.getTypeManager().lookupDefinition(typeName);
                                if (typeDefinition == null)
                                    throw new InternalException("Looked up type but null definition: " + typeName);
                                if (inner == null)
                                    return new PatternInfo(TestUtil.tagged(parent.getTypeManager().getUnitManager(), new TagInfo(typeDefinition, p.getTagIndex()), null, t, false), null);
                                @Nullable @Value Object innerValue = p.getInner();
                                if (innerValue == null)
                                    throw new InternalException("Type says inner value but is null");
                                PatternInfo subPattern = makePatternMatch(maxLevels, inner, innerValue, canMatchMore);
                                return new PatternInfo(TestUtil.tagged(parent.getTypeManager().getUnitManager(), new TagInfo(typeDefinition, p.getTagIndex()), subPattern.pattern, t, false), subPattern.guard);
                            }
                        });
                    }
                    else
                        return general();
                }

                @Override
                public PatternInfo tuple(ImmutableList<DataType> inner) throws InternalException, UserException
                {
                    if (r.nextBoolean())
                    {
                        ImmutableList.Builder<Expression> members = ImmutableList.builderWithExpectedSize(inner.size());
                        ImmutableList.Builder<Expression> guards = ImmutableList.builder();
                        @Value Object[] values = Utility.castTuple(actual, inner.size());
                        for (int i = 0; i < inner.size(); i++)
                        {
                            DataType dataType = inner.get(i);
                            PatternInfo p = makePatternMatch(maxLevels - 1, dataType, values[i], canMatchMore);
                            members.add(p.pattern);
                            if (p.guard != null)
                                guards.add(p.guard);
                        }
                        ImmutableList<Expression> g = guards.build();
                        return new PatternInfo(new TupleExpression(members.build()),
                            g.size() == 0 ? null : (g.size() == 1 ? g.get(0) : new AndExpression(g))    
                        );
                    }
                    else
                        return general();
                }

                @Override
                public PatternInfo array(DataType inner) throws InternalException, UserException
                {
                    if (r.nextBoolean())
                    {
                        ListEx values = Utility.cast(actual, ListEx.class);
                        ImmutableList.Builder<Expression> members = ImmutableList.builderWithExpectedSize(values.size());
                        ImmutableList.Builder<Expression> guards = ImmutableList.builder();
                        
                        for (int i = 0; i < values.size(); i++)
                        {
                            PatternInfo p = makePatternMatch(maxLevels - 1, inner, values.get(i), canMatchMore);
                            members.add(p.pattern);
                            if (p.guard != null)
                                guards.add(p.guard);
                        }
                        ImmutableList<Expression> g = guards.build();
                        return new PatternInfo(new ArrayExpression(members.build()),
                                g.size() == 0 ? null : (g.size() == 1 ? g.get(0) : new AndExpression(g))
                        );
                    }
                    else
                        return general();

                }
                
                private PatternInfo general() throws InternalException, UserException
                {
                    Expression expression = parent.make(t, actual, maxLevels);

                    if (r.nextBoolean()) // Do equals but using variable + guard
                    {
                        Expression rhsVal = parent.make(t, actual, maxLevels);
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String varName = "var" + nextVar++;
                        if (!varContexts.isEmpty())
                            varContexts.get(varContexts.size() - 1).add(new VarInfo(varName, t, actual));
                        return new PatternInfo(new VarDeclExpression(varName), new EqualExpression(ImmutableList.of(new IdentExpression(varName), rhsVal)));
                    }
                    if (canMatchMore && r.nextInt(0, 5) == 1)
                        return new PatternInfo(new MatchAnythingExpression(), null);

                    return new PatternInfo(expression, null);
                }
            });
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Simulation)
    private List<PatternInfo> makeNonMatchingPatterns(final int maxLevels, final DataType t, @Value Object actual) throws InternalException, UserException
    {
        return Utility.filterOptional(TestUtil.<Optional<PatternInfo>>makeList(r, 1, 3, () -> Optional.ofNullable(makeNonMatchingPattern(maxLevels, t, actual))).stream()).collect(ImmutableList.toImmutableList());
    }

    @OnThread(Tag.Simulation)
    private @Nullable PatternInfo makeNonMatchingPattern(final int maxLevels, final DataType t, @Value Object actual) throws InternalException, UserException
    {
        @Value Object nonMatchingValue;
        int attempts = 0;
        do
        {
            nonMatchingValue = parent.makeValue(t);
            if (attempts++ >= 30)
                return null;
        }
        while (Utility.compareValues(nonMatchingValue, actual) == 0);
        @Value Object nonMatchingValueFinal = nonMatchingValue;
        // Add var context for pattern:
        varContexts.add(new ArrayList<>());
        PatternInfo match = makePatternMatch(maxLevels - 1, t, nonMatchingValueFinal, false);
    
        @Nullable Expression guard = r.nextBoolean() ? null : parent.make(DataType.BOOLEAN, true, maxLevels - 1);
        @Nullable Expression extraGuard = match.guard;
        if (extraGuard != null)
            guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(extraGuard, guard)));
        varContexts.remove(varContexts.size() - 1);
        @Nullable Expression guardFinal = guard;
        return new PatternInfo(match.pattern, guardFinal);
    }

}
