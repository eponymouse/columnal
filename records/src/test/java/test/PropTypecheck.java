package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.generator.Ctor;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import test.gen.ExpressionValue;
import test.gen.GenDataType;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import test.gen.GenTypecheckFail;
import test.gen.GenUnit;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static test.TestUtil.distinctTypes;

/**
 * Created by neil on 09/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropTypecheck
{
    @Test
    public void testTypeComparison() throws InternalException, UserException
    {
        for (DataType a : distinctTypes)
        {
            for (DataType b : distinctTypes)
            {
                assertEqualIfSame(a, b);
            }
        }
    }

    @SuppressWarnings("intern")
    private void assertEqualIfSame(DataType a, DataType b) throws InternalException, UserException
    {
        // Equivalent to assertEquals(a == b, a.equals(b)) but gives better errors
        if (a == b)
        {
            assertEquals("Should be equal: " + a + " versus " + b, a, b);
        }
        else
        {
            assertNotEquals("Should not be equal: " + a + " versus " + b, a, b);
        }
    }

    @Property(trials = 1000, shrink = true, maxShrinks = 1000)
    @SuppressWarnings("nullness")
    public void propTypeCheckFail(@From(GenTypecheckFail.class) GenTypecheckFail.TypecheckInfo src) throws InternalException, UserException
    {
        for (Expression expression : src.expressionFailures)
        {
            AtomicBoolean errorReported = new AtomicBoolean(false);
            assertNull(src.getDisplay(expression), expression.check(src.recordSet, TestUtil.typeState(), (e, s, q) -> {errorReported.set(true);}));
            // If it was null, an error should also have been reported:
            assertTrue(src.getDisplay(expression) + "\n\n(failure from original: " + src.getDisplay(src.original) + ")", errorReported.get());
        }
    }

    @Property(trials = 1000)
    @SuppressWarnings("nullness")
    public void propTypeCheckSucceed(@From(GenExpressionValueBackwards.class) @From(GenExpressionValueForwards.class) ExpressionValue src) throws InternalException, UserException
    {
        StringBuilder b = new StringBuilder();
        @Nullable DataType checked = src.expression.check(src.recordSet, TestUtil.typeState(), (e, s, q) ->
        {
            b.append("Err in " + e + ": " + s);
        });
        assertEquals(src.expression.toString() + "\n" + b.toString() + "\nCol types: " + src.recordSet.getColumns().stream().map(c -> {try
    {
        return c.getType().toString();
    } catch (InternalException | UserException e) { throw new RuntimeException(e); }}).collect(Collectors.joining(", ")), src.type, DataType.checkSame(src.type, checked, s -> {}));
    }

    //#error Have property which generates tables/expressions of given types, and check they don't typecheck

    //#error Have property which generates result and tables/expressions to get result, and check equality

    @Property
    public void checkNumber(@From(GenUnit.class) Unit unitA, @From(GenUnit.class) Unit unitB) throws InternalException, UserException
    {
        assertEquals(new NumberInfo(unitA, 0), new NumberInfo(unitA, 0));
        assertEquals(new NumberInfo(unitB, 3), new NumberInfo(unitB, 3));

        DataType numberA = DataType.number(new NumberInfo(unitA, 0));
        DataType numberB = DataType.number(new NumberInfo(unitB, 1));
        DataTypeValue numberAV = DataTypeValue.number(new NumberInfo(unitA, 2), (i, prog) -> Utility.value(0));
        DataTypeValue numberBV = DataTypeValue.number(new NumberInfo(unitB, 2), (i, prog) -> Utility.value(0));

        boolean same = unitA.equals(unitB);

        checkSameRelations(numberA, numberB, numberAV, numberBV, same);
    }

    @Test
    public void checkBool() throws InternalException, UserException
    {
        checkSameRelations(DataType.BOOLEAN, DataType.BOOLEAN, DataTypeValue.bool((i, prog) -> Utility.value(true)), DataTypeValue.bool((i, prog) -> Utility.value(true)), true);
    }

    @Test
    public void checkText() throws InternalException, UserException
    {
        checkSameRelations(DataType.TEXT, DataType.TEXT, DataTypeValue.text((i, prog) -> Utility.value("")), DataTypeValue.text((i, prog) -> Utility.value("")), true);
    }

    @Property
    public void checkDate(@From(Ctor.class) DateTimeInfo dateTimeInfoA, @From(Ctor.class) DateTimeInfo dateTimeInfoB) throws InternalException, UserException
    {
        DataType dateA = DataType.date(dateTimeInfoA);
        DataType dateB = DataType.date(dateTimeInfoB);
        DataTypeValue dateAV = DataTypeValue.date(dateTimeInfoA, (i, prog) -> {throw new InternalException("");});
        DataTypeValue dateBV = DataTypeValue.date(dateTimeInfoB, (i, prog) -> {throw new InternalException("");});

        boolean same = dateTimeInfoA.sameType(dateTimeInfoB);

        checkSameRelations(dateA, dateB, dateAV, dateBV, same);
    }

    // Need at least two types for tuple, so they are explicit, plus list of more (which may be empty):
    @Property
    public void checkTuple(@From(GenDataType.class) DataType typeA, @From(GenDataType.class) DataType typeB, @From(DataTypeListGenerator.class)  List typeRest) throws InternalException, UserException
    {
        List<DataType> all = Stream.concat(Stream.<@NonNull DataType>of(typeA, typeB), ((List<DataType>)typeRest).stream()).collect(Collectors.<@NonNull DataType>toList());
        DataType type = DataType.tuple(all);
        DataTypeValue typeV = DataTypeValue.tupleV(Utility.mapListEx(all, t -> toValue(t)));
        List<DataType> allSwapped = Stream.concat(Stream.of(typeB, typeA), ((List<DataType>)typeRest).stream()).collect(Collectors.<@NonNull DataType>toList());
        DataType typeS = DataType.tuple(allSwapped);
        DataTypeValue typeSV = DataTypeValue.tupleV(Utility.mapListEx(allSwapped, t -> toValue(t)));
        // Swapped is same as unswapped only if typeA and typeB are same:
        checkSameRelations(type, typeS, typeV, typeSV, DataType.checkSame(typeA, typeB, s -> {}) != null);
    }

    @Property
    public void checkArray(@From(GenDataType.class) DataType innerA, @From(GenDataType.class) DataType innerB) throws InternalException, UserException
    {
        DataType typeA = DataType.array(innerA);
        DataType typeB = DataType.array(innerB);
        checkSameRelations(typeA, typeB, toValue(typeA), toValue(typeB), DataType.checkSame(innerA, innerB, s -> {}) != null);
    }

    @Property
    public void checkTagged(@From(GenDataType.GenTaggedType.class) DataType typeA, @From(GenDataType.GenTaggedType.class) DataType typeB) throws UserException, InternalException
    {
        // Is equals right here?
        checkSameRelations(typeA, typeB, toValue(typeA), toValue(typeB), typeA.equals(typeB));
    }

    @Property(trials = 2000)
    public void checkBlankArray(@From(GenDataType.class) DataType original, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // We make a list with the original type, and N duplicates of that type
        // with arrays randomly swapped to blank.  No matter what order you
        // put them in and feed them to checkAllSameType, you should get back
        // the original.

        ArrayList<DataType> types = new ArrayList<>();

        // Add permutations:
        int amount = r.nextInt(8);
        for (int i = 0; i < amount; i++)
        {
            types.add(blankArray(original, r));
        }

        // Now add original at random place:
        types.add(r.nextInt(types.size() + 1), original);
        assertEquals(original, DataType.checkAllSame(types, s -> {}));
    }

    private DataType blankArray(DataType original, Random r) throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        return original.apply(new DataTypeVisitor<DataType>()
        {
            @Override
            public DataType number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return DataType.number(displayInfo);
            }

            @Override
            public DataType text() throws InternalException, UserException
            {
                return DataType.TEXT;
            }

            @Override
            public DataType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return DataType.date(dateTimeInfo);
            }

            @Override
            public DataType bool() throws InternalException, UserException
            {
                return DataType.BOOLEAN;
            }

            @Override
            public DataType tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return typeManager.registerTaggedType(typeName.getRaw(), Utility.mapListEx(tags, tt -> new TagType<DataType>(tt.getName(), tt.getInner() == null  ? null : blankArray(tt.getInner(), r))));
            }

            @Override
            public DataType tuple(List<DataType> inner) throws InternalException, UserException
            {
                return DataType.tuple(Utility.mapListEx(inner, t -> blankArray(t, r)));
            }

            @Override
            public DataType array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null || r.nextInt(3) == 0)
                    return DataType.array();
                else
                    return DataType.array(inner);
            }
        });
    }

    private DataTypeValue toValue(@NonNull DataType t) throws UserException, InternalException
    {
        return t.apply(new DataTypeVisitor<DataTypeValue>()
        {
            @Override
            public DataTypeValue number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return DataTypeValue.number(displayInfo, (i, prog) -> {throw new InternalException("");});
            }

            @Override
            public DataTypeValue text() throws InternalException, UserException
            {
                return DataTypeValue.text((i, prog) -> {throw new InternalException("");});
            }

            @Override
            public DataTypeValue date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return DataTypeValue.date(dateTimeInfo, (i, prog) -> {throw new InternalException("");});
            }

            @Override
            public DataTypeValue bool() throws InternalException, UserException
            {
                return DataTypeValue.bool((i, prog) -> {throw new InternalException("");});
            }

            @Override
            public DataTypeValue tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return DataTypeValue.tagged(typeName, Utility.mapListEx(tags, tt -> new TagType<DataTypeValue>(tt.getName(), tt.getInner() == null ? null : toValue(tt.getInner()))), (i, prog) -> {throw new InternalException("");});
            }

            @Override
            public DataTypeValue tuple(List<DataType> inner) throws InternalException, UserException
            {
                return DataTypeValue.tupleV(Utility.mapListEx(inner, t -> toValue(t)));
            }

            @Override
            public DataTypeValue array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return DataTypeValue.arrayV();
                else
                    return DataTypeValue.arrayV(inner, (i, prog) -> {throw new InternalException("");});
            }
        });
    }


    /**
     * Checks relations between types are as expected
     * @param typeA The DataType version of A
     * @param typeB The DataType version of B
     * @param typeAV The DataTypeValue version of A (which be equivalent to A)
     * @param typeBV The DataTypeValue version of B (which be equivalent to B)
     * @param same Are A and B the same?
     */
    private void checkSameRelations(DataType typeA, DataType typeB, DataTypeValue typeAV, DataTypeValue typeBV, boolean same) throws UserException, InternalException
    {
        assertEquals(typeA, typeA);
        assertEquals(typeA, typeAV);
        assertEquals(typeAV, typeAV);

        assertEquals(typeB, typeB);
        assertEquals(typeB, typeBV);
        assertEquals(typeBV, typeBV);


        assertEquals(typeA, DataType.checkSame(typeA, typeA, s -> {}));
        assertEquals(typeA, DataType.checkSame(typeA, typeAV, s -> {}));
        assertEquals(typeA, DataType.checkSame(typeAV, typeA, s -> {}));
        assertEquals(typeA, DataType.checkSame(typeAV, typeAV, s -> {}));

        assertEquals(typeB, DataType.checkSame(typeB, typeB, s -> {}));
        assertEquals(typeB, DataType.checkSame(typeB, typeBV, s -> {}));
        assertEquals(typeB, DataType.checkSame(typeBV, typeB, s -> {}));
        assertEquals(typeB, DataType.checkSame(typeBV, typeBV, s -> {}));

        Object expected = same ? typeA : null;
        assertEquals(expected, DataType.checkSame(typeA, typeB, s -> {}));
        assertEquals(expected, DataType.checkSame(typeA, typeBV, s -> {}));
        assertEquals(expected, DataType.checkSame(typeAV, typeB, s -> {}));
        assertEquals(expected, DataType.checkSame(typeAV, typeBV, s -> {}));

        assertEquals(expected, DataType.checkSame(typeB, typeA, s -> {}));
        assertEquals(expected, DataType.checkSame(typeB, typeAV, s -> {}));
        assertEquals(expected, DataType.checkSame(typeBV, typeA, s -> {}));
        assertEquals(expected, DataType.checkSame(typeBV, typeAV, s -> {}));

        assertEquals(typeA, DataType.checkAllSame(Arrays.asList(typeA), s -> {}));
        assertEquals(typeB, DataType.checkAllSame(Arrays.asList(typeB), s -> {}));
        assertEquals(typeAV, DataType.checkAllSame(Arrays.asList(typeAV), s -> {}));
        assertEquals(typeBV, DataType.checkAllSame(Arrays.asList(typeBV), s -> {}));

        assertEquals(typeA, DataType.checkAllSame(Arrays.asList(typeA, typeAV), s -> {}));
        assertEquals(typeA, DataType.checkAllSame(Arrays.asList(typeAV, typeA), s -> {}));
        assertEquals(typeB, DataType.checkAllSame(Arrays.asList(typeB, typeBV), s -> {}));
        assertEquals(typeB, DataType.checkAllSame(Arrays.asList(typeBV, typeB), s -> {}));

        assertEquals(expected, DataType.checkAllSame(Arrays.asList(typeA, typeB), s -> {}));
        assertEquals(expected, DataType.checkAllSame(Arrays.asList(typeA, typeAV, typeB), s -> {}));
        assertEquals(expected, DataType.checkAllSame(Arrays.asList(typeA, typeAV, typeB, typeBV), s -> {}));
        assertEquals(expected, DataType.checkAllSame(Arrays.asList(typeB, typeBV, typeA, typeAV), s -> {}));
    }

    public static class DataTypeListGenerator extends Generator<List>
    {
        public DataTypeListGenerator()
        {
            super(List.class);
        }

        @Override
        public List generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            return TestUtil.makeList(sourceOfRandomness.nextInt(0, 10), new GenDataType(), sourceOfRandomness, generationStatus);
        }
    }
}
