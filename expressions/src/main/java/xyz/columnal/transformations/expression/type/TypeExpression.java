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
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UnimplementedException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.grammar.FormatParser;
import xyz.columnal.grammar.FormatParser.ApplyArgumentExpressionContext;
import xyz.columnal.grammar.FormatParser.ApplyTypeExpressionContext;
import xyz.columnal.grammar.FormatParser.ArrayTypeExpressionContext;
import xyz.columnal.grammar.FormatParser.DateContext;
import xyz.columnal.grammar.FormatParser.InvalidOpsTypeExpressionContext;
import xyz.columnal.grammar.FormatParser.RecordTypeExpressionContext;
import xyz.columnal.grammar.FormatParser.TypeExpressionTerminalContext;
import xyz.columnal.grammar.FormatParserBaseVisitor;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.jellytype.JellyType.JellyTypeVisitorEx;
import xyz.columnal.jellytype.JellyTypeRecord.Field;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.QuickFix;
import xyz.columnal.transformations.expression.Replaceable;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Comparator;
import java.util.Map.Entry;

public abstract class TypeExpression implements StyledShowable, Replaceable<TypeExpression>
{

    @SuppressWarnings("recorded")
    public static TypeExpression fromDataType(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<TypeExpression, InternalException>()
        {
            @Override
            public TypeExpression number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return new NumberTypeExpression(UnitExpression.load(numberInfo.getUnit()));
            }

            @Override
            public TypeExpression text() throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(dataType);
            }

