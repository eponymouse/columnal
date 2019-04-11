package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.jellytype.JellyType;
import records.jellytype.JellyUnit;
import records.loadsave.OutputBuilder;
import records.transformations.expression.UnitExpression;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// An Nary expression applying a tagged type, e.g. Either-Int-String.  That would have three args.
public class TypeApplyExpression extends TypeExpression
{
    private final @ExpressionIdentifier String typeName;
    private final ImmutableList<Either<UnitExpression, TypeExpression>> arguments;

    public TypeApplyExpression(@ExpressionIdentifier String typeName,  ImmutableList<Either<UnitExpression, TypeExpression>> arguments)
    {
        this.typeName = typeName;
        // Turn any units which are encased in a type expression wrapper
        // back into actual units:
        this.arguments = Utility.mapListI(arguments, arg -> arg.flatMap(t -> {
            if (t instanceof UnitLiteralTypeExpression)
                return Either.left(((UnitLiteralTypeExpression)t).getUnitExpression());
            else
                return Either.right(t);
        }));
        if (arguments.isEmpty())
            Log.logStackTrace("Empty arguments in type apply");
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        StringBuilder sb = new StringBuilder();
        if (structured)
        {
            sb.append(OutputBuilder.stripQuotes(FormatLexer.VOCABULARY.getLiteralName(FormatLexer.APPLY)));
            sb.append(" ");
        }
        sb.append(typeName);
        for (int i = 0; i < arguments.size(); i++)
        {
            // Bit of a hack to look for TupleTypeExpression exactly...
            Either<UnitExpression, TypeExpression> e = arguments.get(i);
            sb.append(e.<String>either(u -> "({" + u.save(structured,  true) + "})", x -> x instanceof TupleTypeExpression ? x.save(structured, renames) : "(" + x.save(structured, renames) + ")"));
        }
        return sb.toString();
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        // Shouldn't happen:
        if (arguments.isEmpty())
            return null;

        TaggedTypeDefinition def = typeManager.getKnownTaggedTypes().get(new TypeId(typeName));
        if (def == null)
            return null;
        if (def.getTypeArguments().size() != arguments.size())
        {
            // Wrong number of type arguments
            return null;
        }
        
        // If we're here, right number of arguments!
        List<Either<Unit, DataType>> typeArgs = new ArrayList<>();
        for (Either<UnitExpression, TypeExpression> arg : arguments)
        {
            @Nullable Either<Unit, DataType> type = Either.surfaceNull(arg.<@Nullable Unit, @Nullable DataType>mapBoth(u -> u.asUnit(typeManager.getUnitManager()).<@Nullable Unit>either(e -> null, u2 -> {
                try
                {
                    return u2.makeUnit(ImmutableMap.of());
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return null;
                }
            }), t -> t.toDataType(typeManager)));
            if (type == null)
                return null;
            typeArgs.add(type);
        }
        try
        {
            return def.instantiate(ImmutableList.copyOf(typeArgs), typeManager);
        }
        catch (UserException | InternalException e)
        {
            return null;
        }
    }

    @Override
    public JellyType toJellyType(TypeManager typeManager) throws InternalException, UserException
    {
        if (arguments.isEmpty())
            throw new InternalException("Empty type-apply expression");

        ImmutableList<Either<JellyUnit, JellyType>> args =
            Utility.mapListExI(
                arguments,
                arg -> arg.mapBothEx(u -> u.asUnit(typeManager.getUnitManager()).eitherEx((Pair<@Nullable StyledString, List<UnitExpression>> p) -> {throw new UserException(p.getFirst() == null ? "Invalid unit" : p.getFirst().toPlain());}, ju -> ju), t -> t.toJellyType(typeManager))
            );
        
        return JellyType.tagged(new TypeId(typeName), args);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.concat(StyledString.s(typeName), arguments.stream().map(e -> StyledString.roundBracket(e.either(UnitExpression::toStyledString, TypeExpression::toStyledString))).collect(StyledString.joining("")));
    }

    public @ExpressionIdentifier String getTypeName()
    {
        return typeName;
    }

    // Gets arguments, without the leading identifier
    public ImmutableList<Either<UnitExpression, TypeExpression>> getArgumentsOnly()
    {
        return arguments;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeApplyExpression that = (TypeApplyExpression) o;
        return Objects.equals(typeName, that.typeName) && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(typeName, arguments);
    }

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return new TypeApplyExpression(typeName, Utility.mapListI(arguments, x -> x.map(t -> t.replaceSubExpression(toReplace, replaceWith))));
    }
}
