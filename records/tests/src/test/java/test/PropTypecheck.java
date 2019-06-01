package test;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.Ctor;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.LocationInfo;
import records.transformations.expression.QuickFix;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import test.gen.GenTypecheckFail;
import test.gen.GenUnit;
import test.gen.type.GenDataTypeMaker;
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

/**
 * Created by neil on 09/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropTypecheck
{
    @Test
    public void testTypeComparison() throws InternalException, UserException
    {
        List<DataType> types = TestUtil.managerWithTestTypes().getSecond();
        for (DataType a : types)
        {
            for (DataType b : types)
            {
                assertEqualIfSame(a, b);
            }
        }
    }
    
    // This won't test tagged types very well, but it should do okay for numbers, etc
    @Property(trials=10000)
    public void testTypeHashCodeAndEquals(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker) throws InternalException, UserException
    {
        DataType a = typeMaker.makeType().getDataType();
        DataType b = typeMaker.makeType().getDataType();
        assertTrue(a.equals(a));
        assertTrue(b.equals(b));
        assertEquals(a.hashCode(),a.hashCode());
        assertEquals(b.hashCode(),b.hashCode());
        String aSaved = a.save(new OutputBuilder()).toString();
        String bSaved = b.save(new OutputBuilder()).toString();
        assertEquals(aSaved + " =?= " + bSaved, a.equals(b), aSaved.equals(bSaved));
        if (aSaved.equals(bSaved))
            assertEquals(aSaved, a.hashCode(), b.hashCode());
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
            assertNull(src.getDisplay(expression), expression.check(src, TestUtil.createTypeState(src.typeManager), LocationInfo.UNIT_DEFAULT, new ErrorAndTypeRecorder()
            {
                @Override
                public <E> void recordError(E src, StyledString error)
                {
                    errorReported.set(true);
                }

                @Override
                public <EXPRESSION> void recordInformation(EXPRESSION src, StyledString informaton)
                {
                }

                @Override
                public <EXPRESSION extends StyledShowable> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION>> quickFixes)
                {
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
        @Nullable TypeExp checked = src.expression.checkExpression(src, TestUtil.createTypeState(src.typeManager), storer);
        TypeExp srcTypeExp = TypeExp.fromDataType(null, src.type);
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
        assertEquals(new NumberInfo(unitA), new NumberInfo(unitA));
        assertEquals(new NumberInfo(unitB), new NumberInfo(unitB));

        DataType numberA = DataType.number(new NumberInfo(unitA));
        DataType numberB = DataType.number(new NumberInfo(unitB));
        DataTypeValue numberAV = DataTypeValue.number(new NumberInfo(unitA), (i, prog) -> DataTypeUtility.value(0));
        DataTypeValue numberBV = DataTypeValue.number(new NumberInfo(unitB), (i, prog) -> DataTypeUtility.value(0));

        boolean same = unitA.equals(unitB);

        checkSameRelations(DummyManager.make().getTypeManager(), numberA, numberB, same);
    }

    @Test
    public void checkBool() throws InternalException, UserException
    {
        checkSameRelations(DummyManager.make().getTypeManager(), DataType.BOOLEAN, DataType.BOOLEAN, true);
    }

    @Test
    public void checkText() throws InternalException, UserException
    {
        checkSameRelations(DummyManager.make().getTypeManager(), DataType.TEXT, DataType.TEXT, true);
    }

    @Property
    public void checkDate(@From(Ctor.class) DateTimeInfo dateTimeInfoA, @From(Ctor.class) DateTimeInfo dateTimeInfoB) throws InternalException, UserException
    {
        DataType dateA = DataType.date(dateTimeInfoA);
        DataType dateB = DataType.date(dateTimeInfoB);
        DataTypeValue dateAV = DataTypeValue.date(dateTimeInfoA, (i, prog) -> {throw new InternalException("");});
        DataTypeValue dateBV = DataTypeValue.date(dateTimeInfoB, (i, prog) -> {throw new InternalException("");});

        boolean same = dateTimeInfoA.sameType(dateTimeInfoB);

        checkSameRelations(DummyManager.make().getTypeManager(), dateA, dateB, same);
    }

    // Need at least two types for tuple, so they are explicit, plus list of more (which may be empty):
    @SuppressWarnings("unchecked")
    @Property
    public void checkTuple(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker, @From(GenRandom.class) Random r) throws InternalException, UserException
    {
        DataType typeA = typeMaker.makeType().getDataType();
        DataType typeB = typeMaker.makeType().getDataType();

        List<DataType> typeRest = TestUtil.makeList(new SourceOfRandomness(r), 0, 10, () -> typeMaker.makeType().getDataType());
        
        List<DataType> all = Stream.<DataType>concat(Stream.<@NonNull DataType>of(typeA, typeB), ((List<DataType>)typeRest).stream()).collect(Collectors.<@NonNull DataType>toList());
        DataType type = DataType.tuple(all);;
        List<DataType> allSwapped = Stream.<DataType>concat(Stream.of(typeB, typeA), ((List<DataType>)typeRest).stream()).collect(Collectors.<@NonNull DataType>toList());
        DataType typeS = DataType.tuple(allSwapped);
        // Swapped is same as unswapped only if typeA and typeB are same:
        checkSameRelations(typeMaker.getTypeManager(), type, typeS, typeA.equals(typeB));
    }

    @Property
    public void checkArray(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker) throws InternalException, UserException
    {
        DataType innerA = typeMaker.makeType().getDataType();
        DataType innerB = typeMaker.makeType().getDataType();
        DataType typeA = DataType.array(innerA);
        DataType typeB = DataType.array(innerB);
        checkSameRelations(typeMaker.getTypeManager(), typeA, typeB, innerA.equals(innerB));
    }

    @Property
    public void checkTagged(@From(GenDataTypeMaker.GenTaggedType.class) GenDataTypeMaker.DataTypeMaker typeMaker) throws UserException, InternalException
    {
        DataType typeA = typeMaker.makeType().getDataType();
        DataType typeB = typeMaker.makeType().getDataType();
        // Is equals right here?
        checkSameRelations(typeMaker.getTypeManager(), typeA, typeB, typeA.equals(typeB));
    }

    @Property(trials = 2000)
    public void checkBlankArray(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        DataType original = typeMaker.makeType().getDataType();
        
        // We make a list with the original type, and N duplicates of that type
        // with arrays randomly swapped to blank (and other types left unchanged).  No matter what order you
        // put them in and feed them to checkAllSameType, you should get back
        // the original, because we add a single unmodified copy back in.

        ArrayList<TypeExp> types = new ArrayList<>();

        // Add permutations:
        int amount = r.nextInt(8);
        for (int i = 0; i < amount; i++)
        {
            types.add(TypeExp.fromDataType(null, blankArray(original, r)));
        }

        // Now add original at random place:
        types.add(r.nextInt(types.size() + 1), TypeExp.fromDataType(null, original));
        assertEquals(Either.right(original), TypeExp.unifyTypes(types).eitherEx(err -> Either.left(err), t -> t.toConcreteType(typeMaker.getTypeManager())));
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
            public DataType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return original;
            }
            
            @Override
            public DataType tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return DataType.tuple(Utility.mapListEx(inner, t -> blankArray(t, r)));
            }

            @Override
            public DataType array(DataType inner) throws InternalException, UserException
            {
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
            public DataTypeValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return DataTypeValue.tagged(typeName, typeVars, tags, (i, prog) -> {throw new InternalException("");});
            }

            @Override
            public DataTypeValue tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return DataTypeValue.tuple(inner, (i, prog) -> {throw new InternalException("");});
            }

            @Override
            public DataTypeValue array(DataType inner) throws InternalException, UserException
            {
                return DataTypeValue.array(inner, (i, prog) -> {throw new InternalException("");});
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
    private void checkSameRelations(TypeManager typeManager, DataType typeA, DataType typeB, boolean same) throws UserException, InternalException
    {
        assertEquals(typeA, typeA);

        assertEquals(typeB, typeB);

        assertEquals(Either.right(typeA), TypeExp.fromDataType(null, typeA).toConcreteType(typeManager));
        assertEquals(Either.right(typeB), TypeExp.fromDataType(null, typeB).toConcreteType(typeManager));
        
        Object expected = same ? typeA : null;

        assertEquals(typeA, unifyList(typeManager, typeA));
        assertEquals(typeB, unifyList(typeManager, typeB));

        assertEquals(typeA, unifyList(typeManager, typeA, typeA));
        assertEquals(typeB, unifyList(typeManager, typeB, typeB));

        assertEquals(expected, unifyList(typeManager, typeA, typeB));
    }

    private @Nullable DataType unifyList(TypeManager typeManager, DataType... types) throws InternalException, UserException
    {
        return TypeExp.unifyTypes(Utility.mapListInt(Arrays.asList(types), t -> TypeExp.fromDataType(null, t))).<@Nullable DataType>eitherEx(e -> null, t -> t.toConcreteType(typeManager).<@Nullable DataType>either(e -> null, x -> x));
    }
}
