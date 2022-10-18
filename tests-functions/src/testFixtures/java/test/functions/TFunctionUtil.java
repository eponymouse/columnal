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

package test.functions;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.identifier.qual.UnitIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.SpecificDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitDeclaration;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.jellytype.JellyType.JellyTypeVisitorEx;
import xyz.columnal.jellytype.JellyTypeRecord.Field;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorder;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.ExpressionUtil;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.QuickFix;
import xyz.columnal.transformations.expression.StringLiteral;
import xyz.columnal.transformations.expression.TypeLiteralExpression;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeCons;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.adt.ComparableEither;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.junit.Assert.assertNotNull;

public class TFunctionUtil
{
    public static final InitialLoadDetails ILD = new InitialLoadDetails(null, null, null, null);

    @OnThread(Tag.Simulation)
    public static @Nullable Pair<ValueFunction, DataType> typeCheckFunction(FunctionDefinition function, ImmutableList<DataType> paramTypes) throws InternalException, UserException
    {
        return typeCheckFunction(function, paramTypes, null);
    }

    // Returns the function and the return type of the function
    @OnThread(Tag.Simulation)
    public static @Nullable Pair<ValueFunction,DataType> typeCheckFunction(FunctionDefinition function, ImmutableList<DataType> paramTypes, @Nullable TypeManager overrideTypeManager) throws InternalException, UserException
    {
        ErrorAndTypeRecorder onError = TFunctionUtil.excOnError();
        TypeManager typeManager = overrideTypeManager != null ? overrideTypeManager : TFunctionUtil.managerWithTestTypes().getFirst().getTypeManager();
        Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> functionType = function.getType(typeManager);
        MutVar returnTypeVar = new MutVar(null);
        @SuppressWarnings("nullness") // For null src
        TypeExp paramTypeExp = onError.recordError(null, TypeExp.unifyTypes(TypeCons.function(null, Utility.mapListInt(paramTypes, p -> TypeExp.fromDataType(null, p)), returnTypeVar), functionType.getFirst()));
        if (paramTypeExp == null)
            return null;
        
        @SuppressWarnings("nullness") // For null src
        @Nullable DataType returnType = onError.recordLeftError(typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()), null, returnTypeVar.toConcreteType(typeManager));
        if (returnType != null)
            return new Pair<>(function.getInstance(typeManager, s -> TFunctionUtil.getConcrete(s, functionType.getSecond(), typeManager)), returnType);
        return null;
    }

    @OnThread(Tag.Simulation)
    public static @Nullable Pair<ValueFunction,DataType> typeCheckFunction(FunctionDefinition function, DataType expectedReturnType, ImmutableList<DataType> paramTypes, @Nullable TypeManager overrideTypeManager) throws InternalException, UserException
    {
        ErrorAndTypeRecorder onError = TFunctionUtil.excOnError();
        TypeManager typeManager = overrideTypeManager != null ? overrideTypeManager : DummyManager.make().getTypeManager();
        Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> functionType = function.getType(typeManager);
        MutVar returnTypeVar = new MutVar(null);
        @SuppressWarnings("nullness") // For null src
        @Nullable TypeExp unifiedReturn = onError.recordError(null, TypeExp.unifyTypes(returnTypeVar, TypeExp.fromDataType(null, expectedReturnType)));
        if (null == unifiedReturn)
            return null;
        @SuppressWarnings("nullness") // For null src
        TypeExp funcTypeExp = onError.recordError(null, TypeExp.unifyTypes(TypeCons.function(null, Utility.mapListInt(paramTypes, p -> TypeExp.fromDataType(null, p)), returnTypeVar), functionType.getFirst()));
        if (funcTypeExp == null)
            return null;
            
        @SuppressWarnings("nullness") // For null src
        @Nullable DataType returnType = onError.recordLeftError(typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()), null, returnTypeVar.toConcreteType(typeManager));
        if (returnType != null)
            return new Pair<>(function.getInstance(typeManager, s -> TFunctionUtil.getConcrete(s, functionType.getSecond(), typeManager)), returnType);
        return null;
    }

    private static Either<Unit, DataType> getConcrete(String s, Map<String, Either<MutUnitVar, MutVar>> vars, TypeManager typeManager) throws InternalException, UserException
    {
        Either<MutUnitVar, MutVar> var = vars.get(s);
        if (var == null)
            throw new InternalException("Var " + s + " not found");
        return var.mapBothEx(
            u -> {
                @Nullable Unit concrete = u.toConcreteUnit();
                if (concrete == null)
                    throw new InternalException("Could not concrete unit: " + u);
                return concrete;
            },
            v -> v.toConcreteType(typeManager).getRight(""));
    }

    public static ErrorAndTypeRecorder excOnError()
    {
        return new ErrorAndTypeRecorder()
        {
            @Override
            public <E> void recordError(E src, StyledString error)
            {
                throw new RuntimeException(error.toPlain());
            }

            @Override
            public <EXPRESSION extends StyledShowable> void recordInformation(EXPRESSION src, Pair<StyledString, @Nullable QuickFix<EXPRESSION>> informaton)
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
        };
    }

    public static Pair<DummyManager, List<DataType>> managerWithTestTypes()
    {
        try
        {
            DummyManager dummyManager = new DummyManager();
            
            dummyManager.getUnitManager().addUserUnit(new Pair<>("myUnit", Either.<@UnitIdentifier String, UnitDeclaration>right(new UnitDeclaration(new SingleUnit("myUnit", "Custom unit for testing", "", ""), null, "New Category"))));
            dummyManager.getUnitManager().addUserUnit(new Pair<>("myAlias", Either.<@UnitIdentifier String, UnitDeclaration>left("myUnit")));
            dummyManager.getUnitManager().addUserUnit(new Pair<>("hogshead", Either.<@UnitIdentifier String, UnitDeclaration>right(new UnitDeclaration(new SingleUnit("hogshead", "An English wine cask hogshead", "", ""), new Pair<>(Rational.ofLongs(2387, 10), dummyManager.getUnitManager().loadUse("l")), "Volume"))));
            
            // TODO add more higher-order types
            TypeManager typeManager = dummyManager.getTypeManager();
            @SuppressWarnings("nullness")
            DataType a = typeManager.registerTaggedType("A", ImmutableList.of(), ImmutableList.of(new TagType<JellyType>("Single", null))).instantiate(ImmutableList.of(), typeManager);
            @SuppressWarnings("nullness")
            DataType c = typeManager.registerTaggedType("C", ImmutableList.of(), ImmutableList.of(new TagType<JellyType>("Blank", null), new TagType<JellyType>("Num", JellyType.fromConcrete(DataType.NUMBER)))).instantiate(ImmutableList.of(), typeManager);
            @SuppressWarnings("nullness")
            DataType b = typeManager.registerTaggedType("B", ImmutableList.of(), ImmutableList.of(new TagType<JellyType>("Single", null))).instantiate(ImmutableList.of(), typeManager);
            @SuppressWarnings({"nullness", "identifier"})
            DataType nested = typeManager.registerTaggedType("Nested", ImmutableList.of(), ImmutableList.of(new TagType<JellyType>("A", JellyType.tagged(new TypeId("A"), ImmutableList.of())), new TagType<JellyType>("C", JellyType.tagged(new TypeId("C"), ImmutableList.of())))).instantiate(ImmutableList.of(), typeManager);
            DataType maybeMaybe = typeManager.getMaybeType().instantiate(ImmutableList.of(Either.right(
                typeManager.getMaybeType().instantiate(ImmutableList.of(Either.right(DataType.TEXT)), typeManager)
            )), typeManager);
            
            @SuppressWarnings({"nullness", "identifier"})
            DataType eitherUnits = typeManager.registerTaggedType("EitherNumUnit",
                    ImmutableList.of(new Pair<>(TypeVariableKind.UNIT, "a"), new Pair<>(TypeVariableKind.UNIT, "b")),
                    ImmutableList.of(new TagType<>("Left", JellyType.number(JellyUnit.unitVariable("a"))),
                            new TagType<>("Right", JellyType.number(JellyUnit.unitVariable("b"))))
            ).instantiate(ImmutableList.of(Either.left(Unit.SCALAR), Either.left(typeManager.getUnitManager().loadUse("m"))), typeManager);

            @SuppressWarnings("nullness")
            DataType eitherUnits2 = typeManager.registerTaggedType("EitherMyOrHogshead",
                ImmutableList.of(),
                ImmutableList.of(new TagType<>("Left", JellyType.number(JellyUnit.fromConcrete(typeManager.getUnitManager().loadUse("myUnit")))),
                    new TagType<>("Right", JellyType.number(JellyUnit.fromConcrete(typeManager.getUnitManager().loadUse("hogshead")))))
            ).instantiate(ImmutableList.of(), typeManager);
            
            return new Pair<>(dummyManager, Arrays.<DataType>asList(
                DataType.BOOLEAN,
                DataType.TEXT,
                DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
                DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)),
                DataType.date(new DateTimeInfo(DateTimeType.DATETIME)),
                DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)),
                DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)),
                //DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)),
                DataType.NUMBER,
                DataType.number(new NumberInfo(typeManager.getUnitManager().loadUse("GBP"))),
                DataType.number(new NumberInfo(typeManager.getUnitManager().loadUse("m"))),
                DataType.number(new NumberInfo(typeManager.getUnitManager().loadUse("m^2"))),
                DataType.number(new NumberInfo(typeManager.getUnitManager().loadUse("m^3/s^3"))),
                DataType.number(new NumberInfo(typeManager.getUnitManager().loadUse("cm"))),
                DataType.number(new NumberInfo(typeManager.getUnitManager().loadUse("(USD*m)/s^2"))),
                a,
                b,
                c,
                nested,
                maybeMaybe,
                eitherUnits,
                eitherUnits2,
                DataType.record(ImmutableMap.of("a", DataType.NUMBER, "b", DataType.NUMBER)),
                DataType.record(ImmutableMap.of("bool", DataType.BOOLEAN, "Text", DataType.TEXT, "dtz", DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), "c", c)),
                DataType.record(ImmutableMap.of("z", DataType.NUMBER, "inner", DataType.record(ImmutableMap.of("t 1", DataType.TEXT, "t 2", DataType.NUMBER)))),
                DataType.array(DataType.TEXT),
                DataType.array(DataType.NUMBER),
                DataType.array(DataType.record(ImmutableMap.of("a", DataType.NUMBER, "nested", DataType.record(ImmutableMap.of("the text 0 item", DataType.TEXT, "num num", DataType.NUMBER))))),
                DataType.array(DataType.array(DataType.record(ImmutableMap.of("key", DataType.NUMBER, "value", DataType.record(ImmutableMap.of("a", DataType.TEXT, "c", DataType.NUMBER))))))
            ));
        }
        catch (UserException | InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Simulation)
    public static @Value Object runExpression(String expressionSrc) throws UserException, InternalException
    {
        DummyManager mgr = managerWithTestTypes().getFirst();
        Expression expression = parseExpression(expressionSrc, mgr.getTypeManager(), FunctionList.getFunctionLookup(mgr.getUnitManager()));
        assertNotNull(expression.checkExpression(dummyColumnLookup(), createTypeState(mgr.getTypeManager()), excOnError()));
        return expression.calculateValue(new EvaluateState(mgr.getTypeManager(), OptionalInt.empty())).value;
    }

    public static Expression parseExpression(String expressionSrc, TypeManager typeManager, FunctionLookup functionLookup) throws InternalException, UserException
    {
        return ExpressionUtil.parse(null, expressionSrc, ExpressionVersion.latest(), typeManager, functionLookup);
    }

    public static Unit getUnit(DataType numberType) throws InternalException
    {
        return numberType.apply(new SpecificDataTypeVisitor<Unit>() {
            @Override
            public Unit number(NumberInfo displayInfo) throws InternalException
            {
                return displayInfo.getUnit();
            }
        });
    }

    public static ColumnLookup dummyColumnLookup()
    {
        return new ColumnLookup()
        {
            @Override
            public @Nullable FoundColumn getColumn(Expression expression, @Nullable TableId tableId, ColumnId columnId)
            {
                return null;
            }

            @Override
            public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
            {
                return null;
            }

            @Override
            public Stream<Pair<@Nullable TableId, ColumnId>> getAvailableColumnReferences()
            {
                return Stream.empty();
            }

            @Override
            public Stream<TableId> getAvailableTableReferences()
            {
                return Stream.empty();
            }

            @Override
            public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
            {
                return Stream.empty();
            }
        };
    }

    public static TypeState createTypeState(TypeManager typeManager) throws InternalException
    {
        return TypeState.withRowNumber(typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
    }

    // Used for testing
    // Creates a call to a tag constructor
    @SuppressWarnings("recorded")
    public static Expression tagged(UnitManager unitManager, TagInfo constructor, @Nullable Expression arg, DataType destType, boolean canAddAsType) throws InternalException
    {
        IdentExpression constructorExpression = IdentExpression.tag(constructor.getTypeName().getRaw(), constructor.getTagInfo().getName());
        Expression r;
        if (arg == null)
        {
            r = constructorExpression;
        }
        else
        {
            r = new CallExpression(constructorExpression, ImmutableList.of(arg));
        }
        
        if (!canAddAsType)
            return r;
        
        // Need to avoid having an ambiguous type:
        TaggedTypeDefinition wholeType = constructor.wholeType;
        for (Pair<TypeVariableKind, String> var : wholeType.getTypeArguments())
        {
            // If any type variables aren't mentioned, wrap in asType:
            if (!TFunctionUtil.containsTypeVar(wholeType.getTags().get(constructor.tagIndex).getInner(), var))
            {
                FunctionDefinition asType = FunctionList.lookup(unitManager, "as type");
                if (asType == null)
                    throw new RuntimeException("Could not find as type");
                return new CallExpression(IdentExpression.function(asType.getFullName()),ImmutableList.of(new TypeLiteralExpression(TypeExpression.fromDataType(destType)), r));
            }
        }
        return r;
    }

    public static StringLiteral makeStringLiteral(String target, SourceOfRandomness r)
    {
        StringBuilder b = new StringBuilder();
        
        target.codePoints().forEach(n -> {
            if (r.nextInt(8) == 1)
                b.append("^{" + Integer.toHexString(n) + "}");
            else
                b.append(GrammarUtility.escapeChars(Utility.codePointToString(n)));
        });
        return new StringLiteral(b.toString());
    }

    public static boolean containsTypeVar(JellyUnit unit, Pair<TypeVariableKind, String> var)
    {
        if (var.getFirst() == TypeVariableKind.UNIT)
            return unit.getDetails().containsKey(ComparableEither.left(var.getSecond()));
        return false;
    }

    private static boolean containsTypeVar(@Nullable JellyType jellyType, Pair<TypeVariableKind, String> var)
    {
        if (jellyType == null)
            return false;

        try
        {
            return jellyType.apply(new JellyTypeVisitorEx<Boolean, InternalException>()
            {
                @Override
                public Boolean number(JellyUnit unit) throws InternalException
                {
                    return containsTypeVar(unit, var);
                }
    
                @Override
                public Boolean text() throws InternalException
                {
                    return false;
                }
    
                @Override
                public Boolean date(DateTimeInfo dateTimeInfo) throws InternalException
                {
                    return false;
                }
    
                @Override
                public Boolean bool() throws InternalException
                {
                    return false;
                }
    
                @Override
                public Boolean applyTagged(TypeId typeName, ImmutableList<Either<JellyUnit, JellyType>> typeParams) throws InternalException
                {
                    return typeParams.stream().anyMatch(p -> p.<Boolean>either(u -> containsTypeVar(u, var), t -> containsTypeVar(t, var)));
                }

                @Override
                public Boolean record(ImmutableMap<@ExpressionIdentifier String, Field> fields, boolean complete) throws InternalException, InternalException
                {
                    return fields.values().stream().anyMatch(t -> containsTypeVar(t.getJellyType(), var));
                }
    
                @Override
                public Boolean array(JellyType inner) throws InternalException
                {
                    return containsTypeVar(inner, var);
                }
    
                @Override
                public Boolean function(ImmutableList<JellyType> argTypes, JellyType resultType) throws InternalException
                {
                    return argTypes.stream().anyMatch(a -> containsTypeVar(a, var)) || containsTypeVar(resultType, var);
                }
    
                @Override
                public Boolean ident(String name) throws InternalException
                {
                    return var.equals(new Pair<>(TypeVariableKind.TYPE, name));
                }
            });
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Makes something which could be an unfinished expression.  Can't have operators, can't start with a number.
    public static String makeUnfinished(SourceOfRandomness r)
    {
        StringBuilder s = new StringBuilder();
        s.append(r.nextChar('a', 'z'));
        int len = r.nextInt(0, 10);
        for (int i = 0; i < len; i++)
        {
            s.append(r.nextBoolean() ? r.nextChar('a', 'z') : r.nextChar('0', '9'));
        }
        return s.toString();
    }

    @SuppressWarnings("nullness")
    public static TypeState typeState()
    {
        try
        {
            UnitManager unitManager = new UnitManager();
            TypeManager typeManager = new TypeManager(unitManager);
            /*
            List<DataType> taggedTypes = distinctTypes.stream().filter(p -> p.isTagged()).collect(Collectors.toList());
            for (DataType t : taggedTypes)
            {
                typeManager.registerTaggedType(t.getTaggedTypeName().getRaw(), ImmutableList.of(), Utility.mapListInt(t.getTagTypes(), t2 -> t2.mapInt(JellyType::fromConcrete)));
            }
            */
            return createTypeState(typeManager);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
