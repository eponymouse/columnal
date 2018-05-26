package test.gen.backwards;

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
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.ExSupplier;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BackwardsMatch extends BackwardsProvider
{
    private static class VarInfo
    {
        private final String name;
        private final DataType type;
        private final Object value;

        public VarInfo(String name, DataType type, Object value)
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
                            new NumericLiteral(Utility.addSubtractNumbers((Number)targetValue, (Number)numVarFinal.value, false), parent.makeUnitExpression(numberInfo.getUnit()))
                    ), ImmutableList.of(Op.SUBTRACT));
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
                    String name = boolVar.name;
                    return ImmutableList.of(() -> new IdentExpression(name));
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
        });
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of(
            () -> makeMatch(maxLevels, targetType, targetValue)//,
            //() -> makeMatchIf(maxLevels, targetType, targetValue)
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
                    return me.new MatchClause(Utility.mapListEx(patterns, p -> p.toPattern()), outcome);
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }).stream().<Function<MatchExpression, MatchClause>>flatMap(o -> o.isPresent() ? Stream.<Function<MatchExpression, MatchClause>>of(o.get()) : Stream.<Function<MatchExpression, MatchClause>>empty()).collect(Collectors.<Function<MatchExpression, MatchClause>>toList()));
        List<PatternInfo> patterns = new ArrayList<>(makeNonMatchingPatterns(maxLevels - 1, t, actual));
        
        // Add var context for successful pattern:
        varContexts.add(new ArrayList<>());
        PatternInfo match = makePatternMatch(maxLevels - 1, t, actual);
        Expression correctOutcome = parent.make(targetType, targetValue, maxLevels - 1);
        @Nullable Expression guard = r.nextBoolean() ? null : parent.make(DataType.BOOLEAN, true, maxLevels - 1);
        @Nullable Expression extraGuard = match.guard;
        if (extraGuard != null)
            guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(guard, extraGuard)));
        PatternInfo successful = new PatternInfo(match.pattern, guard);
        patterns.add(r.nextInt(0, patterns.size()), successful);
        // Remove for successful pattern:
        varContexts.remove(varContexts.size() -1);
        clauses.add(r.nextInt(0, clauses.size()), me -> {
            try
            {
                return me.new MatchClause(Utility.mapListEx(patterns, p -> p.toPattern()), correctOutcome);
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
    }

    // Pattern and an optional guard
    @NonNull
    private PatternInfo makePatternMatch(int maxLevels, DataType t, Object actual)
    {
        try
        {
            if (t.isTagged() && r.nextBoolean())
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
                        @Nullable TaggedTypeDefinition typeDefinition = DummyManager.INSTANCE.getTypeManager().lookupDefinition(typeName);
                        if (typeDefinition == null)
                            throw new InternalException("Looked up type but null definition: " + typeName);
                        if (inner == null)
                            return new PatternInfo(TestUtil.tagged(Either.right(new TagInfo(typeDefinition, p.getTagIndex())), null), null);
                        @Nullable Object innerValue = p.getInner();
                        if (innerValue == null)
                            throw new InternalException("Type says inner value but is null");
                        PatternInfo subPattern = makePatternMatch(maxLevels, inner, innerValue);
                        return new PatternInfo(TestUtil.tagged(Either.right(new TagInfo(typeDefinition, p.getTagIndex())), subPattern.pattern), subPattern.guard);
                    }
                });

            }
            else if (r.nextBoolean()) // Do equals but using variable + guard
            {
                String varName = "var" + nextVar++;
                if (!varContexts.isEmpty())
                    varContexts.get(varContexts.size() - 1).add(new VarInfo(varName, t, actual));
                return new PatternInfo(new VarDeclExpression(varName), new EqualExpression(ImmutableList.of(new IdentExpression(varName), parent.make(t, actual, maxLevels))));
            }
            Expression expression = parent.make(t, actual, maxLevels);
            return new PatternInfo(expression, null);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Simulation)
    private List<PatternInfo> makeNonMatchingPatterns(final int maxLevels, final DataType t, @Value Object actual) throws InternalException, UserException
    {
        return Utility.filterOutNulls(TestUtil.<@Nullable PatternInfo>makeList(r, 1, 3, () -> makeNonMatchingPattern(maxLevels, t, actual)).stream()).collect(ImmutableList.toImmutableList());
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
        Object nonMatchingValueFinal = nonMatchingValue;
        PatternInfo match = makePatternMatch(maxLevels - 1, t, nonMatchingValueFinal);
    
        @Nullable Expression guard = r.nextBoolean() ? null : parent.make(DataType.BOOLEAN, true, maxLevels - 1);
        @Nullable Expression extraGuard = match.guard;
        if (extraGuard != null)
            guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(guard, extraGuard)));
        @Nullable Expression guardFinal = guard;
        return new PatternInfo(match.pattern, guardFinal);
    }

}
