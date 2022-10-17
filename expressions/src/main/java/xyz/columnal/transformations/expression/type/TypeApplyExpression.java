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

package xyz.columnal.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.UnitExpression.UnitLookupException;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// An Nary expression applying a tagged type, e.g. Either-Int-String.  That would have three args.
public class TypeApplyExpression extends TypeExpression
{
    private final @ExpressionIdentifier String typeName;
    private final ImmutableList<Either<@Recorded UnitExpression, @Recorded TypeExpression>> arguments;

    public TypeApplyExpression(@ExpressionIdentifier String typeName,  ImmutableList<Either<@Recorded UnitExpression, @Recorded TypeExpression>> arguments)
    {
        this.typeName = typeName;
        // Turn any units which are encased in a type expression wrapper
        // back into actual units:
        this.arguments = Utility.<Either<@Recorded UnitExpression, @Recorded TypeExpression>, Either<@Recorded UnitExpression, @Recorded TypeExpression>>mapListI(arguments, arg -> arg.<@Recorded TypeExpression>flatMap(t -> {
            if (t instanceof UnitLiteralTypeExpression)
                return Either.<@Recorded UnitExpression, @Recorded TypeExpression>left(((UnitLiteralTypeExpression)t).getUnitExpression());
            else
                return Either.<@Recorded UnitExpression, @Recorded TypeExpression>right(t);
        }));
        if (arguments.isEmpty())
            Log.logStackTrace("Empty arguments in type apply");
    }

    @Override
    public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
    {
        StringBuilder sb = new StringBuilder();
        if (saveDestination.needKeywords())
        {
            sb.append(OutputBuilder.stripQuotes(FormatLexer.VOCABULARY.getLiteralName(FormatLexer.APPLY)));
            sb.append(" ");
        }
        sb.append(typeName);
        for (int i = 0; i < arguments.size(); i++)
        {
            // Bit of a hack to look for RecordTypeExpression exactly...
            Either<@Recorded UnitExpression, @Recorded TypeExpression> e = arguments.get(i);
            sb.append(e.<String>either(u -> "({" + u.save(saveDestination,  true) + "})", x -> x instanceof RecordTypeExpression ? x.save(saveDestination, renames) : "(" + x.save(saveDestination, renames) + ")"));
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
        for (Either<@Recorded UnitExpression, @Recorded TypeExpression> arg : arguments)
        {
            @Nullable Either<Unit, DataType> type = null;
            try
            {
                type = Either.surfaceNull(arg.<@Nullable Unit, @Nullable DataType, InternalException, UnitLookupException>mapBothEx2(u -> u.asUnit(typeManager.getUnitManager()).makeUnit(ImmutableMap.of()), t -> t.toDataType(typeManager)));
            }
            catch (InternalException e)
            {
                Log.log(e);
                return null;
            }
            catch (UnitLookupException e)
            {
                return null;
            }
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
    public @Recorded JellyType toJellyType(@Recorded TypeApplyExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression
    {
        if (arguments.isEmpty())
            throw new InternalException("Empty type-apply expression");

        ImmutableList.Builder<Either<JellyUnit, @Recorded JellyType>> args = ImmutableList.builderWithExpectedSize(arguments.size());

        for (int i = 0; i < arguments.size(); i++)
        {
            Either<@Recorded UnitExpression, @Recorded TypeExpression> arg = arguments.get(i);
            int iFinal = i;
            args.add(arg.<JellyUnit, @Recorded JellyType, InternalException, UnJellyableTypeExpression>mapBothEx2(u -> {
                try
                {
                    return u.asUnit(typeManager.getUnitManager());
                }
                catch (UnitLookupException e)
                {
                    throw new UnJellyableTypeExpression(e.errorMessage == null ? StyledString.s("Invalid unit") : e.errorMessage, e.errorItem, e.quickFixes);
                }
            }, (@Recorded TypeExpression t) -> t.toJellyType(typeManager, jellyRecorder)));
            
        }
        
        return jellyRecorder.record(JellyType.tagged(new TypeId(typeName), args.build()), this);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.concat(StyledString.s(typeName), arguments.stream().map(e -> StyledString.roundBracket(e.either(u -> StyledString.concat(StyledString.s("{"), u.toStyledString(), StyledString.s("}")), TypeExpression::toStyledString))).collect(StyledString.joining("")));
    }

    public @ExpressionIdentifier String getTypeName()
    {
        return typeName;
    }

    // Gets arguments, without the leading identifier
    public ImmutableList<Either<@Recorded UnitExpression, @Recorded TypeExpression>> getArgumentsOnly()
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

    @SuppressWarnings("recorded")
    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return new TypeApplyExpression(typeName, Utility.mapListI(arguments, x -> x.map(t -> t.replaceSubExpression(toReplace, replaceWith))));
    }

    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return null;
    }
}
