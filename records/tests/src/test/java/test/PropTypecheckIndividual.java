package test;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import records.data.KnownLengthRecordSet;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.AndExpression;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.LocationInfo;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.NonOperatorExpression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.OrExpression;
import records.transformations.expression.RaiseExpression;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionList;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import test.gen.type.GenDataType;
import test.gen.GenUnit;
import test.gen.type.GenDataTypeMaker;
import utility.Either;
import utility.ExFunction;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 24/01/2017.
 */
@SuppressWarnings("recorded")
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
        public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
        {
            return onError.recordType(this, ExpressionKind.EXPRESSION, state, TypeExp.fromDataType(this, type));
        }

        @Override
        public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
        {
            throw new InternalException("Testing");
        }

        @Override
        public Stream<ColumnReference> allColumnReferences()
        {
            return Stream.empty();
        }

        @Override
        public Stream<String> allVariableReferences()
        {
            return Stream.empty();
        }

        @Override
        public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
        {
            return "Testing";
        }

        @Override
        protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
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
        boolean same = DataType.checkSame(a, b, s -> {}) != null;
        Assume.assumeThat(same, Matchers.<Boolean>equalTo(false));

        ColumnLookup columnLookup = TestUtil.dummyColumnLookup();
        assertEquals(null, new EqualExpression(ImmutableList.of(new DummyExpression(a), new DummyExpression(b))).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new EqualExpression(ImmutableList.of(new DummyExpression(a), new DummyExpression(a))).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new EqualExpression(ImmutableList.of(new DummyExpression(b), new DummyExpression(b))).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(null, new NotEqualExpression(new DummyExpression(a), new DummyExpression(b)).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new NotEqualExpression(new DummyExpression(a), new DummyExpression(a)).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new NotEqualExpression(new DummyExpression(b), new DummyExpression(b)).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
    }

    @Property
    public void propDivide(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        ColumnLookup columnLookup = TestUtil.dummyColumnLookup();
        if (a.isNumber() && b.isNumber())
        {
            // Will actually type-check
            Unit aOverB = a.getNumberInfo().getUnit().divideBy(b.getNumberInfo().getUnit());
            Unit bOverA = b.getNumberInfo().getUnit().divideBy(a.getNumberInfo().getUnit());
            assertEquals(new NumTypeExp(null, UnitExp.fromConcrete(aOverB)), new DivideExpression(new DummyExpression(a), new DummyExpression(b)).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
            assertEquals(new NumTypeExp(null, UnitExp.fromConcrete(bOverA)), new DivideExpression(new DummyExpression(b), new DummyExpression(a)).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        }
        else
        {
            assertEquals(null, new DivideExpression(new DummyExpression(a), new DummyExpression(b)).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
            assertEquals(null, new DivideExpression(new DummyExpression(b), new DummyExpression(a)).checkExpression(columnLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        }
    }

    @Property
    @SuppressWarnings({"i18n", "deprecation"}) // Because of assumeThat 
    public void testArray(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker) throws InternalException, UserException
    {
        DataType a = typeMaker.makeType().getDataType();
        DataType b = typeMaker.makeType().getDataType();
        boolean same = DataType.checkSame(a, b, s -> {}) != null;
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
        Assume.assumeFalse(a.isNumber());
        assertEquals(null, new RaiseExpression(new DummyExpression(a), new DummyExpression(DataType.NUMBER)).check(TestUtil.dummyColumnLookup(), TestUtil.typeState(), LocationInfo.UNIT_DEFAULT, new ErrorAndTypeRecorderStorer()));
        assertEquals(null, new RaiseExpression(new DummyExpression(DataType.NUMBER), new DummyExpression(a)).check(TestUtil.dummyColumnLookup(), TestUtil.typeState(), LocationInfo.UNIT_DEFAULT, new ErrorAndTypeRecorderStorer()));
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
        Assume.assumeFalse(DataType.checkSame(main, other, s -> {}) != null);

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
        Assume.assumeFalse(DataType.checkSame(matchType, otherType, s -> {}) != null);
        Assume.assumeFalse(DataType.checkSame(resultType, otherType, s -> {}) != null);

        // Not valid to have zero clauses as can't determine result type:
        assertEquals(null, check(new MatchExpression(new DummyExpression(matchType), ImmutableList.of())));

        // One clause with one pattern, all checks out:
        assertEquals(resultType, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new DummyExpression(matchType), ImmutableList.of(new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // One clause, two patterns
        assertEquals(resultType, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new DummyExpression(matchType), ImmutableList.of(new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns each
        assertEquals(resultType, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new DummyExpression(matchType), ImmutableList.of(new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one pattern doesn't match:
        assertEquals(null, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new DummyExpression(matchType), ImmutableList.of(new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(otherType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one clause doesn't match:
        assertEquals(null, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new DummyExpression(matchType), ImmutableList.of(new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(otherType), null), new Pattern(new DummyPatternMatch(otherType), null)), new DummyExpression(resultType)), new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one result doesn't match:
        assertEquals(null, checkConcrete(typeMaker.getTypeManager(), new MatchExpression(new DummyExpression(matchType), ImmutableList.of(new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), new MatchClause(
            ImmutableList.of(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(otherType))))));
    }

    private static @Nullable TypeExp check(Expression e) throws UserException, InternalException
    {
        return e.checkExpression(TestUtil.dummyColumnLookup(), TestUtil.typeState(), new ErrorAndTypeRecorderStorer());
    }

    private static @Nullable DataType checkConcrete(Expression e) throws UserException, InternalException
    {
        return checkConcrete(DummyManager.make().getTypeManager(), e);
    }

    private static @Nullable DataType checkConcrete(TypeManager typeManager, Expression e) throws UserException, InternalException
    {
        TypeExp typeExp = e.checkExpression(TestUtil.dummyColumnLookup(), new TypeState(typeManager.getUnitManager(), typeManager), new ErrorAndTypeRecorderStorer());
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
        public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
        {
            return onError.recordTypeAndError(this, Either.right(TypeExp.fromDataType(this, expected)), ExpressionKind.PATTERN, state);
        }

        @Override
        public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
        {
            throw new InternalException("Should not be called");
        }

        @Override
        public Stream<ColumnReference> allColumnReferences()
        {
            return Stream.empty();
        }

        @Override
        public Stream<String> allVariableReferences()
        {
            return Stream.empty();
        }

        @Override
        public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
        {
            return "Testing";
        }

        @Override
        protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
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
        checkConcreteType((DataType)null, "@call @function abs(1) + 2{m}");
        checkConcreteType(DataType.number(new NumberInfo(m)), "@call @function abs(1) * 2{m}");
        checkConcreteType(DataType.NUMBER, "@call @function abs(1)");
        checkConcreteType(DataType.number(new NumberInfo(m)), "@call @function abs(1{m})");
        checkConcreteType(DataType.tuple(DataType.NUMBER, DataType.NUMBER), "(1, 2{1})");
    }

    private void checkConcreteType(@Nullable DataType dataType, String expression) throws InternalException, UserException
    {
        TypeManager typeManager = TestUtil.managerWithTestTypes().getFirst().getTypeManager();
        assertEquals(expression, dataType, checkConcrete(typeManager, Expression.parse(null, expression, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()))));
    }

    private void checkConcreteType(ExFunction<TypeManager, DataType> dataType, String expression) throws InternalException, UserException
    {
        TypeManager typeManager = TestUtil.managerWithTestTypes().getFirst().getTypeManager();
        assertEquals(expression, dataType.apply(typeManager), checkConcrete(typeManager, Expression.parse(null, expression, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()))));
    }

    @Test
    public void checkFromTextFunctions() throws UserException, InternalException
    {
        checkConcreteType(DataType.BOOLEAN, "@call @function from text to(type{Boolean}, \"true\")");
    }

    @Test
    public void checkFromTextFunctions2() throws UserException, InternalException
    {
        checkConcreteType(m -> TestUtil.checkNonNull(m.lookupType(new TypeId("A"), ImmutableList.of())), "@call @function from text to(type{A}, \"Hello\")");
    }
    
    @Test
    public void checkNestedFunctions() throws UserException, InternalException
    {
        checkConcreteType(m -> TestUtil.checkNonNull(m.lookupType(new TypeId("A"), ImmutableList.of())), "@call @function from text to(type{A}, @call @function from text to(type{Text}, \"Hello\"))");
    }
}
