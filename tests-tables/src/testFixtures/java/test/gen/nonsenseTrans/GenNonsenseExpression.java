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

package test.gen.nonsenseTrans;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import test.functions.TFunctionUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.*;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.gen.GenUnit;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Generates arbitrary expression which probably won't type check.
 * Useful for testing loading and saving of expressions.
 */
@SuppressWarnings("recorded")
@OnThread(Tag.Simulation)
public class GenNonsenseExpression extends Generator<Expression>
{
    private final GenUnit genUnit = new GenUnit();
    private TableManager tableManager = DummyManager.make();
    // An identifier that is a column must be a column throughout, so we need to keep track:
    // Maps to true if column reference, false if not.
    private final HashMap<String, Boolean> columnReferences = new HashMap<>();
    
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
        columnReferences.clear();
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
                () -> new EqualExpression(TBasicUtil.<Expression>makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs)), false),
                () -> {
                    List<Expression> expressions = TBasicUtil.<Expression>makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs));
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
                    return new ComparisonExpression(expressions, ImmutableList.<ComparisonOperator>copyOf(TBasicUtil.<ComparisonOperator>makeList(expressions.size() - 1, comparisonOperatorGenerator, r, gs)));
                },
                () -> new AndExpression(TBasicUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> new OrExpression(TBasicUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> new TimesExpression(TBasicUtil.makeList(r, 2, 5, () -> genDepth(r, depth + 1, gs))),
                () -> {
                    if (!tagAllowed)
                        return genTerminal(r, gs, false);
                    else
                    {
                        try
                        {
                            Pair<DataType, TagInfo> tag = genTag(r);
                            return TFunctionUtil.tagged(tableManager.getUnitManager(), tag.getSecond(), genDepth(r, depth + 1, gs), tag.getFirst(), true);
                        }
                        catch (InternalException | UserException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                },
                () ->
                {
                    List<Expression> expressions = TBasicUtil.makeList(r, 2, 6, () -> genDepth(r, depth + 1, gs));
                    @SuppressWarnings("unchecked")
                    Generator<AddSubtractOp> opGenerator = (Generator<AddSubtractOp>) (Generator<?>) new EnumGenerator(AddSubtractOp.class);
                    return new AddSubtractExpression(expressions, TBasicUtil.makeList(expressions.size() - 1, opGenerator, r, gs));
                },
                () -> new DivideExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new RaiseExpression(genDepth(r, depth + 1, gs), genDepth(r, depth + 1, gs)),
                () -> new CallExpression(genTerminal(r, gs, true), TBasicUtil.makeList(r, 1, 5, () -> genDepth(true, r, depth + 1, gs))),
                () -> new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), genDepth(false, r, depth + 1, gs), TBasicUtil.makeList(r, 1, 5, () -> genClause(r, gs, depth + 1)), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO)),
                () -> new ArrayExpression(ImmutableList.<Expression>copyOf(TBasicUtil.makeList(r, 0, 6, () -> genDepth(r, depth + 1, gs)))),
                () -> new RecordExpression(TBasicUtil.<Pair<@ExpressionIdentifier String, @Recorded Expression>>makeList(r, 2, 6, () -> new Pair<>("x", genDepth(r, depth + 1, gs))))
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
            ArrayList<ExSupplier<Expression>> items = new ArrayList<ExSupplier<Expression>>(Arrays.<ExSupplier<Expression>>asList(
                () -> {
                    TagInfo tagInfo = genTag(r).getSecond();
                    return IdentExpression.tag(tagInfo.getTypeName().getRaw(), tagInfo.getTagInfo().getName());
                },
                () -> IdentExpression.function(r.choose(FunctionList.getAllFunctions(tableManager.getUnitManager())).getFullName()),
                () -> {
                    while (true)
                    {
                        @ExpressionIdentifier String ident = TBasicUtil.generateVarName(r);
                        if (columnReferences.getOrDefault(ident, false))
                        {
                            if (!onlyCallTargets)
                                return IdentExpression.column(new ColumnId(ident));
                        }
                        else
                        {
                            columnReferences.put(ident, false);
                            return IdentExpression.column(new ColumnId(ident));
                        }
                    }
                }
            ));
            // Although unfinished is a valid call target, it doesn't survive a
            // round trip, so we put it below:
            
            if (!onlyCallTargets)
            {
                items.addAll(Arrays.<ExSupplier<Expression>>asList(
                    () -> InvalidIdentExpression.identOrUnfinished(TFunctionUtil.makeUnfinished(r)),
                    () -> new NumericLiteral(Utility.parseNumber(r.nextBigInteger(160).toString()), r.nextBoolean() ? null : genUnit(r, gs)),
                    () -> new BooleanLiteral(r.nextBoolean()),
                    () -> TFunctionUtil.makeStringLiteral(TBasicUtil.makeStringV(r, gs), r),
                    () -> {
                        ColumnId columnId = TBasicUtil.generateColumnId(r);
                        if (columnReferences.getOrDefault(columnId.getRaw(), true))
                        {
                            columnReferences.put(columnId.getRaw(), true);
                            return IdentExpression.column(columnId);
                        }
                        else
                        {
                            return IdentExpression.column(columnId);
                        }
                    },
                    () -> {
                        return IdentExpression.table(TBasicUtil.generateTableId(r).getRaw());
                    }
                ));
            }
            
            return r.<ExSupplier<Expression>>choose(items).get();
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
            if (vs.getTaggedTypeName().getRaw().equals("Type") || vs.getTaggedTypeName().getRaw().equals("Unit"))
                continue;
            
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

    private MatchExpression.MatchClause genClause(SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return MatchClause.unrecorded(TBasicUtil.makeList(r, 1, 4, () -> genPattern(r, gs, depth)), genDepth(r, depth, gs));
    }

    private MatchExpression.Pattern genPattern(SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return new MatchExpression.Pattern(genPatternMatch(r, gs, depth), r.nextBoolean() ? null : genDepth(r, depth, gs));
    }

    private Expression genPatternMatch(SourceOfRandomness r, GenerationStatus gs, int depth)
    {
        return r.choose(Arrays.<Supplier<Expression>>asList(
            () -> genDepth(false, r, depth, gs),
            () -> {
                // Variable name for pattern match:
                while (true)
                {
                    @ExpressionIdentifier String ident = TBasicUtil.generateVarName(r);
                    if (!columnReferences.getOrDefault(ident, false))
                    {
                        columnReferences.put(ident, false);
                        return IdentExpression.column(new ColumnId(ident));
                    }
                }
            },
            () ->
            {
                try
                {
                    Pair<DataType, TagInfo> tag = genTag(r);
                    return TFunctionUtil.tagged(tableManager.getUnitManager(), tag.getSecond(), r.nextInt(0, 3 - depth) == 0 ? null : genPatternMatch(r, gs, depth + 1), tag.getFirst(), true);
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
    @OnThread(Tag.Any)
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

    private static class EnumGenerator extends Generator<Enum> {
        private final Class<?> enumType;

        private EnumGenerator(Class<?> enumType) {
            super(Enum.class);

            this.enumType = enumType;
        }

        @Override public Enum<?> generate(
                SourceOfRandomness random,
                GenerationStatus status) {

            Object[] values = enumType.getEnumConstants();
            int index = random.nextInt(0, values.length - 1);
            return (Enum<?>) values[index];
        }

        @Override public boolean canShrink(Object larger) {
            return enumType.isInstance(larger);
        }
    }
}
