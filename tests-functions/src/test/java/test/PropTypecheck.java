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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.Ctor;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.functions.TFunctionUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorder;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorderStorer;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.QuickFix;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import test.gen.GenTypecheckFail;
import test.gen.GenUnit;
import test.gen.type.GenDataTypeMaker;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 09/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class PropTypecheck
{
    @Test
    public void testTypeComparison() throws InternalException, UserException
    {
        List<DataType> types = TFunctionUtil.managerWithTestTypes().getSecond();
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
            assertNull(src.getDisplay(expression), expression.checkExpression(src, TFunctionUtil.createTypeState(src.typeManager), new ErrorAndTypeRecorder()
            {
                @Override
                public <E> void recordError(E src, StyledString error)
                {
                    errorReported.set(true);
                }

                @Override
                public <EXPRESSION extends StyledShowable> void recordInformation(EXPRESSION src, Pair<StyledString, @Nullable QuickFix<EXPRESSION>> information)
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
        @Nullable TypeExp checked = src.expression.checkExpression(src, TFunctionUtil.createTypeState(src.typeManager), storer);
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

    @SuppressWarnings("unchecked")
    @Property
    public void checkTuple(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker, @From(GenRandom.class) Random r) throws InternalException, UserException
    {
        List<DataType> types = TBasicUtil.makeList(new SourceOfRandomness(r), 1, 10, () -> typeMaker.makeType().getDataType());
        
        List<@ExpressionIdentifier String> namesA = Utility.<@ExpressionIdentifier String>replicateM(types.size(), () -> TBasicUtil.generateExpressionIdentifier(new SourceOfRandomness(r)));
        if (namesA.stream().distinct().count() != namesA.size())
            return;
        List<@ExpressionIdentifier String> namesB = r.nextInt(4) == 1 ? namesA : Utility.<@ExpressionIdentifier String>replicateM(types.size(), () -> TBasicUtil.generateExpressionIdentifier(new SourceOfRandomness(r)));
        if (namesB.stream().distinct().count() != namesB.size())
            return;
        
        DataType a = DataType.record(IntStream.range(0, types.size()).mapToObj(i -> i).collect(ImmutableMap.toImmutableMap(i -> namesA.get(i), i ->types.get(i))));
        DataType b = DataType.record(IntStream.range(0, types.size()).mapToObj(i -> i).collect(ImmutableMap.toImmutableMap(i -> namesB.get(i), i ->types.get(i))));
        
        // Same only if each name is the same:
        // (Miniscule chance that names will be permutations of each other in exact same way as types permute each other)
        checkSameRelations(typeMaker.getTypeManager(), a, b, namesA.equals(namesB));
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
