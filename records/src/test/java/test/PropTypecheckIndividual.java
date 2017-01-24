package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.Column;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.TypeState;
import test.gen.GenDataType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

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
        public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
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

    @Property
    public void testEquals(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        boolean same = DataType.checkSame(a, b, s -> {}) != null;
        Assume.assumeThat(same, Matchers.equalTo(false));

        assertEquals(null, new EqualExpression(new DummyExpression(a), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
        assertEquals(DataType.BOOLEAN, new EqualExpression(new DummyExpression(a), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
        assertEquals(DataType.BOOLEAN, new EqualExpression(new DummyExpression(b), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
        assertEquals(null, new NotEqualExpression(new DummyExpression(a), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
        assertEquals(DataType.BOOLEAN, new NotEqualExpression(new DummyExpression(a), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
        assertEquals(DataType.BOOLEAN, new NotEqualExpression(new DummyExpression(b), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
    }

    @Property
    public void testDivide(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        if (a.isNumber() && b.isNumber())
        {
            // Will actually type-check
            Unit aOverB = a.getNumberInfo().getUnit().divide(b.getNumberInfo().getUnit());
            Unit bOverA = b.getNumberInfo().getUnit().divide(a.getNumberInfo().getUnit());
            assertEquals(DataType.number(new NumberInfo(aOverB, 0)), new DivideExpression(new DummyExpression(a), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
            assertEquals(DataType.number(new NumberInfo(bOverA, 0)), new DivideExpression(new DummyExpression(b), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
        }
        else
        {
            assertEquals(null, new DivideExpression(new DummyExpression(a), new DummyExpression(b)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
            assertEquals(null, new DivideExpression(new DummyExpression(b), new DummyExpression(a)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
        }
    }

    @Property
    public void testArray(@From(GenDataType.class) DataType a, @From(GenDataType.class) DataType b) throws InternalException, UserException
    {
        boolean same = DataType.checkSame(a, b, s -> {}) != null;
        Assume.assumeThat(same, Matchers.equalTo(false));

        List<DataType> types = new ArrayList<>();
        for (int length = 1; length < 10; length++)
        {
            // Add once more as length increases:
            types.add(a);
            // All a should type check:
            assertEquals(DataType.array(a), new ArrayExpression(Utility.mapListExI(types, DummyExpression::new)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));

            for (int i = 0; i <= length; i++)
            {
                // Once we add b in, should fail to type check:
                List<DataType> badTypes = new ArrayList<>(types);
                badTypes.add(i, b);
                assertEquals(null, new ArrayExpression(Utility.mapListExI(badTypes, DummyExpression::new)).check(new DummyRecordSet(), TestUtil.typeState(), (e, s) -> {}));
            }
        }
    }

    private static class DummyRecordSet extends KnownLengthRecordSet
    {
        public DummyRecordSet() throws InternalException, UserException
        {
            super("Dummy", Collections.emptyList(), 0);
        }
    }
}
