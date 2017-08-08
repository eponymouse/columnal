package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.transformations.expression.*;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.MatchExpression.Pattern;
import test.gen.GenDataType;
import test.gen.GenUnit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by neil on 24/01/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropTypecheckIndividual
{
    // A shell expression that exists just to resolve its type checking to a given type.
    private static class DummyExpression extends Expression
    {
        private final @Nullable DataType type;

        private DummyExpression(@Nullable DataType type)
        {
            this.type = type;
        }

        @Override
        public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
        {
            return type;
        }

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
        {
            throw new InternalException("Testing");
        }

        @Override
        public Stream<ColumnId> allColumnNames()
        {
            return Stream.empty();
        }

        @Override
        public String save(boolean topLevel)
        {
            return "Testing";
        }

        @Override
        public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
        {
            throw new InternalException("Testing");
        }

        @Override
        public Pair<List<FXPlatformFunction<ConsecutiveBase, OperandNode>>, List<FXPlatformFunction<ConsecutiveBase, OperatorEntry>>> loadAsConsecutive()
        {
            throw new RuntimeException("Testing");
        }

        @Override
        public FXPlatformFunction<ConsecutiveBase, OperandNode> loadAsSingle()
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

        private DummyConstExpression(@Nullable DataType type, Rational value)
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
    public void testEquals(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        boolean same = DataType.checkSame(a, b, s -> {}) != null;
        Assume.assumeThat(same, Matchers.<Boolean>equalTo(false));

        assertEquals(null, new EqualExpression(new DummyExpression(a), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
        assertEquals(DataType.BOOLEAN, new EqualExpression(new DummyExpression(a), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
        assertEquals(DataType.BOOLEAN, new EqualExpression(new DummyExpression(b), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
        assertEquals(null, new NotEqualExpression(new DummyExpression(a), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
        assertEquals(DataType.BOOLEAN, new NotEqualExpression(new DummyExpression(a), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
        assertEquals(DataType.BOOLEAN, new NotEqualExpression(new DummyExpression(b), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
    }

    @Property
    public void testDivide(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        if (a.isNumber() && b.isNumber())
        {
            // Will actually type-check
            Unit aOverB = a.getNumberInfo().getUnit().divide(b.getNumberInfo().getUnit());
            Unit bOverA = b.getNumberInfo().getUnit().divide(a.getNumberInfo().getUnit());
            assertEquals(DataType.number(new NumberInfo(aOverB, null)), new DivideExpression(new DummyExpression(a), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
            assertEquals(DataType.number(new NumberInfo(bOverA, null)), new DivideExpression(new DummyExpression(b), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
        }
        else
        {
            assertEquals(null, new DivideExpression(new DummyExpression(a), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
            assertEquals(null, new DivideExpression(new DummyExpression(b), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
        }
    }

    @Property
    @SuppressWarnings({"i18n", "deprecation"}) // Because of assumeThat, bizarrely
    public void testArray(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        boolean same = DataType.checkSame(a, b, s -> {}) != null;
        Assume.assumeThat(same, Matchers.<Boolean>equalTo(false));

        List<DataType> types = new ArrayList<>();
        for (int length = 1; length < 10; length++)
        {
            // Add once more as length increases:
            types.add(a);
            // All a should type check:
            assertEquals(DataType.array(a), new ArrayExpression(Utility.mapListExI(types, DummyExpression::new)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));

            for (int i = 0; i <= length; i++)
            {
                // Once we add b in, should fail to type check:
                List<DataType> badTypes = new ArrayList<>(types);
                badTypes.add(i, b);
                assertEquals(null, new ArrayExpression(Utility.mapListExI(badTypes, DummyExpression::new)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
            }
        }
    }

    // Tests non-numeric types in raise expressions
    @Property
    public void testRaiseNonNumeric(@From(GenDataType.class) DataType a) throws UserException, InternalException
    {
        Assume.assumeFalse(a.isNumber());
        assertEquals(null, new RaiseExpression(new DummyExpression(a), new DummyExpression(DataType.NUMBER)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
        assertEquals(null, new RaiseExpression(new DummyExpression(DataType.NUMBER), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s, q) -> {}));
    }

    @Property
    public void testRaiseNumeric(@From(GenUnit.class) Unit unit) throws UserException, InternalException
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
        DataType unitNum = DataType.number(new NumberInfo(unit, null));
        assertEquals(null, check(new RaiseExpression(new DummyExpression(DataType.NUMBER), new DummyExpression(unitNum))));
        // Plain on both is fine, even when RHS doesn't constant fold:
        assertEquals(DataType.NUMBER, check(new RaiseExpression(new DummyExpression(DataType.NUMBER), new DummyExpression(DataType.NUMBER))));
        // LHS units is banned if RHS doesn't constant fold:
        assertEquals(null, check(new RaiseExpression(new DummyExpression(unitNum), new DummyExpression(DataType.NUMBER))));
        // LHS units and RHS integer is fine:
        assertEquals(unitNum, check(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.ONE))));
        assertEquals(DataType.NUMBER, check(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.ZERO))));
        assertEquals(DataType.number(new NumberInfo(unit.raisedTo(5), null)), check(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.of(5)))));
        assertEquals(DataType.number(new NumberInfo(unit.reciprocal(), null)), check(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.of(-1)))));
        assertEquals(DataType.number(new NumberInfo(unit.raisedTo(3).reciprocal(), null)), check(new RaiseExpression(new DummyExpression(unitNum), new DummyConstExpression(DataType.NUMBER, Rational.of(-3)))));
        // 1/integer is ok if all units divisible:
        assertEquals(unitNum, check(new RaiseExpression(new DummyExpression(DataType.number(new NumberInfo(unit.raisedTo(3), null))), new DummyConstExpression(DataType.NUMBER, Rational.ofLongs(1L, 3L)))));
        // Any other rational not allowed:
        assertEquals(null, check(new RaiseExpression(new DummyExpression(DataType.number(new NumberInfo(unit.raisedTo(6), null))), new DummyConstExpression(DataType.NUMBER, Rational.ofLongs(2L, 3L)))));
    }

    @Property
    public void checkAndOr(@From(GenDataType.class) DataType nonBool) throws InternalException, UserException
    {
        Assume.assumeFalse(nonBool.equals(DataType.BOOLEAN));

        for (Function<List<Expression>, Expression> create : Arrays.<Function<List<Expression>, Expression>>asList(AndExpression::new, OrExpression::new))
        {
            assertEquals(DataType.BOOLEAN, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN)))));
            assertEquals(DataType.BOOLEAN, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN)))));
            assertEquals(DataType.BOOLEAN, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN)))));

            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(nonBool)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(nonBool)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(nonBool), new DummyExpression(DataType.BOOLEAN)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN), new DummyExpression(nonBool)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(DataType.BOOLEAN), new DummyExpression(nonBool), new DummyExpression(DataType.BOOLEAN)))));
            assertEquals(null, check(create.apply(Arrays.asList(new DummyExpression(nonBool), new DummyExpression(DataType.BOOLEAN), new DummyExpression(DataType.BOOLEAN)))));
        }
    }

    @Property
    public void checkComparison(@From(GenDataType.class) DataType main, @From(GenDataType.class) DataType other) throws InternalException, UserException
    {
        // Must be different types:
        Assume.assumeFalse(DataType.checkSame(main, other, s -> {}) != null);

        assertEquals(DataType.BOOLEAN, check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN))));
        assertEquals(DataType.BOOLEAN, check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(main), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO))));

        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(other), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN))));
        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(other)), ImmutableList.of(ComparisonOperator.GREATER_THAN))));
        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(other), new DummyExpression(main), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO))));
        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(other), new DummyExpression(main)), ImmutableList.of(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO))));
        assertEquals(null, check(new ComparisonExpression(Arrays.asList(new DummyExpression(main), new DummyExpression(main), new DummyExpression(other)), ImmutableList.of(ComparisonOperator.LESS_THAN, ComparisonOperator.LESS_THAN_OR_EQUAL_TO))));
    }

    // TODO typecheck all the compound expressions (addsubtract, times, tag)

    @Property
    public void checkMatch(@From(GenDataType.class) DataType matchType, @From(GenDataType.class) DataType resultType, @From(GenDataType.class) DataType other) throws InternalException, UserException
    {
        // Doesn't matter if match is same as result, but other must be different than both to make failures actually fail:
        Assume.assumeFalse(DataType.checkSame(matchType, other, s -> {}) != null);
        Assume.assumeFalse(DataType.checkSame(resultType, other, s -> {}) != null);

        // Not valid to have zero clauses as can't determine result type:
        try
        {
            assertEquals(null, check(new MatchExpression(new DummyExpression(matchType), Collections.emptyList())));
            fail("Zero clauses in match should throw exception");
        }
        catch (InternalException e)
        {
            // As expected...
        }

        // One clause with one pattern, all checks out:
        assertEquals(resultType, check(new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // One clause, two patterns
        assertEquals(resultType, check(new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns each
        assertEquals(resultType, check(new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one pattern doesn't match:
        assertEquals(null, check(new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(other), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one clause doesn't match:
        assertEquals(null, check(new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(other), null), new Pattern(new DummyPatternMatch(other), null)), new DummyExpression(resultType)), me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType))))));
        // Two clauses, two patterns, one result doesn't match:
        assertEquals(null, check(new MatchExpression(new DummyExpression(matchType), Arrays.asList(me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(resultType)), me -> me.new MatchClause(
            Arrays.asList(new Pattern(new DummyPatternMatch(matchType), null), new Pattern(new DummyPatternMatch(matchType), null)), new DummyExpression(other))))));
    }

    private static @Nullable DataType check(Expression e) throws UserException, InternalException
    {
        return e.check(new DummyRecordSet(), TestUtil.typeState(), (ex, s, q) -> {});
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
        public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
        {
            throw new InternalException("Should not be called");
        }

        @Override
        public @Nullable Pair<DataType, TypeState> checkAsPattern(boolean varAllowed, DataType srcType, RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
        {
            return DataType.checkSame(srcType, expected, s -> {}) != null ? new Pair<>(srcType, state) : null;
        }

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
        {
            throw new InternalException("Should not be called");
        }

        @Override
        public Stream<ColumnId> allColumnNames()
        {
            return Stream.empty();
        }

        @Override
        public String save(boolean topLevel)
        {
            return "";
        }

        @Override
        public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
        {
            throw new RuntimeException("N/A");
        }

        @Override
        public FXPlatformFunction<ConsecutiveBase, OperandNode> loadAsSingle()
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
