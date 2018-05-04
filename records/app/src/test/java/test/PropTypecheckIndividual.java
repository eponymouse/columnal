package test;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import records.data.KnownLengthRecordSet;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.transformations.expression.*;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.Expression.TableLookup;
import records.transformations.expression.MatchExpression.Pattern;
import records.types.NumTypeExp;
import records.types.TypeExp;
import records.types.units.UnitExp;
import styled.StyledString;
import test.gen.GenDataType;
import test.gen.GenUnit;
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
import static org.junit.Assert.fail;

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
        public @Nullable TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
        {
            return onError.recordType(this, TypeExp.fromConcrete(this, type));
        }

        @Override
        public @Value Object getValue(EvaluateState state) throws UserException, InternalException
        {
            throw new InternalException("Testing");
        }

        @Override
        public Stream<ColumnReference> allColumnReferences()
        {
            return Stream.empty();
        }

        @Override
        public String save(BracketedStatus surround, TableAndColumnRenames renames)
        {
            return "Testing";
        }

        @Override
        protected StyledString toDisplay(BracketedStatus bracketedStatus)
        {
            return StyledString.s("Testing");
        }

        @Override
        public Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
        {
            throw new RuntimeException("Testing");
        }

        @Override
        public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
        {
            throw new RuntimeException("Testing");
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
    @SuppressWarnings({"i18n", "deprecation"}) // Because of assumeThat, bizarrely
    public void propEquals(@From(GenDataType.class) GenDataType.DataTypeAndManager am, @From(GenDataType.class) GenDataType.DataTypeAndManager bm) throws InternalException, UserException
    {
        DataType a = am.dataType;
        DataType b = bm.dataType;
        boolean same = DataType.checkSame(a, b, s -> {}) != null;
        Assume.assumeThat(same, Matchers.<Boolean>equalTo(false));

        TableLookup tableLookup = id -> null;
        assertEquals(null, new EqualExpression(ImmutableList.of(new DummyExpression(a), new DummyExpression(b))).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new EqualExpression(ImmutableList.of(new DummyExpression(a), new DummyExpression(a))).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new EqualExpression(ImmutableList.of(new DummyExpression(b), new DummyExpression(b))).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(null, new NotEqualExpression(new DummyExpression(a), new DummyExpression(b)).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new NotEqualExpression(new DummyExpression(a), new DummyExpression(a)).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(TypeExp.bool(null), new NotEqualExpression(new DummyExpression(b), new DummyExpression(b)).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
    }

    @Property
    public void propDivide(@From(GenDataType.class) GenDataType.DataTypeAndManager am, @From(GenDataType.class) GenDataType.DataTypeAndManager bm) throws InternalException, UserException
    {
        DataType a = am.dataType;
        DataType b = bm.dataType;
        TableLookup tableLookup = id -> null;
        if (a.isNumber() && b.isNumber())
        {
            // Will actually type-check
            Unit aOverB = a.getNumberInfo().getUnit().divideBy(b.getNumberInfo().getUnit());
            Unit bOverA = b.getNumberInfo().getUnit().divideBy(a.getNumberInfo().getUnit());
            assertEquals(new NumTypeExp(null, UnitExp.fromConcrete(aOverB, u -> null)), new DivideExpression(new DummyExpression(a), new DummyExpression(b)).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
            assertEquals(new NumTypeExp(null, UnitExp.fromConcrete(bOverA, u -> null)), new DivideExpression(new DummyExpression(b), new DummyExpression(a)).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        }
        else
        {
            assertEquals(null, new DivideExpression(new DummyExpression(a), new DummyExpression(b)).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
            assertEquals(null, new DivideExpression(new DummyExpression(b), new DummyExpression(a)).check(tableLookup, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        }
    }

    @Property
    @SuppressWarnings({"i18n", "deprecation"}) // Because of assumeThat, bizarrely
    public void testArray(@From(GenDataType.class) GenDataType.DataTypeAndManager am, @From(GenDataType.class) GenDataType.DataTypeAndManager bm) throws InternalException, UserException
    {
        DataType a = am.dataType;
        DataType b = bm.dataType;
        boolean same = DataType.checkSame(a, b, s -> {}) != null;
        Assume.assumeThat(same, Matchers.<Boolean>equalTo(false));

        List<DataType> types = new ArrayList<>();
        for (int length = 1; length < 10; length++)
        {
            // Add once more as length increases:
            types.add(a);
            // All a should type check:
            assertEquals(DataType.array(a), checkConcrete(am.typeManager, new ArrayExpression(Utility.mapListExI(types, DummyExpression::new))));

            for (int i = 0; i <= length; i++)
            {
                // Once we add b in, should fail to type check:
                List<DataType> badTypes = new ArrayList<>(types);
                badTypes.add(i, b);
                assertEquals(null, checkConcrete(am.typeManager, new ArrayExpression(Utility.mapListExI(badTypes, DummyExpression::new))));
            }
        }
    }

    // Tests non-numeric types in raise expressions
    @Property
    public void propRaiseNonNumeric(@From(GenDataType.class) GenDataType.DataTypeAndManager am) throws UserException, InternalException
    {
        DataType a = am.dataType;
        Assume.assumeFalse(a.isNumber());
        assertEquals(null, new RaiseExpression(new DummyExpression(a), new DummyExpression(DataType.NUMBER)).check(id -> null, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
        assertEquals(null, new RaiseExpression(new DummyExpression(DataType.NUMBER), new DummyExpression(a)).check(id -> null, TestUtil.typeState(), new ErrorAndTypeRecorderStorer()));
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
    public void propAndOr(@From(GenDataType.class) GenDataType.DataTypeAndManager nonBoolM) throws InternalException, UserException
    {
        DataType nonBool = nonBoolM.dataType;
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
    public void propComparison(@From(GenDataType.class) GenDataType.DataTypeAndManager mainM, @From(GenDataType.class) GenDataType.DataTypeAndManager otherM) throws InternalException, UserException
    {
        DataType main = mainM.dataType;
        DataType other = otherM.dataType;
        
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
    public void checkMatch(@From(GenDataType.class) GenDataType.DataTypeAndManager match, @From(GenDataType.class) GenDataType.DataTypeAndManager result, @From(GenDataType.class) GenDataType.DataTypeAndManager other) throws InternalException, UserException
    {
        DataType matchType = match.dataType;
        DataType resultType = result.dataType;
        DataType otherType = other.dataType;
        
        // Doesn't matter if match is same as result, but other must be different than both to make failures actually fail:
        Assume.assumeFalse(DataType.checkSame(matchType, otherType, s -> {}) != null);
        Assume.assumeFalse(DataType.checkSame(resultType, otherType, s -> {}) != null);

        // Not valid to have zero clauses as can't determine result type:
        assertEquals(null, check(new MatchExpression(new DummyExpression(matchType), Collections.emptyList())));

        // One clause with one pattern, all checks out:
        assertEquals(resultType, checkConcrete(match.typeManager, new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // One clause, two patterns
        assertEquals(resultType, checkConcrete(match.typeManager, new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns each
        assertEquals(resultType, checkConcrete(match.typeManager, new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one pattern doesn't match:
        assertEquals(null, checkConcrete(match.typeManager, new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(otherType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one clause doesn't match:
        assertEquals(null, checkConcrete(match.typeManager, new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(otherType), null), new Pattern(new DummyPatternMatch(otherType), null)), new DummyExpression(resultType)), me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one result doesn't match:
        assertEquals(null, checkConcrete(match.typeManager, new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(otherType))))));
    }

    private static @Nullable TypeExp check(Expression e) throws UserException, InternalException
    {
        return e.check(id -> null, TestUtil.typeState(), new ErrorAndTypeRecorderStorer());
    }

    private static @Nullable DataType checkConcrete(Expression e) throws UserException, InternalException
    {
        return checkConcrete(DummyManager.INSTANCE.getTypeManager(), e);
    }

    private static @Nullable DataType checkConcrete(TypeManager typeManager, Expression e) throws UserException, InternalException
    {
        TypeExp typeExp = e.check(id -> null, TestUtil.typeState(), new ErrorAndTypeRecorderStorer());
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
        public @Nullable TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
        {
            throw new InternalException("Should not be called");
        }

        @Override
        public @Nullable Pair<@Recorded TypeExp, TypeState> checkAsPattern(boolean varAllowed, TableLookup data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
        {
            return new Pair<>(onError.recordTypeNN(this, TypeExp.fromConcrete(this, expected)), state);
        }

        @Override
        public @Value Object getValue(EvaluateState state) throws UserException, InternalException
        {
            throw new InternalException("Should not be called");
        }

        @Override
        public Stream<ColumnReference> allColumnReferences()
        {
            return Stream.empty();
        }

        @Override
        public String save(BracketedStatus surround, TableAndColumnRenames renames)
        {
            return "Testing";
        }

        @Override
        protected StyledString toDisplay(BracketedStatus bracketedStatus)
        {
            return StyledString.s("Testing");
        }

        @Override
        public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
        {
            throw new RuntimeException("N/A");
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
    }
}
