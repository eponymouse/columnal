package records.jellytype;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.BracketedTypeContext;
import records.grammar.FormatParser.NumberContext;
import records.grammar.FormatParser.TagRefParamContext;
import records.grammar.FormatParser.TypeContext;
import records.grammar.FormatParser.UnbracketedTypeContext;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;
import utility.Utility;

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

    public abstract TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException;

    public abstract DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UserException;

    public abstract void save(OutputBuilder output) throws InternalException;

    public abstract boolean equals(@Nullable Object o);

    public abstract int hashCode();

    // For every tagged type use anywhere within this type, call back the given function with the name.
    public abstract void forNestedTagged(Consumer<TypeId> nestedTagged);

    public static JellyType fromConcrete(DataType t)
    {
        return new JellyTypeConcrete(t);
    }
    
    public static JellyType typeVariable(String name)
    {
        return new JellyTypeVariable(name);
    }
    
    public static JellyType tuple(ImmutableList<JellyType> members)
    {
        return new JellyTypeTuple(members);
    }

    public static JellyType tagged(String name, ImmutableList<JellyType> params)
    {
        return new JellyTypeTagged(name, Utility.mapListI(params, p -> Either.right(p)));
    }
    
    public static JellyType list(JellyType inner)
    {
        return new JellyTypeArray(inner);
    }
    
    public static JellyType load(TypeContext typeContext, TypeManager mgr) throws InternalException, UserException
    {
        if (typeContext.unbracketedType() != null)
            return load(typeContext.unbracketedType(), mgr);
        else if (typeContext.bracketedType() != null)
            return load(typeContext.bracketedType(), mgr);
        else
            throw new InternalException("Unrecognised case: " + typeContext);
    }

    private static JellyType load(BracketedTypeContext ctx, TypeManager mgr) throws InternalException, UserException
    {
        if (ctx.type() != null)
            return load(ctx.type(), mgr);
        else if (ctx.tuple() != null)
            return new JellyTypeTuple(Utility.mapListExI(ctx.tuple().type(), t -> load(t, mgr)));
        else if (ctx.functionType() != null)
            return new JellyTypeFunction(load(ctx.functionType().type(0), mgr), load(ctx.functionType().type(1), mgr));
        throw new InternalException("Unrecognised case: " + ctx);
    }

    private static JellyType load(UnbracketedTypeContext ctx, TypeManager mgr) throws InternalException, UserException
    {
        if (ctx.BOOLEAN() != null)
            return new JellyTypeConcrete(DataType.BOOLEAN);
        else if (ctx.number() != null)
            return load(ctx.number(), mgr);
        else if (ctx.TEXT() != null)
            return new JellyTypeConcrete(DataType.TEXT);
        else if (ctx.date() != null)
        {
            if (ctx.date().DATETIME() != null)
                return new JellyTypeConcrete(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)));
            else if (ctx.date().YEARMONTHDAY() != null)
                return new JellyTypeConcrete(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)));
            else if (ctx.date().TIMEOFDAY() != null)
                return new JellyTypeConcrete(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)));
            if (ctx.date().YEARMONTH() != null)
                return new JellyTypeConcrete(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)));
            if (ctx.date().DATETIMEZONED() != null)
                return new JellyTypeConcrete(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)));
        }
        else if (ctx.tagRef() != null)
        {
            return new JellyTypeTagged(ctx.tagRef().ident().getText(), Utility.mapListExI(ctx.tagRef().tagRefParam(), param -> load(param, mgr)));
        }
        else if (ctx.array() != null)
        {
            return new JellyTypeArray(load(ctx.array().type(), mgr));
        }
        else if (ctx.typeVar() != null)
        {
            return new JellyTypeVariable(ctx.typeVar().ident().getText());
        }
        throw new InternalException("Unrecognised case: " + ctx.getText());
    }

    private static Either<JellyUnit, JellyType> load(TagRefParamContext param, TypeManager mgr) throws InternalException, UserException
    {
        if (param.bracketedType() != null)
            return Either.right(load(param.bracketedType(), mgr));
        else if (param.UNIT() != null)
            return Either.left(JellyUnit.load(param.UNIT().getText(), mgr.getUnitManager()));
        else if (param.UNITVAR() != null)
            return Either.left(JellyUnit.unitVariable(param.ident().getText()));
        throw new InternalException("Unrecognised case: " + param);
    }

    private static JellyType load(NumberContext number, TypeManager mgr) throws InternalException, UserException
    {
        if (number.UNIT() == null)
            return new JellyTypeConcrete(DataType.NUMBER);
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

        R tagged(TypeId typeName, ImmutableList<Either<JellyUnit, JellyType>> typeParams) throws InternalException, E;
        R tuple(ImmutableList<JellyType> inner) throws InternalException, E;
        // If null, array is empty and thus of unknown type
        R array(JellyType inner) throws InternalException, E;

        R function(JellyType argType, JellyType resultType) throws InternalException, E;
        
        R typeVariable(String name) throws InternalException, E;
    }

    public static interface JellyTypeVisitor<R> extends JellyTypeVisitorEx<R, UserException>
    {

    }

    public abstract <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E;
}
