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

package xyz.columnal.jellytype;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.grammar.FormatParser;
import xyz.columnal.grammar.FormatParser.BracketedTypeContext;
import xyz.columnal.grammar.FormatParser.NumberContext;
import xyz.columnal.grammar.FormatParser.TagRefParamContext;
import xyz.columnal.grammar.FormatParser.TypeContext;
import xyz.columnal.grammar.FormatParser.UnbracketedTypeContext;
import xyz.columnal.jellytype.JellyTypeRecord.Field;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Utility;

import java.util.HashMap;
import java.util.function.Consumer;

/**
 * There are three different representations of types:
 *   - DataType, which is a concrete type.  No type variables, no ambiguity, everything is a specific type.
 *     Immutable.  Can always be converted into TypeExp.  There's not a need to convert it to JellyType.
 *     
 *   - TypeExp, which is used during type checking.  No named type variables, though there are mutable
 *     type variables which can be unified during the process.  Not immutable, and regenerated fresh
 *     every time we need to do a round of type checking.  Once type-checking is complete, it can be
 *     converted into a DataType (with an error if there remains problematic ambiguity).  There's no
 *     need to turn it into a JellyType.
 *     
 *   - JellyType, named for being not a concrete type.  Mirrors DataType in structure, but can contain
 *     named type variables and unit variables.  Can produce a DataType given substitution for the named
 *     variables, ditto for TypeExp.
 */
public abstract class JellyType
{
    // package-visible constructor
    JellyType()
    {
    }

    public static JellyType text()
    {
        return JellyTypePrimitive.text();
    }

    public static JellyType number(JellyUnit unit)
    {
        return new JellyTypeNumberWithUnit(unit);
    }

    public abstract TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException;