            @Override
            public TypeExpression date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(dataType);
            }

            @Override
            public TypeExpression bool() throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(dataType);
            }

            @Override
            public TypeExpression tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                if (typeVars.isEmpty())
                    return new IdentTypeExpression(typeName.getRaw());
                ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builderWithExpectedSize(typeVars.size());
                for (Either<Unit, DataType> typeVar : typeVars)
                {
                    args.add(typeVar.<UnitExpression, TypeExpression>mapBothInt(u -> UnitExpression.load(u), t -> fromDataType(t)));
                }
                return new TypeApplyExpression(typeName.getRaw(), args.build());
            }

            @Override
            public TypeExpression record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                ImmutableList.Builder<Pair<@ExpressionIdentifier String, TypeExpression>> b = ImmutableList.builderWithExpectedSize(fields.size());
                for (Entry<@ExpressionIdentifier String, DataType> entry : Utility.iterableStream(fields.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey()))))
                {
                    b.add(new Pair<>(entry.getKey(), fromDataType(entry.getValue())));
                }
                return new RecordTypeExpression(b.build());
            }

            @Override
            public TypeExpression array(@Nullable DataType inner) throws InternalException, InternalException
            {
                return new ListTypeExpression(inner == null ? new InvalidIdentTypeExpression("") : fromDataType(inner));
            }

            @Override
            public TypeExpression function(ImmutableList<DataType> argTypes, DataType resultType) throws InternalException, InternalException
            {
                return new TypeApplyExpression("Function", Utility.<DataType, Either<UnitExpression, TypeExpression>>mapListInt(Utility.<DataType>concatI(argTypes, ImmutableList.<DataType>of(resultType)), t -> Either.<UnitExpression, TypeExpression>right(fromDataType(t))));
            }
        });
    }

    @SuppressWarnings("recorded")
    public static TypeExpression fromJellyType(JellyType original, TypeManager mgr) throws InternalException, UserException
    {
        return original.apply(new JellyTypeVisitorEx<TypeExpression, UserException>()
        {
            @Override
            public TypeExpression number(JellyUnit unit) throws InternalException, InternalException
            {
                return new NumberTypeExpression(UnitExpression.load(unit));
            }

            @Override
            public TypeExpression text() throws InternalException, UserException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of(), mgr));
            }

            @Override
            public TypeExpression date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of(), mgr));
            }

            @Override
            public TypeExpression bool() throws InternalException, UserException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of(), mgr));
            }

            @Override
            public TypeExpression applyTagged(TypeId typeName, ImmutableList<Either<JellyUnit, JellyType>> typeVars) throws InternalException, UserException
            {
                if (typeVars.isEmpty())
                    return new IdentTypeExpression(typeName.getRaw());
                ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builderWithExpectedSize(typeVars.size());
                for (Either<JellyUnit, JellyType> typeVar : typeVars)
                {
                    args.add(typeVar.<UnitExpression, TypeExpression>mapBothEx(u -> UnitExpression.load(u), t -> fromJellyType(t, mgr)));
                }
                return new TypeApplyExpression(typeName.getRaw(), args.build());
            }

            @Override
            public TypeExpression record(ImmutableMap<@ExpressionIdentifier String, Field> fields, boolean complete) throws InternalException, UserException
            {
                ImmutableList.Builder<Pair<@ExpressionIdentifier String, TypeExpression>> b = ImmutableList.builderWithExpectedSize(fields.size());
                for (Entry<@ExpressionIdentifier String, Field> entry : Utility.iterableStream(fields.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey()))))
                {
                    b.add(new Pair<>(entry.getKey(), fromJellyType(entry.getValue().getJellyType(), mgr)));
                }
                return new RecordTypeExpression(b.build());
            }

            @Override
            public TypeExpression array(JellyType inner) throws InternalException, UserException
            {
                return new ListTypeExpression(fromJellyType(inner, mgr));
            }

            @Override
            public TypeExpression function(ImmutableList<JellyType> argType, JellyType resultType) throws InternalException, InternalException
            {
                throw new UnimplementedException();
            }

            @Override
            public TypeExpression ident(String name) throws InternalException, InternalException
            {
                return InvalidIdentTypeExpression.identOrUnfinished(name);
            }
        });
    }

    public abstract String save(SaveDestination saveDestination, TableAndColumnRenames renames);

    public abstract @Nullable DataType toDataType(TypeManager typeManager);

    public abstract @Recorded JellyType toJellyType(@Recorded TypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression;
    
    public static interface JellyRecorder
    {
        public @Recorded JellyType record(JellyType jellyType, @Recorded TypeExpression source);
    }
    
    public static class UnJellyableTypeExpression extends UserException
    {
        private final Either<@Recorded UnitExpression, @Recorded TypeExpression> source; // For error location
        private final ImmutableList<QuickFix<UnitExpression>> fixes;

        public UnJellyableTypeExpression(String message, @Recorded TypeExpression source)
        {
            super(message);
            this.source = Either.right(source);
            this.fixes = ImmutableList.of();
        }

        public UnJellyableTypeExpression(StyledString message, @Recorded UnitExpression source, ImmutableList<QuickFix<UnitExpression>> fixes)
        {
            super(message);
            this.source = Either.left(source);
            this.fixes = fixes;
        }

        public Either<@Recorded UnitExpression, @Recorded TypeExpression> getSource()
        {
            return source;
        }

        public ImmutableList<QuickFix<UnitExpression>> getFixes()
        {
            return fixes;
        }
    }

    @SuppressWarnings("recorded")
    public static TypeExpression parseTypeExpression(String src) throws UserException, InternalException
    {
        class WrappedUserException extends RuntimeException
        {
            private final UserException e;

            WrappedUserException(UserException e)
            {
                this.e = e;
            }
        }

        class WrappedInternalException extends RuntimeException
        {
            private final InternalException e;

            WrappedInternalException(InternalException e)
            {
                this.e = e;
            }
        }
        
        try
        {
            return Utility.parseAsOne(src, FormatLexer::new, FormatParser::new, p -> p.completeTypeExpression()).accept(new FormatParserBaseVisitor<TypeExpression>()
            {
                @Override
                public TypeExpression visitTypeExpressionTerminal(TypeExpressionTerminalContext ctx)
                {
                    try
                    {
                        if (ctx.INCOMPLETE() != null)
                        {
                            return new IdentTypeExpression(ctx.STRING().getText());
                        }
                        else if (ctx.UNIT() != null)
                        {
                            String withCurly = ctx.UNIT().getText();
                            return new UnitLiteralTypeExpression(UnitExpression.load(withCurly.substring(1, withCurly.length() - 1)));
                        }
                        else if (ctx.number() != null)
                        {
                            if (ctx.number().UNIT() != null)
                            {
                                String withCurly = ctx.number().UNIT().getText();
                                return new NumberTypeExpression(UnitExpression.load(withCurly.substring(1, withCurly.length() - 1)));
                            }
                            else
                                return new NumberTypeExpression(null);
                        }
                        else if (ctx.BOOLEAN() != null)
                        {
                            return new TypePrimitiveLiteral(DataType.BOOLEAN);
                        }
                        else if (ctx.TEXT() != null)
                        {
                            return new TypePrimitiveLiteral(DataType.TEXT);
                        }
                        else if (ctx.ident() != null)
                        {
                            return new IdentTypeExpression(IdentifierUtility.fromParsed(ctx.ident()));
                        }
                        else if (ctx.date() != null)
                        {
                            DateContext d = ctx.date();
                            if (d.YEARMONTHDAY() != null)
                                return new TypePrimitiveLiteral(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)));
                            else if (d.YEARMONTH() != null)
                                return new TypePrimitiveLiteral(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)));
                            else if (d.DATETIME() != null)
                                return new TypePrimitiveLiteral(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)));
                            else if (d.DATETIMEZONED() != null)
                                return new TypePrimitiveLiteral(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)));
                            else if (d.TIMEOFDAY() != null)
                                return new TypePrimitiveLiteral(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)));
                        }
                        
                        throw new UserException("Cannot parse: " + ctx.getText() + " unknown case");
                    }
                    catch (UserException e)
                    {
                        throw new WrappedUserException(e);
                    }
                    catch (InternalException e)
                    {
                        throw new WrappedInternalException(e);
                    }
                }

                @Override
                public TypeExpression visitArrayTypeExpression(ArrayTypeExpressionContext ctx)
                {
                    return new ListTypeExpression(visit(ctx.typeExpression()));
                }

                @Override
                public TypeExpression visitRecordTypeExpression(RecordTypeExpressionContext ctx)
                {
                    ImmutableList.Builder<Pair<@ExpressionIdentifier String, TypeExpression>> members = ImmutableList.builderWithExpectedSize(ctx.typeExpression().size());

                    for (int i = 0; i < ctx.fieldName().size(); i++)
                    {
                        @ExpressionIdentifier String fieldName = IdentifierUtility.asExpressionIdentifier(ctx.fieldName(i).getText());
                        if (fieldName != null)
                            members.add(new Pair<>(fieldName, visitTypeExpression(ctx.typeExpression(i))));
                    }
                    
                    return new RecordTypeExpression(members.build());
                }

                @Override
                public TypeExpression visitInvalidOpsTypeExpression(InvalidOpsTypeExpressionContext ctx)
                {
                    return new InvalidOpTypeExpression(ImmutableList.of()/*Utility.mapListI(ctx.typeExpression(), t -> visitTypeExpression(t)),
                        Utility.mapListI(ctx.STRING(), s -> s.getText())
                    */);
                }

                @Override
                public TypeExpression visitApplyTypeExpression(ApplyTypeExpressionContext ctx)
                {
                    @ExpressionIdentifier String typeName = IdentifierUtility.fromParsed(ctx.ident());

                    ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builderWithExpectedSize(ctx.applyArgumentExpression().size());
                    for (ApplyArgumentExpressionContext applyArgumentExpressionContext : ctx.applyArgumentExpression())
                    {
                        args.add(Either.right(visitApplyArgumentExpression(applyArgumentExpressionContext)));
                    }
                    return new TypeApplyExpression(typeName, args.build());
                }

                @Override
                public TypeExpression visitApplyArgumentExpression(ApplyArgumentExpressionContext ctx)
                {
                    if (ctx.typeExpression() != null)
                        return visitTypeExpression(ctx.typeExpression());
                    else if (ctx.recordTypeExpression() != null)
                        return visitRecordTypeExpression(ctx.recordTypeExpression());
                    try
                    {
                        throw new InternalException("Neither typeExpression not recordTypeExpression in argument");
                    }
                    catch (InternalException e)
                    {
                        throw new WrappedInternalException(e);
                    }
                }

                public TypeExpression visitChildren(RuleNode node) {
                    @Nullable TypeExpression result = this.defaultResult();
                    int n = node.getChildCount();

                    for(int i = 0; i < n && this.shouldVisitNextChild(node, result); ++i) {
                        ParseTree c = node.getChild(i);
                        TypeExpression childResult = c.accept(this);
                        if (childResult == null)
                            break;
                        result = this.aggregateResult(result, childResult);
                    }
                    if (result == null)
                        throw new WrappedInternalException(new InternalException("No parseTypeExpression rules matched for " + node.getText()));
                    else
                        return result;
                }
            });
        }
        catch (WrappedUserException e)
        {
            throw e.e;
        }
        catch (WrappedInternalException e)
        {
            throw e.e;
        }
        
    }
    
    public abstract boolean isEmpty();

    // Force sub-expressions to implement equals and hashCode:
    @Override
    public abstract boolean equals(@Nullable Object o);
    @Override
    public abstract int hashCode();

    // If this can be an ident key in a record type, return the ident:
    public abstract @Nullable @ExpressionIdentifier String asIdent();

    // Useful for debugging:
    @Override
    public String toString()
    {
        return save(SaveDestination.TO_STRING, TableAndColumnRenames.EMPTY);
    }
}
