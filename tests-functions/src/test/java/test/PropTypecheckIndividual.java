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

package test;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import test.functions.TFunctionUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.AndExpression;
import xyz.columnal.transformations.expression.ArrayExpression;
import xyz.columnal.transformations.expression.BracketedStatus;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.transformations.expression.ComparisonExpression;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.DivideExpression;
import xyz.columnal.transformations.expression.EqualExpression;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorder;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorderStorer;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.MatchExpression;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.expression.MatchExpression.Pattern;
import xyz.columnal.transformations.expression.NonOperatorExpression;
import xyz.columnal.transformations.expression.NotEqualExpression;
import xyz.columnal.transformations.expression.OrExpression;
import xyz.columnal.transformations.expression.RaiseExpression;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledString;
import test.gen.type.GenDataType;
import test.gen.GenUnit;
import test.gen.type.GenDataTypeMaker;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by neil on 24/01/2017.
 */
@SuppressWarnings("recorded")
@OnThread(Tag.Simulation)
@RunWith(JUnitQuickcheck.class)
public class PropTypecheckIndividual
{
    // A shell expression that exists just to resolve its type checking to a given type.
    private static class DummyExpression extends Expression
    {
        private final DataType type;

        private DummyExpression(DataType type)
        {
            this.type = type;
        }

        @Override
        public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
        {
            return onError.recordType(this, state, TypeExp.fromDataType(this, type));
        }

        @Override
        public ValueResult calculateValue(EvaluateState state) throws InternalException
        {
            throw new InternalException("Testing");
        }
        
        @Override
        public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
        {
            return "Testing";
        }

        @Override
        protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
        {
            return StyledString.s("Testing");
        }

        @Override
        public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
        {
            return Stream.empty();
        }

        @Override
        public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
        {
            return null;
        }

        @SuppressWarnings("interned")
        @Override
        public boolean equals(@Nullable Object o)
        {
            return this == o;
        }

        @Override
        public int hashCode()
        {
            return System.identityHashCode(this);
        }

        @Override
        public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return this == toReplace ? replaceWith : this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T visit(ExpressionVisitor<T> visitor)
        {
            // Hack -- we know we should only be called with Stream visitors:
            return (T)Stream.of();
        }
    }

    class DummyConstExpression extends DummyExpression
    {
        private final Rational value;

        private DummyConstExpression(DataType type, Rational value)
        {
            super(type);
            this.value = value;
        }

        @Override
        public Optional<Rational> constantFold()
        {
            return Optional.of(value);
        }
    }