    public abstract DataType makeDataType(@Recorded JellyType this, ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException;
    
    //public static class TypeQuickFix {}
    
    public static class UnknownTypeException extends UserException
    {
        private final ImmutableList<JellyType> suggestedFixes;
        private final @Recorded JellyType replacementTarget;

        public UnknownTypeException(String message, @Recorded JellyType replacementTarget, ImmutableList<JellyType> suggestedFixes)
        {
            super(message);
            this.replacementTarget = replacementTarget;
            this.suggestedFixes = suggestedFixes;
        }

        public ImmutableList<JellyType> getSuggestedFixes()
        {
            return suggestedFixes;
        }

        public @Recorded JellyType getReplacementTarget()
        {
            return replacementTarget;
        }
    }

    public abstract void save(OutputBuilder output);

    public abstract boolean equals(@Nullable Object o);

    public abstract int hashCode();

    // For every tagged type use anywhere within this type, call back the given function with the name.
    public abstract void forNestedTagged(Consumer<TypeId> nestedTagged);
    
    public static JellyType typeVariable(@ExpressionIdentifier String name)
    {
        return new JellyTypeIdent(name);
    }
    
    public static JellyType record(ImmutableMap<@ExpressionIdentifier String, Field> members)
    {
        return new JellyTypeRecord(members, true);
    }

    public static JellyType tagged(TypeId name, ImmutableList<Either<JellyUnit, @Recorded JellyType>> params)
    {
        if (params.isEmpty())
            return new JellyTypeIdent(name.getRaw());
        else
            return new JellyTypeApply(name, params);
    }
    
    public static JellyType list(@Recorded JellyType inner)
    {
        return new JellyTypeArray(inner);
    }

    @SuppressWarnings("recorded") // Won't actually be used in editor
    public static JellyType load(TypeContext typeContext, TypeManager mgr) throws InternalException, UserException
    {
        if (typeContext.unbracketedType() != null)
            return load(typeContext.unbracketedType(), mgr);
        else if (typeContext.bracketedType() != null)
            return load(typeContext.bracketedType(), mgr);
        else
            throw new InternalException("Unrecognised case: " + typeContext);
    }

    @SuppressWarnings("recorded") // Won't actually be used in editor
    public static JellyType load(BracketedTypeContext ctx, TypeManager mgr) throws InternalException, UserException
    {
        if (ctx.type() != null)
            return load(ctx.type(), mgr);
        else if (ctx.record() != null)
        {
            HashMap<@ExpressionIdentifier String, Field> fields = new HashMap<>();
            for (int i = 0; i < ctx.record().columnName().size(); i++)
            {
                @ExpressionIdentifier String fieldName = IdentifierUtility.fromParsed(ctx.record().columnName(i));
                JellyType type = load(ctx.record().type(i), mgr);
                if (fields.put(fieldName, new Field(type, true)) != null)
                    throw new UserException("Duplicated field: \"" + fieldName + "\"");
            }
            
            return new JellyTypeRecord(ImmutableMap.copyOf(fields), ctx.record().RECORD_MORE() == null);
        }
        throw new InternalException("Unrecognised case: " + ctx);
    }

    @SuppressWarnings("recorded") // Won't actually be used in editor
    private static JellyType load(UnbracketedTypeContext ctx, TypeManager mgr) throws InternalException, UserException
    {
        if (ctx.BOOLEAN() != null)
            return JellyTypePrimitive.bool();
        else if (ctx.number() != null)
            return load(ctx.number(), mgr);
        else if (ctx.TEXT() != null)
            return JellyTypePrimitive.text();
        else if (ctx.date() != null)
        {
            if (ctx.date().DATETIME() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.DATETIME));
            else if (ctx.date().YEARMONTHDAY() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY));
            else if (ctx.date().TIMEOFDAY() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.TIMEOFDAY));
            if (ctx.date().YEARMONTH() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.YEARMONTH));
            if (ctx.date().DATETIMEZONED() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.DATETIMEZONED));
        }
        else if (ctx.applyRef() != null)
        {
            TypeId typeId = new TypeId(IdentifierUtility.fromParsed(ctx.applyRef().ident()));
            return new JellyTypeApply(typeId, Utility.mapListExI(ctx.applyRef().tagRefParam(), param -> load(param, mgr)));
        }
        else if (ctx.array() != null)
        {
            return new JellyTypeArray(load(ctx.array().type(), mgr));
        }
        else if (ctx.ident() != null)
        {
            // TODO is it right that @typevar comes to same place as plain ident?
            @ExpressionIdentifier String name = IdentifierUtility.fromParsed(ctx.ident());
            return new JellyTypeIdent(name);
        }
        else if (ctx.functionType() != null)
            return new JellyTypeFunction(Utility.mapListExI(ctx.functionType().functionArgs().type(), t -> load(t, mgr)), load(ctx.functionType().type(), mgr));

        throw new InternalException("Unrecognised case: " + ctx.getText());
    }

    @SuppressWarnings("recorded") // Won't actually be used in editor
    private static Either<JellyUnit, JellyType> load(TagRefParamContext param, TypeManager mgr) throws InternalException, UserException
    {
        if (param.bracketedType() != null)
            return Either.right(load(param.bracketedType(), mgr));
        else if (param.UNIT() != null)
            // Strip curly brackets:
            return Either.left(JellyUnit.load(param.UNIT().getText().substring(1, param.UNIT().getText().length() - 1), mgr.getUnitManager()));
        else if (param.UNITVAR() != null)
            return Either.left(JellyUnit.unitVariable(param.ident().getText()));
        throw new InternalException("Unrecognised case: " + param);
    }

    private static JellyType load(NumberContext number, TypeManager mgr) throws InternalException, UserException
    {
        if (number.UNIT() == null)
            return new JellyTypeNumberWithUnit(JellyUnit.fromConcrete(Unit.SCALAR));
        else
        {
            String withCurly = number.UNIT().getText();
            return new JellyTypeNumberWithUnit(JellyUnit.load(withCurly.substring(1, withCurly.length() - 1), mgr.getUnitManager()));
        }
    }

    public static JellyType parse(String functionType, TypeManager mgr) throws UserException, InternalException
    {
        return load(Utility.<TypeContext, FormatParser>parseAsOne(functionType, FormatLexer::new, FormatParser::new, p -> p.completeType().type()), mgr);
    }

    public static interface JellyTypeVisitorEx<R, E extends Throwable>
    {
        R number(JellyUnit unit) throws InternalException, E;
        R text() throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo) throws InternalException, E;
        R bool() throws InternalException, E;

        R applyTagged(TypeId typeName, ImmutableList<Either<JellyUnit, @Recorded JellyType>> typeParams) throws InternalException, E;
        R record(ImmutableMap<@ExpressionIdentifier String, JellyTypeRecord.Field> fields, boolean complete) throws InternalException, E;
        // If null, array is empty and thus of unknown type
        R array(@Recorded JellyType inner) throws InternalException, E;

        R function(ImmutableList<@Recorded JellyType> argTypes, JellyType resultType) throws InternalException, E;
        
        R ident(String name) throws InternalException, E;
    }

    public static interface JellyTypeVisitor<R> extends JellyTypeVisitorEx<R, UserException>
    {

    }

    public abstract <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E;


    @SuppressWarnings("recorded") // Won't actually be used in editor
    public static JellyType fromConcrete(DataType t) throws InternalException
    {
        return t.apply(new DataTypeVisitorEx<JellyType, InternalException>()
        {
            @Override
            public JellyType number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return new JellyTypeNumberWithUnit(JellyUnit.fromConcrete(numberInfo.getUnit()));
            }

            @Override
            public JellyType text() throws InternalException, InternalException
            {
                return JellyTypePrimitive.text();
            }

            @Override
            public JellyType date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return JellyTypePrimitive.date(dateTimeInfo);
            }

            @Override
            public JellyType bool() throws InternalException, InternalException
            {
                return JellyTypePrimitive.bool();
            }

            @Override
            public JellyType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                if (typeVars.isEmpty())
                    return new JellyTypeIdent(typeName.getRaw());
                
                return new JellyTypeApply(typeName, Utility.mapListInt(typeVars, e -> 
                    e.mapBothInt(u -> JellyUnit.fromConcrete(u), t -> fromConcrete(t))
                ));
            }

            @Override
            public JellyType record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return new JellyTypeRecord(Utility.mapValuesInt(fields, t -> new Field(fromConcrete(t), false)), true);
            }

            @Override
            public JellyType array(DataType inner) throws InternalException, InternalException
            {
                return new JellyTypeArray(fromConcrete(inner));
            }

            @Override
            public JellyType function(ImmutableList<DataType> argType, DataType resultType) throws InternalException, InternalException
            {
                return new JellyTypeFunction(Utility.mapListInt(argType, a -> fromConcrete(a)), fromConcrete(resultType));
            }
        });
    }
}
