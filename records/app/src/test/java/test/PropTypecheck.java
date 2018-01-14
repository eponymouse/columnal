package test;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
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
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.Expression;
import records.types.TypeExp;
import styled.StyledString;
import test.gen.ExpressionValue;
import test.gen.GenDataType;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import test.gen.GenTypecheckFail;
import test.gen.GenUnit;
import utility.Either;
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
            assertNull(src.getDisplay(expression), expression.check(src.recordSet, TestUtil.typeState(), new ErrorAndTypeRecorder()
            {
                @Override
                public <E> void recordError(E src, StyledString error, List<QuickFix<E>> fixes)
                {
                    errorReported.set(true);
                }

                @SuppressWarnings("recorded")
                @Override
                public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp)
                {
                    return typeExp;
                }
            }));
            // If it was null, an error should also have been reported:
            assertTrue(src.getDisplay(expression) + "\n\n(failure from original: " + src.getDisplay(src.original) + ")", errorReported.get());
        }
    }

    @Property(trials = 1000)
    // @SuppressWarnings("nullness")
    public void propTypeCheckSucceed(@From(GenExpressionValueBackwards.class) @From(GenExpressionValueForwards.class) ExpressionValue src) throws InternalException, UserException
    {
        ErrorAndTypeRecorderStorer storer = new ErrorAndTypeRecorderStorer();
        @Nullable TypeExp checked = src.expression.check(src.recordSet, TestUtil.typeState(), storer);
        TypeExp srcTypeExp = TypeExp.fromConcrete(null, src.type);
        assertEquals(src.expression.toString() + "\n" + storer.getAllErrors().map(StyledString::toPlain).collect(Collectors.joining("\n")) + "\nCol types: " + src.recordSet.getColumns().stream().map(c -> {
            try
            {
                return c.getType().toString();
            } catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.joining(", ")), Either.right(src.type), checked == null ? null : TypeExp.unifyTypes(srcTypeExp, checked).eitherEx(err -> Either.left(err), t -> t.toConcreteType(src.typeManager)));
    }

    //#error Have property which generates tables/expressions of given types, and check they don't typecheck

    //#error Have property which generates result and tables/expressions to get result, and check equality

    @Property
    public void checkNumber(@From(GenUnit.class) Unit unitA, @From(GenUnit.class) Unit unitB) throws InternalException, UserException
    {
        // TODO test with non-null NumberDisplayInfo
        assertEquals(new NumberInfo(unitA, null), new NumberInfo(unitA, null));
        assertEquals(new NumberInfo(unitB, null), new NumberInfo(unitB, null));

        DataType numberA = DataType.number(new NumberInfo(unitA, null));
        DataType numberB = DataType.number(new NumberInfo(unitB, null));
        DataTypeValue numberAV = DataTypeValue.number(new NumberInfo(unitA, null), (i, prog) -> DataTypeUtility.value(0));
        DataTypeValue numberBV = DataTypeValue.number(new NumberInfo(unitB, null), (i, prog) -> DataTypeUtility.value(0));

        boolean same = unitA.equals(unitB);

        checkSameRelations(DummyManager.INSTANCE.getTypeManager(), numberA, numberB, numberAV, numberBV, same);
    }

    @Test
    public void checkBool() throws InternalException, UserException
    {
        checkSameRelations(DummyManager.INSTANCE.getTypeManager(), DataType.BOOLEAN, DataType.BOOLEAN, DataTypeValue.bool((i, prog) -> DataTypeUtility.value(true)), DataTypeValue.bool((i, prog) -> DataTypeUtility.value(true)), true);
    }

    @Test
    public void checkText() throws InternalException, UserException
    {
        checkSameRelations(DummyManager.INSTANCE.getTypeManager(), DataType.TEXT, DataType.TEXT, DataTypeValue.text((i, prog) -> DataTypeUtility.value("")), DataTypeValue.text((i, prog) -> DataTypeUtility.value("")), true);
    }

    @Property
    public void checkDate(@From(Ctor.class) DateTimeInfo dateTimeInfoA, @From(Ctor.class) DateTimeInfo dateTimeInfoB) throws InternalException, UserException
    {
        DataType dateA = DataType.date(dateTimeInfoA);
        DataType dateB = DataType.date(dateTimeInfoB);
        DataTypeValue dateAV = DataTypeValue.date(dateTimeInfoA, (i, prog) -> {throw new InternalException("");});
        DataTypeValue dateBV = DataTypeValue.date(dateTimeInfoB, (i, prog) -> {throw new InternalException("");});

        boolean same = dateTimeInfoA.sameType(dateTimeInfoB);

        checkSameRelations(DummyManager.INSTANCE.getTypeManager(), dateA, dateB, dateAV, dateBV, same);
    }

    // Need at least two types for tuple, so they are explicit, plus list of more (which may be empty):
    @SuppressWarnings("unchecked")
    @Property
    public void checkTuple(@From(GenDataType.class) GenDataType.DataTypeAndManager typeA, @From(GenDataType.class) GenDataType.DataTypeAndManager typeB, @From(DataTypeListGenerator.class)  List typeRest) throws InternalException, UserException
    {
        List<DataType> all = Stream.<DataType>concat(Stream.<@NonNull DataType>of(typeA.dataType, typeB.dataType), ((List<DataType>)typeRest).stream()).collect(Collectors.<@NonNull DataType>toList());
        DataType type = DataType.tuple(all);
        DataTypeValue typeV = DataTypeValue.tupleV(Utility.mapListEx(all, t -> toValue(t)));
        List<DataType> allSwapped = Stream.<DataType>concat(Stream.of(typeB.dataType, typeA.dataType), ((List<DataType>)typeRest).stream()).collect(Collectors.<@NonNull DataType>toList());
        DataType typeS = DataType.tuple(allSwapped);
        DataTypeValue typeSV = DataTypeValue.tupleV(Utility.mapListEx(allSwapped, t -> toValue(t)));
        // Swapped is same as unswapped only if typeA and typeB are same:
        checkSameRelations(typeA.typeManager, type, typeS, typeV, typeSV, DataType.checkSame(typeA.dataType, typeB.dataType, s -> {}) != null);
    }

    @Property
    public void checkArray(@From(GenDataType.class) GenDataType.DataTypeAndManager innerA, @From(GenDataType.class) GenDataType.DataTypeAndManager innerB) throws InternalException, UserException
    {
        DataType typeA = DataType.array(innerA.dataType);
        DataType typeB = DataType.array(innerB.dataType);
        checkSameRelations(innerA.typeManager, typeA, typeB, toValue(typeA), toValue(typeB), DataType.checkSame(innerA.dataType, innerB.dataType, s -> {}) != null);
    }

    @Property
    public void checkTagged(@From(GenDataType.GenTaggedType.class) GenDataType.DataTypeAndManager typeA, @From(GenDataType.GenTaggedType.class) GenDataType.DataTypeAndManager typeB) throws UserException, InternalException
    {
        // Is equals right here?
        checkSameRelations(typeA.typeManager, typeA.dataType, typeB.dataType, toValue(typeA.dataType), toValue(typeB.dataType), typeA.dataType.equals(typeB.dataType));
    }

    @Property(trials = 2000)
    public void checkBlankArray(@From(GenDataType.class) GenDataType.DataTypeAndManager original, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // We make a list with the original type, and N duplicates of that type
        // with arrays randomly swapped to blank (and other types left unchanged).  No matter what order you
        // put them in and feed them to checkAllSameType, you should get back
        // the original, because we add a single unmodified copy back in.

        ArrayList<TypeExp> types = new ArrayList<>();

        // Add permutations:
        int amount = r.nextInt(8);
        for (int i = 0; i < amount; i++)
        {
            types.add(TypeExp.fromConcrete(null, blankArray(original.dataType, r)));
        }

        // Now add original at random place:
        types.add(r.nextInt(types.size() + 1), TypeExp.fromConcrete(null, original.dataType));
        assertEquals(Either.right(original.dataType), TypeExp.unifyTypes(types).eitherEx(err -> Either.left(err), t -> t.toConcreteType(original.typeManager)));
    }

    /**
     * Randomly blanks an array type (or not) within a complex type
     */
    private DataType blankArray(DataType original, Random r) throws UserException, InternalException
    {
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
            public DataType tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return original;
            }
            
            @Override
            public DataType tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return DataType.tuple(Utility.mapListEx(inner, t -> blankArray(t, r)));
            }

            @Override
            public DataType array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null || r.nextInt(3) == 0)
                    return DataType.array();
                else
                    return DataType.array(blankArray(inner, r));
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
            public DataTypeValue tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return DataTypeValue.tagged(typeName, typeVars, Utility.mapListExI(tags, tt -> new TagType<DataTypeValue>(tt.getName(), tt.getInner() == null ? null : toValue(tt.getInner()))), (i, prog) -> {throw new InternalException("");});
            }

            @Override
            public DataTypeValue tuple(ImmutableList<DataType> inner) throws InternalException, UserException
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
    private void checkSameRelations(TypeManager typeManager, DataType typeA, DataType typeB, DataTypeValue typeAV, DataTypeValue typeBV, boolean same) throws UserException, InternalException
    {
        assertEquals(typeA, typeA);
        assertEquals(typeA, typeAV);
        assertEquals(typeAV, typeAV);

        assertEquals(typeB, typeB);
        assertEquals(typeB, typeBV);
        assertEquals(typeBV, typeBV);
        
        assertEquals(Either.right(typeA), TypeExp.fromConcrete(null, typeA).toConcreteType(typeManager));
        assertEquals(Either.right(typeAV), TypeExp.fromConcrete(null, typeAV).toConcreteType(typeManager));
        assertEquals(Either.right(typeB), TypeExp.fromConcrete(null, typeB).toConcreteType(typeManager));
        assertEquals(Either.right(typeBV), TypeExp.fromConcrete(null, typeBV).toConcreteType(typeManager));
        

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

        assertEquals(typeA, unifyList(typeManager, typeA));
        assertEquals(typeB, unifyList(typeManager, typeB));
        assertEquals(typeAV, unifyList(typeManager, typeAV));
        assertEquals(typeBV, unifyList(typeManager, typeBV));

        assertEquals(typeA, unifyList(typeManager, typeA, typeAV));
        assertEquals(typeA, unifyList(typeManager, typeAV, typeA));
        assertEquals(typeB, unifyList(typeManager, typeB, typeBV));
        assertEquals(typeB, unifyList(typeManager, typeBV, typeB));

        assertEquals(expected, unifyList(typeManager, typeA, typeB));
        assertEquals(expected, unifyList(typeManager, typeA, typeAV, typeB));
        assertEquals(expected, unifyList(typeManager, typeA, typeAV, typeB, typeBV));
        assertEquals(expected, unifyList(typeManager, typeB, typeBV, typeA, typeAV));
    }

    private @Nullable DataType unifyList(TypeManager typeManager, DataType... types) throws InternalException, UserException
    {
        return TypeExp.unifyTypes(Utility.mapListInt(Arrays.asList(types), t -> TypeExp.fromConcrete(null, t))).<@Nullable DataType>eitherEx(e -> null, t -> t.toConcreteType(typeManager).<@Nullable DataType>either(e -> null, x -> x));
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
            return Utility.mapList(TestUtil.makeList(sourceOfRandomness.nextInt(0, 10), new GenDataType(), sourceOfRandomness, generationStatus), t -> t.dataType);
        }
    }
}
