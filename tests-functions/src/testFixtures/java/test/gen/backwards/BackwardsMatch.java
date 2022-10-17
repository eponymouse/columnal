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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import test.functions.TFunctionUtil;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.SpecificDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.*;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.DefineExpression.Definition;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.expression.MatchExpression.Pattern;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
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
                @Nullable VarInfo numVar = findVarOfType(DataTypeUtility::isNumber);
                if (numVar == null)
                    return ImmutableList.of();
                @NonNull VarInfo numVarFinal = numVar;
                IdentExpression varRef = IdentExpression.load(numVar.name);
                return ImmutableList.of(() -> {
                    return new AddSubtractExpression(ImmutableList.of(
                        new TimesExpression(ImmutableList.of(varRef, new NumericLiteral(1, parent.makeUnitExpression(numberInfo.getUnit().divideBy(TFunctionUtil.getUnit(numVarFinal.type)))))),
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
                        return ImmutableList.of(() -> IdentExpression.load(name));
                    else
                    {
                        return ImmutableList.of(() -> new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), "not", IdentExpression.load(name)),
                            () -> new EqualExpression(ImmutableList.of(new BooleanLiteral(false), IdentExpression.load(name)), false)
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
            public List<ExpressionMaker> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
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
            () -> makeMatchIf(maxLevels, targetType, targetValue),
            () -> makeMatchDefine(maxLevels, targetType, targetValue)
        );
    }

    /**
     * Make a match expression with an if-then-else.
     *
     * @return An IfThenElseExpression that evaluates to the correct outcome.
     * @throws InternalException
     * @throws UserException
     */
    @OnThread(Tag.Simulation)
    private IfThenElseExpression makeMatchIf(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        DataType t = parent.makeType();
        @Value Object actual = parent.makeValue(t);
        // Make a bunch of guards which won't fire:
        List<Pair<PatternInfo, Expression>> clauses = new ArrayList<>(Utility.filterOptional(TBasicUtil.<Optional<Pair<PatternInfo, Expression>>>makeList(r, 0, 4, () -> {
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
        IfThenElseExpression cur = IfThenElseExpression.unrecorded(clauses.get(0).getFirst().toEquals(toMatch), clauses.get(0).getSecond(), parent.make(targetType, parent.makeValue(targetType), maxLevels - 1));

        for (int i = 1; i < clauses.size(); i++)
        {
            cur = IfThenElseExpression.unrecorded(clauses.get(i).getFirst().toEquals(toMatch), clauses.get(i).getSecond(), cur);
        }
        
        return cur;
    }

    /**
     * Make a define expression.
     *
     * @return A DefineExpression that evaluates to the correct outcome.
     * @throws InternalException
     * @throws UserException
     */
    @OnThread(Tag.Simulation)
    private Expression makeMatchDefine(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        DataType t = parent.makeType();
        @Value Object actual = parent.makeValue(t);
        
        // Add var context for successful pattern:
        varContexts.add(new ArrayList<>());
        PatternInfo match = makePatternMatch(maxLevels - 1, t, actual, true);
        Expression correctOutcome = parent.make(targetType, targetValue, maxLevels - 1);
        Expression guard = parent.make(DataType.BOOLEAN, true, maxLevels - 1);
        @Nullable Expression extraGuard = match.guard;
        if (extraGuard != null)
            guard = new AndExpression(Arrays.asList(extraGuard, guard));
        PatternInfo successful = new PatternInfo(match.pattern, guard);
        
        // Remove for successful pattern:
        ArrayList<VarInfo> declVars = varContexts.remove(varContexts.size() - 1);

        Expression toMatch = parent.make(t, actual, maxLevels - 1);
        
        ImmutableList.Builder<Either<HasTypeExpression, Definition>> defines = ImmutableList.builder();
        if (!declVars.isEmpty())
        {
            VarInfo v = declVars.get(r.nextInt(declVars.size()));
            defines.add(Either.left(new HasTypeExpression(v.name, new TypeLiteralExpression(TypeExpression.fromDataType(v.type)))));
            defines.add(Either.right(new Definition(match.pattern, toMatch)));
            return DefineExpression.unrecorded(defines.build(), IfThenElseExpression.unrecorded(guard, correctOutcome, parent.make(targetType, parent.makeValue(targetType), maxLevels - 1)));
        }
        else
        {
            // If no vars, it's fine to just return the outcome: 
            return correctOutcome;
        }        
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
        ArrayList<MatchClause> clauses = new ArrayList<>(TBasicUtil.makeList(r, 0, 4, (ExSupplier<Optional<MatchClause>>)() -> {
            // Generate a bunch which can't match the item:
            List<PatternInfo> patterns = makeNonMatchingPatterns(maxLevels - 1, t, actual);
            Expression outcome = parent.make(targetType, parent.makeValue(targetType), maxLevels - 1);
            if (patterns.isEmpty())
                return Optional.<MatchClause>empty();
            return Optional.<MatchClause>of(MatchClause.unrecorded(Utility.mapListExI(patterns, p -> p.toPattern()), outcome));
        }).stream().<MatchClause>flatMap(o -> o.isPresent() ? Stream.<MatchClause>of(o.get()) : Stream.<MatchClause>empty()).collect(Collectors.<MatchClause>toList()));
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
        clauses.add(r.nextInt(0, clauses.size()), MatchClause.unrecorded(Utility.mapListExI(patterns, p -> p.toPattern()), toMatch));
        return new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), parent.make(t, actual, maxLevels - 1), ImmutableList.copyOf(clauses), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO));
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
            EqualExpression equalExpression = new EqualExpression(ImmutableList.of(matchAgainst, pattern), true);
            return guard == null ? equalExpression : new AndExpression(
                ImmutableList.of(equalExpression, guard)
            );
        }
    }

    // Pattern and an optional guard
    private PatternInfo makePatternMatch(int maxLevels, DataType t, @Value Object actual, boolean canMatchMore)
    {
        try
        {
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
                        @Value TaggedValue p = (TaggedValue) actual;
                        return t.apply(new SpecificDataTypeVisitor<PatternInfo>()
                        {
                            @Override
                            @OnThread(value = Tag.Simulation, ignoreParent = true)
                            public PatternInfo tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes) throws InternalException
                            {
                                TagType<DataType> tagType = tagTypes.get(p.getTagIndex());
                                @Nullable DataType inner = tagType.getInner();
                                @Nullable TaggedTypeDefinition typeDefinition = parent.getTypeManager().lookupDefinition(typeName);
                                if (typeDefinition == null)
                                    throw new InternalException("Looked up type but null definition: " + typeName);
                                if (inner == null)
                                    return new PatternInfo(TFunctionUtil.tagged(parent.getTypeManager().getUnitManager(), new TagInfo(typeDefinition, p.getTagIndex()), null, t, false), null);
                                @Nullable @Value Object innerValue = p.getInner();
                                if (innerValue == null)
                                    throw new InternalException("Type says inner value but is null");
                                PatternInfo subPattern = makePatternMatch(maxLevels, inner, innerValue, canMatchMore);
                                return new PatternInfo(TFunctionUtil.tagged(parent.getTypeManager().getUnitManager(), new TagInfo(typeDefinition, p.getTagIndex()), subPattern.pattern, t, false), subPattern.guard);
                            }
                        });
                    }
                    else
                        return general();
                }

                @Override
                public PatternInfo record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                {
                    if (r.nextBoolean())
                    {
                        ArrayList<Pair<@ExpressionIdentifier String, Expression>> members = new ArrayList<>();
                        ImmutableList.Builder<Expression> guards = ImmutableList.builder();
                        @Value Record values = Utility.cast(actual, Record.class);
                        // Note -- important to shuffle here not later, because we may capture a variable then use it later@
                        ArrayList<Entry<@ExpressionIdentifier String, DataType>> entries = new ArrayList<>(fields.entrySet());
                        Collections.shuffle(entries, new Random(r.nextLong()));
                        for (Entry<@ExpressionIdentifier String, DataType> entry : entries)
                        {
                            // We don't have to match every item:
                            if (canMatchMore && members.size() >= 1 && r.nextInt(3) == 1)
                                continue;
                            
                            DataType dataType = entry.getValue();
                            PatternInfo p = makePatternMatch(maxLevels - 1, dataType, values.getField(entry.getKey()), canMatchMore);
                            members.add(new Pair<>(entry.getKey(), p.pattern));
                            if (p.guard != null)
                                guards.add(p.guard);
                        }
                        ImmutableList<Expression> g = guards.build();
                        return new PatternInfo(new RecordExpression(ImmutableList.copyOf(members)),
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
                        return new PatternInfo(IdentExpression.load(varName), new EqualExpression(ImmutableList.of(IdentExpression.load(varName), rhsVal), false));
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
        return Utility.filterOptional(TBasicUtil.<Optional<PatternInfo>>makeList(r, 1, 3, () -> Optional.ofNullable(makeNonMatchingPattern(maxLevels, t, actual))).stream()).collect(ImmutableList.toImmutableList());
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