    @Property
    @SuppressWarnings({"i18n", "deprecation"}) // Because of assumeThat
    public void propEquals(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        boolean same = a.equals(b);
        Assume.assumeThat(same, Matchers.<Boolean>equalTo(false));

        ColumnLookup columnLookup = TFunctionUtil.dummyColumnLookup();
        assertEquals(null, new EqualExpression(ImmutableList.of(new DummyExpression(a), new DummyExpression(b)), false).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new EqualExpression(ImmutableList.of(new DummyExpression(a), new DummyExpression(a)), false).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new EqualExpression(ImmutableList.of(new DummyExpression(b), new DummyExpression(b)), false).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(null, new NotEqualExpression(new DummyExpression(a), new DummyExpression(b)).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new NotEqualExpression(new DummyExpression(a), new DummyExpression(a)).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new NotEqualExpression(new DummyExpression(b), new DummyExpression(b)).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
    }

    @Property
    public void propDivide(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        ColumnLookup columnLookup = TFunctionUtil.dummyColumnLookup();
        if (DataTypeUtility.isNumber(a) && DataTypeUtility.isNumber(b))
        {
            // Will actually type-check
            Unit aOverB = TFunctionUtil.getUnit(a).divideBy(TFunctionUtil.getUnit(b));
            Unit bOverA = TFunctionUtil.getUnit(b).divideBy(TFunctionUtil.getUnit(a));
            assertEquals(new NumTypeExp(null, UnitExp.fromConcrete(aOverB)), new DivideExpression(new DummyExpression(a), new DummyExpression(b)).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
            assertEquals(new NumTypeExp(null, UnitExp.fromConcrete(bOverA)), new DivideExpression(new DummyExpression(b), new DummyExpression(a)).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        }
        else
        {
            assertEquals(null, new DivideExpression(new DummyExpression(a), new DummyExpression(b)).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
            assertEquals(null, new DivideExpression(new DummyExpression(b), new DummyExpression(a)).checkExpression(columnLookup, TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        }
    }

    @Property
    @SuppressWarnings({"i18n", "deprecation"}) // Because of assumeThat 
    public void testArray(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker) throws InternalException, UserException
    {
        DataType a = typeMaker.makeType().getDataType();
        DataType b = typeMaker.makeType().getDataType();
        boolean same = a.equals(b);
        Assume.assumeThat(same, Matchers.<Boolean>equalTo(false));

        List<DataType> types = new ArrayList<>();
        for (int length = 1; length < 10; length++)
        {
            // Add once more as length increases:
            types.add(a);
            // All a should type check:
            assertEquals(DataType.array(a), checkConcrete(typeMaker.getTypeManager(), new ArrayExpression(Utility.mapListExI(types, DummyExpression::new))));

            for (int i = 0; i <= length; i++)
            {
                // Once we add b in, should fail to type check:
                List<DataType> badTypes = new ArrayList<>(types);
                badTypes.add(i, b);
                assertEquals(null, checkConcrete(typeMaker.getTypeManager(), new ArrayExpression(Utility.mapListExI(badTypes, DummyExpression::new))));
            }
        }
    }

    // Tests non-numeric types in raise expressions
    @Property
    public void propRaiseNonNumeric(@From(GenDataType.class) DataType a) throws UserException, InternalException
    {
        Assume.assumeFalse(DataTypeUtility.isNumber(a));
        assertNull(new RaiseExpression(new DummyExpression(a), new DummyExpression(DataType.NUMBER)).checkExpression(TFunctionUtil.dummyColumnLookup(), TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertNull(new RaiseExpression(new DummyExpression(DataType.NUMBER), new DummyExpression(a)).checkExpression(TFunctionUtil.dummyColumnLookup(), TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer()));
    }

    @Property
    public void propRaiseNumeric(@From(GenUnit.class) Unit unit) throws UserException, InternalException
    {
        // The rules for raise (besides both must be numeric) are:
        // RHS unit is forbidden
        // LHS plain and RHS plain are fine, for any value of RHS
        // LHS units and RHS variable is banned (only constant RHS)
        // LHS units and RHS integer is always fine
        // LHS units and RHS 1/integer is ok if all unit powers are divisible by the integer
        // LHS units and any other value is not ok.

        // Any unit but scalar:
        Assume.assumeFalse(unit.equals(Unit.SCALAR));

        // No units on RHS:
        DataType unitNum = DataType.number(new NumberInfo(unit));
        assertEquals(null, checkConcrete(new RaiseExpression(new DummyExpression(DataType.NUMBER), new DummyExpression(unitNum))));
        // Plain on both is fine, even when RHS doesn't constant fold:
        assertEquals(DataType.NUMBER, checkConcrete(new RaiseExpression(new DummyExpression(DataType.NUMBER), new DummyExpression(DataType.NUMBER))));
        // LHS units is banned if RHS doesn't constant fold:
        assertEquals(null, checkConcrete(new RaiseExpression(new DummyExpression(unitNum), new DummyExpression(DataType.NUMBER))));
        // LHS units and RHS integer is fine:
        assertEquals(unitNum, checkConcrete(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.ONE))));
        assertEquals(DataType.NUMBER, checkConcrete(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.ZERO))));
        assertEquals(DataType.number(new NumberInfo(unit.raisedTo(5))), checkConcrete(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.of(5)))));
        assertEquals(DataType.number(new NumberInfo(unit.reciprocal())), checkConcrete(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.of(-1)))));
        assertEquals(DataType.number(new NumberInfo(unit.raisedTo(3).reciprocal())), checkConcrete(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.of(-3)))));
        // 1/integer is ok if all units divisible:
        assertEquals(unitNum, checkConcrete(new RaiseExpression(new DummyExpression(DataType.number(new NumberInfo(unit.raisedTo(3)))), new DummyConstExpression(DataType.NUMBER, Rational.ofLongs(1L, 3L)))));
        // Any other rational not allowed:
        assertEquals(null, checkConcrete(new RaiseExpression(new DummyExpression(DataType.number(new NumberInfo(unit.raisedTo(6)))), new DummyConstExpression(DataType.NUMBER, Rational.ofLongs(2L, 3L)))));
    }

    @Property
    public void propAndOr(@From(GenDataType.class) DataType nonBool) throws InternalException, UserException
    {
        Assume.assumeFalse(nonBool.equals(DataType.BOOLEAN));

        for (Function<List<Expression>, Expression> create : Arrays.<Function<List<Expression>, Expression>>asList(AndExpression::new, OrExpression::new))
        {
            assertEquals(TypeExp.bool(null), check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN)))));
            assertEquals(TypeExp.bool(null), check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN)))));
            assertEquals(TypeExp.bool(null), check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN)))));

            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(nonBool)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(nonBool)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(nonBool), new DummyExpression(DataType.BOOLEAN)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN), new DummyExpression(nonBool)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(nonBool), new DummyExpression(DataType.BOOLEAN)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(nonBool), new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN)))));
        }
    }

    @Property
    public void propComparison(@From(GenDataType.class) DataType main, @From(GenDataType.class) DataType other) throws InternalException, UserException
    {        
        // Must be different types:
        Assume.assumeFalse(main.equals(other));

        assertEquals(TypeExp.bool(null), check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN))));
        assertEquals(TypeExp.bool(null), check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(main), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO))));

        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(other), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN))));
        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(other)), ImmutableList.of(ComparisonOperator.GREATER_THAN))));
        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(other), new DummyExpression(main), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO))));
        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(other), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO))));
        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(main), new DummyExpression(other)), ImmutableList.of(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO))));
    }

    // TODO typecheck all the compound expressions (addsubtract, times, tag)

    @Property
    public void checkMatch(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker) throws InternalException, UserException
    {
        DataType matchType = typeMaker.makeType().getDataType();
        DataType resultType = typeMaker.makeType().getDataType();
        DataType otherType = typeMaker.makeType().getDataType();
        
        // Doesn't matter if match is same as result, but other must be different than both to make failures actually fail:
        Assume.assumeFalse(matchType.equals(otherType));
        Assume.assumeFalse(resultType.equals(otherType));

        // Not valid to have zero clauses as can't determine result type:
        assertEquals(null, check(new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), new DummyExpression(matchType), ImmutableList.of(), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO))));

        // One clause with one pattern, all checks out:
        assertEquals(resultType, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), new DummyExpression(matchType), ImmutableList.of(MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO))));
        // One clause, two patterns
        assertEquals(resultType, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), new DummyExpression(matchType), ImmutableList.of(MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO))));
        // Two clauses, two patterns each
        assertEquals(resultType, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), new DummyExpression(matchType), ImmutableList.of(MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO))));
        // Two clauses, two patterns, one pattern doesn't match:
        assertEquals(null, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), new DummyExpression(matchType), ImmutableList.of(MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(otherType), null)), new DummyExpression(resultType))), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO))));
        // Two clauses, two patterns, one clause doesn't match:
        assertEquals(null, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), new DummyExpression(matchType), ImmutableList.of(MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(otherType), null), new Pattern(new DummyPatternMatch(otherType), null)), new DummyExpression(resultType)), MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO))));
        // Two clauses, two patterns, one result doesn't match:
        assertEquals(null, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), new DummyExpression(matchType), ImmutableList.of(MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), MatchClause.unrecorded(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(otherType))), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO))));
    }

    private static @Nullable TypeExp check(Expression e) throws UserException, InternalException
    {
        return e.checkExpression(TFunctionUtil.dummyColumnLookup(), TFunctionUtil.typeState(), new ErrorAndTypeRecorderStorer());
    }

    private static @Nullable DataType checkConcrete(Expression e) throws UserException, InternalException
    {
        return checkConcrete(DummyManager.make().getTypeManager(), e);
    }

    private static @Nullable DataType checkConcrete(TypeManager typeManager, Expression e) throws UserException, InternalException
    {
        TypeExp typeExp = e.checkExpression(TFunctionUtil.dummyColumnLookup(), TFunctionUtil.createTypeState(typeManager), new ErrorAndTypeRecorderStorer());
        if (typeExp == null)
            return null;
        else
            return typeExp.toConcreteType(typeManager).<@Nullable DataType>either(err -> null, t -> t);
    }

    private static class DummyRecordSet extends KnownLengthRecordSet
    {
        public DummyRecordSet() throws InternalException, UserException
        {
            super(Collections.emptyList(), 0);
        }
    }

    private static class DummyPatternMatch extends NonOperatorExpression
    {
        private final DataType expected;

        private DummyPatternMatch(DataType expected)
        {
            this.expected = expected;
        }

        @Override
        public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
        {
            return onError.recordTypeAndError(this, Either.right(TypeExp.fromDataType(this, expected)), state);
        }

        @Override
        public ValueResult calculateValue(EvaluateState state) throws InternalException
        {
            throw new InternalException("Should not be called");
        }

        @Override
        public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
        {
            return "Testing";
        }

        @Override
        protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
        {
            return StyledString.s("Testing");
        }

        @Override
        public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
        {
            return Stream.empty();
        }

        @Override
        public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
        {
            return null;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            return false;
        }

        @Override
        public int hashCode()
        {
            return 0;
        }

        @Override
        public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return this == toReplace ? replaceWith : this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T visit(ExpressionVisitor<T> visitor)
        {
            // Hack -- we know we should only be called with Stream visitors:
            return (T)Stream.of();
        }
    }
    
    @Test
    public void checkUnits() throws InternalException, UserException
    {
        Unit m = DummyManager.make().getUnitManager().loadUse("m");
        checkConcreteType(DataType.BOOLEAN, "1 < 2");
        checkConcreteType(DataType.BOOLEAN, "1 < 2{m}");
        checkConcreteType(DataType.BOOLEAN, "1 < 2{m} <= 3");
        checkConcreteType(DataType.BOOLEAN, "1 = 2");
        checkConcreteType(DataType.BOOLEAN, "1 = 2{m}");
        checkConcreteType(DataType.NUMBER, "1 + 2");
        checkConcreteType(DataType.number(new NumberInfo(m)), "1 + 2{m}");
        checkConcreteType(DataType.number(new NumberInfo(m)), "1 * 2{m}");
        checkConcreteType(DataType.NUMBER, "1 * 2");
        checkConcreteType((DataType)null, "@call function\\\\abs(1) + 2{m}");
        checkConcreteType(DataType.number(new NumberInfo(m)), "@call function\\\\abs(1) * 2{m}");
        checkConcreteType(DataType.NUMBER, "@call function\\\\abs(1)");
        checkConcreteType(DataType.number(new NumberInfo(m)), "@call function\\\\abs(1{m})");
        checkConcreteType(DataType.record(ImmutableMap.of("a", DataType.NUMBER, "b", DataType.NUMBER)), "(a:1, b:2{1})");
    }

    private void checkConcreteType(@Nullable DataType dataType, String expression) throws InternalException, UserException
    {
        TypeManager typeManager = TFunctionUtil.managerWithTestTypes().getFirst().getTypeManager();
        assertEquals(expression, dataType, checkConcrete(typeManager, TFunctionUtil.parseExpression(expression, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()))));
    }

    private void checkConcreteType(ExFunction<TypeManager, DataType> dataType, String expression) throws InternalException, UserException
    {
        TypeManager typeManager = TFunctionUtil.managerWithTestTypes().getFirst().getTypeManager();
        assertEquals(expression, dataType.apply(typeManager), checkConcrete(typeManager, TFunctionUtil.parseExpression(expression, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()))));
    }

    @Test
    public void checkFromTextFunctions() throws UserException, InternalException
    {
        checkConcreteType(DataType.BOOLEAN, "@call function\\\\from text to(type{Boolean}, \"true\")");
    }

    @Test
    public void checkFromTextFunctions2() throws UserException, InternalException
    {
        checkConcreteType(m -> TBasicUtil.checkNonNull(m.lookupType(new TypeId("A"), ImmutableList.of())), "@call function\\\\from text to(type{A}, \"Hello\")");
    }
    
    @Test
    public void checkNestedFunctions() throws UserException, InternalException
    {
        checkConcreteType(m -> TBasicUtil.checkNonNull(m.lookupType(new TypeId("A"), ImmutableList.of())), "@call function\\\\from text to(type{A}, @call function\\\\from text to(type{Text}, \"Hello\"))");
    }

    @Test
    public void checkDefine1() throws UserException, InternalException
    {
        checkConcreteType(DataType.BOOLEAN, "@define x = true @then x @enddefine");
    }

    @Test
    public void checkDefine2() throws UserException, InternalException
    {
        checkConcreteType(m -> m.getMaybeType().instantiate(ImmutableList.of(Either.right(DataType.number(new NumberInfo(m.getUnitManager().loadUse("m"))))), m), "@define dist = (1 * 32{m/s} * 10{s}) @then @call tag\\\\Optional\\Is(dist * 3) @enddefine");
    }

    @Test
    public void checkDefine2b() throws UserException, InternalException
    {
        checkConcreteType(m -> m.getMaybeType().instantiate(ImmutableList.of(Either.right(DataType.number(new NumberInfo(m.getUnitManager().loadUse("m"))))), m), "@define dist :: type{Number{m}}, dist = (1 * 32{m/s} * 10{s}) @then @call tag\\\\Optional\\Is(dist * 3) @enddefine");
    }

    @Test
    public void checkDefine3() throws UserException, InternalException
    {
        checkConcreteType(DataType.BOOLEAN, "@define a :: type{Boolean}, b :: type{Boolean}, [a, b] = [true, false] @then a & b @enddefine");
    }

    @Test
    public void checkDefine3b() throws UserException, InternalException
    {
        checkConcreteType(DataType.BOOLEAN, "@define a :: type{Boolean}, b :: type{Boolean}, (y:a, x:b) = (x:true, y:false) @then a & b @enddefine");
    }

    @Test
    public void checkDefine4() throws UserException, InternalException
    {
        checkConcreteType(DataType.BOOLEAN, "@define a :: type{Boolean}, a = @call function\\\\from text(\"\"), b = a @then b @enddefine");
    }

    @Test
    public void checkDefineErr1() throws UserException, InternalException
    {
        checkConcreteType((DataType)null, "@define x = x @then x @enddefine");
    }

    @Test
    public void checkDefineErr2() throws UserException, InternalException
    {
        checkConcreteType((DataType)null, "@define x = [] @then x @enddefine");
    }

    @Test
    public void checkDefineErr3() throws UserException, InternalException
    {
        // Typing variable we don't define
        checkConcreteType((DataType)null, "@define dist2 :: type{@apply Optional(Number{m})}, dist = (1 * 32{m/s} * 10{s}) @then @call tag\\\\Optional\\Is(dist * 3) @enddefine");
    }

    @Test
    public void checkDefineErr3b() throws UserException, InternalException
    {
        // Type after definition
        checkConcreteType((DataType)null, "@define dist = (1 * 32{m/s} * 10{s}), dist :: type{@apply Optional(Number{m})} @then @call tag\\\\Optional\\Is(dist * 3) @enddefine");
    }

    @Test
    public void checkDefineErr4() throws UserException, InternalException
    {
        // Duplicate definition:
        checkConcreteType((DataType)null, "@define x = 1, x = 1 @then x @enddefine");
    }

    @Test
    public void checkDefineErr5() throws UserException, InternalException
    {
        // Duplicate definition incl type:
        checkConcreteType((DataType)null, "@define x :: type{Number}, x = 1, x = 1 @then x @enddefine");
    }
    
    @Test
    public void checkFunction1() throws UserException, InternalException
    {
        checkConcreteType(DataType.NUMBER, "@define f = (? + 1) @then @call f(3) @enddefine");
    }

    @Test
    public void checkFunction1b() throws UserException, InternalException
    {
        checkConcreteType(DataType.NUMBER, "@define f :: type{@apply Function(Number)(Number)}, f = (? + 1) @then @call f(3) @enddefine");
    }

    @Test
    public void checkFunction2() throws UserException, InternalException
    {
        checkConcreteType(DataType.NUMBER, "@define f = @function (x) @then x + 1 @endfunction @then @call f(3) @enddefine");
    }

    @Test
    public void checkFunction2b() throws UserException, InternalException
    {
        checkConcreteType(DataType.NUMBER, "@define f :: type{@apply Function(Number)(Number)}, f = @function (x) @then x + 1 @endfunction @then @call f(3) @enddefine");
    }
    
    @Test
    public void checkRecord1() throws UserException, InternalException
    {
        checkConcreteType(DataType.record(ImmutableMap.of("a", DataType.NUMBER, "b", DataType.TEXT)), "(a : 3, b : \"Hi\")");
    }

    @Test
    public void checkRecord2() throws UserException, InternalException
    {
        checkConcreteType(DataType.array(DataType.record(ImmutableMap.of("a", DataType.NUMBER, "b", DataType.TEXT))), "[(a : 3, b : \"Hi\"), (b : \"\", a: (3 * 4))]");
    }

    @Test
    public void checkRecord3() throws UserException, InternalException
    {
        checkConcreteType((DataType)null, "[(a : 3, b : \"Hi\"), (b : \"\")]");
    }

    @Test
    public void checkRecord3b() throws UserException, InternalException
    {
        checkConcreteType((DataType)null, "[(a : 3, b : \"Hi\"), (a: true, b : \"\")]");
    }

    @Test
    public void checkField() throws UserException, InternalException
    {
        checkConcreteType(DataType.TEXT, "(a : 3, b : \"Hi\")#b");
    }

    @Test
    public void checkField2() throws UserException, InternalException
    {
        checkConcreteType(DataType.TEXT, "@define var\\\\abs = [(a : 3, b : \"Hi\"), (b : \"\", a: (3 * 4))] @then @call function\\\\element(var\\\\abs, 1)#b @enddefine");
    }

    @Test
    public void checkField3() throws UserException, InternalException
    {
        checkConcreteType(m -> DataType.number(new NumberInfo(m.getUnitManager().loadUse("m").times(m.getUnitManager().loadUse("inch")))), "@define record = (x: 36{m}, y : 37{inch}), getX = (?#x) , getY = (?#y) @then @call getX(record) * @call getY(record) @enddefine");
    }
    

}
