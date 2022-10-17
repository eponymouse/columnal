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

package xyz.columnal.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.grammar.ExpressionParser.AddSubtractExpressionContext;
import xyz.columnal.grammar.ExpressionParser.AndExpressionContext;
import xyz.columnal.grammar.ExpressionParser.AnyContext;
import xyz.columnal.grammar.ExpressionParser.ArrayExpressionContext;
import xyz.columnal.grammar.ExpressionParser.BooleanLiteralContext;
import xyz.columnal.grammar.ExpressionParser.BracketedExpressionContext;
import xyz.columnal.grammar.ExpressionParser.CallExpressionContext;
import xyz.columnal.grammar.ExpressionParser.ColumnRefContext;
import xyz.columnal.grammar.ExpressionParser.ConstructorContext;
import xyz.columnal.grammar.ExpressionParser.CustomLiteralExpressionContext;
import xyz.columnal.grammar.ExpressionParser.DefineExpressionContext;
import xyz.columnal.grammar.ExpressionParser.DefinitionContext;
import xyz.columnal.grammar.ExpressionParser.DivideExpressionContext;
import xyz.columnal.grammar.ExpressionParser.ExpressionContext;
import xyz.columnal.grammar.ExpressionParser.FieldAccessExpressionContext;
import xyz.columnal.grammar.ExpressionParser.GreaterThanExpressionContext;
import xyz.columnal.grammar.ExpressionParser.HasTypeExpressionContext;
import xyz.columnal.grammar.ExpressionParser.IfThenElseExpressionContext;
import xyz.columnal.grammar.ExpressionParser.ImplicitLambdaParamContext;
import xyz.columnal.grammar.ExpressionParser.InvalidOpExpressionContext;
import xyz.columnal.grammar.ExpressionParser.InvalidOpItemContext;
import xyz.columnal.grammar.ExpressionParser.LambdaExpressionContext;
import xyz.columnal.grammar.ExpressionParser.LessThanExpressionContext;
import xyz.columnal.grammar.ExpressionParser.MatchClauseContext;
import xyz.columnal.grammar.ExpressionParser.MatchContext;
import xyz.columnal.grammar.ExpressionParser.NotEqualExpressionContext;
import xyz.columnal.grammar.ExpressionParser.NumericLiteralContext;
import xyz.columnal.grammar.ExpressionParser.OrExpressionContext;
import xyz.columnal.grammar.ExpressionParser.PatternContext;
import xyz.columnal.grammar.ExpressionParser.PlusMinusPatternContext;
import xyz.columnal.grammar.ExpressionParser.RaisedExpressionContext;
import xyz.columnal.grammar.ExpressionParser.RecordExpressionContext;
import xyz.columnal.grammar.ExpressionParser.StandardFunctionContext;
import xyz.columnal.grammar.ExpressionParser.StringConcatExpressionContext;
import xyz.columnal.grammar.ExpressionParser.StringLiteralContext;
import xyz.columnal.grammar.ExpressionParser.TableIdContext;
import xyz.columnal.grammar.ExpressionParser.TimesExpressionContext;
import xyz.columnal.grammar.ExpressionParser.TopLevelExpressionContext;
import xyz.columnal.grammar.ExpressionParser.UnfinishedContext;
import xyz.columnal.grammar.ExpressionParser.VarRefContext;
import xyz.columnal.log.Log;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.ExpressionLexer;
import xyz.columnal.grammar.ExpressionLexer2;
import xyz.columnal.grammar.ExpressionParser;
import xyz.columnal.grammar.ExpressionParser2;
import xyz.columnal.grammar.ExpressionParser2.NamespaceContext;
import xyz.columnal.grammar.ExpressionParser2.SingleIdentContext;
import xyz.columnal.grammar.ExpressionParser2BaseVisitor;
import xyz.columnal.grammar.ExpressionParserBaseVisitor;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.DefineExpression.Definition;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.expression.MatchExpression.Pattern;
import xyz.columnal.transformations.expression.NaryOpExpression.TypeProblemDetails;
import xyz.columnal.transformations.expression.QuickFix.QuickFixReplace;
import xyz.columnal.transformations.expression.UnitExpression.UnitLookupException;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.type.InvalidIdentTypeExpression;
import xyz.columnal.transformations.expression.type.InvalidOpTypeExpression;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorStream;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 21/01/2017.
 */
public class ExpressionUtil
{
    // Parameter should be Expression/UnitExpression/etc
    @OnThread(Tag.Any)
    public static String makeCssClass(StyledShowable replacement)
    {
        return makeCssClass(replacement.toStyledString().toPlain());
    }

    @OnThread(Tag.Any)
    public static String makeCssClass(String replacement)
    {
        Log.debug("Munging: " + replacement);
        return "id-munged-" + replacement.codePoints().mapToObj(i -> Integer.toString(i)).collect(Collectors.joining("-"));
    }

    public static boolean isCallTarget(Expression expression)
    {
        return expression instanceof IdentExpression;
    }

    public static Stream<TableId> tablesFromExpression(Expression expression)
    {
        return tablesFromExpressions(Stream.of(expression));
    }

    @SuppressWarnings("recorded")
    public static Stream<TableId> tablesFromExpressions(Stream<Expression> expressions)
    {
        return expressions.flatMap(e -> e.visit(new ExpressionVisitorStream<TableId>() {
            @Override
            public Stream<TableId> ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                if (namespace != null)
                {
                    switch (namespace)
                    {
                        case "column":
                            if (idents.size() == 2)
                                return Stream.of(new TableId(idents.get(0)));
                            break;
                        case "table":
                            return Stream.of(new TableId(idents.get(0)));
                    }
                }
                return super.ident(self, namespace, idents, isVariable);
            }
        }));
    }

    @SuppressWarnings("recorded")
    public static Stream<Pair<@Nullable TableId, ColumnId>> columnsFromExpressions(Stream<Expression> expressions)
    {
        return expressions.flatMap(new Function<Expression, Stream<? extends Pair<@Nullable TableId, ColumnId>>>()
        {
            @Override
            public Stream<Pair<@Nullable TableId, ColumnId>> apply(Expression e)
            {
                return e.visit(new ExpressionVisitorStream<Pair<@Nullable TableId, ColumnId>>()
                {
                    @Override
                    public Stream<Pair<@Nullable TableId, ColumnId>> ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
                    {
                        if (namespace != null)
                        {
                            switch (namespace)
                            {
                                case "column":
                                    if (idents.size() == 2)
                                        return Stream.of(new Pair<@Nullable TableId, ColumnId>(new TableId(idents.get(0)), new ColumnId(idents.get(1))));
                                    else if (idents.size() == 1)
                                        return Stream.of(new Pair<@Nullable TableId, ColumnId>(null, new ColumnId(idents.get(0))));
                                    break;
                            }
                        }
                        return super.ident(self, namespace, idents, isVariable);
                    }
                });
            }
        });
    }
    
    @SuppressWarnings("recorded")
    @OnThread(Tag.Any)
    public static List<QuickFix<Expression>> quickFixesForTypeError(TypeManager typeManager, FunctionLookup functionLookup, Expression src, @Nullable DataType fix)
    {
        List<QuickFix<Expression>> quickFixes = new ArrayList<>();
        QuickFixReplace<Expression> makeTypeFix = () -> {
            return TypeLiteralExpression.fixType(functionLookup, fix == null ? new InvalidOpTypeExpression(ImmutableList.<@Recorded TypeExpression>of()) : TypeExpression.fromDataType(fix), src);
        };
        quickFixes.add(new QuickFix<Expression>(StyledString.s(TranslationUtility.getString("fix.setType")), ImmutableList.<String>of(), src, makeTypeFix));
        if (fix != null)
        {
            @NonNull DataType fixFinal = fix;
            quickFixes.add(new QuickFix<Expression>(StyledString.s(TranslationUtility.getString("fix.setTypeTo", fix.toString())), ImmutableList.of(), src, () -> TypeLiteralExpression.fixType(typeManager, functionLookup, JellyType.fromConcrete(fixFinal), src)));
        }
        return quickFixes;
    }

    @OnThread(Tag.Any)
    public static ImmutableList<QuickFix<Expression>> getFixesForMatchingNumericUnits(TypeState state, TypeProblemDetails p)
    {
        // Must be a units issue.  Check if fixing a numeric literal involved would make
        // the units match all non-literal units:
        List<Pair<NumericLiteral, @Nullable Unit>> literals = new ArrayList<>();
        List<@Nullable Unit> nonLiteralUnits = new ArrayList<>();
        for (int i = 0; i < p.expressions.size(); i++)
        {
            Expression expression = p.expressions.get(i);
            if (expression instanceof NumericLiteral)
            {
                NumericLiteral n = (NumericLiteral) expression;
                @Nullable @Recorded UnitExpression unitExpression = n.getUnitExpression();
                @Nullable Unit unit = null;
                if (unitExpression == null)
                    unit = Unit.SCALAR;
                else
                {
                    try
                    {
                        unit = unitExpression.asUnit(state.getUnitManager()).makeUnit(ImmutableMap.of());
                    }
                    catch (InternalException e)
                    {
                        Log.log(e);
                    }
                    catch (UnitLookupException e)
                    {
                        // Could not find unit, leave it as null
                    }
                }
                literals.add(new Pair<NumericLiteral, @Nullable Unit>(n, unit));
            }
            else
            {
                @Nullable TypeExp type = p.getType(i);
                if (type != null && !(type instanceof NumTypeExp))
                {
                    // Non-numeric type; definitely can't offer a sensible fix:
                    return ImmutableList.of();
                }
                nonLiteralUnits.add(type == null ? null : ((NumTypeExp) type).unit.toConcreteUnit());
            }
        }
        Log.debug(">>> literals: " + Utility.listToString(literals));
        Log.debug(">>> non-literals: " + Utility.listToString(nonLiteralUnits));
        
        // For us to offer the quick fix, we need the following conditions: all non-literals
        // have the same known unit (and there is at least one non-literal).
        List<Unit> uniqueNonLiteralUnits = Utility.filterOutNulls(nonLiteralUnits.stream()).distinct().collect(Collectors.<Unit>toList());
        if (uniqueNonLiteralUnits.size() == 1)
        {
            for (Pair<NumericLiteral, @Nullable Unit> literal : literals)
            {
                Log.debug("Us: " + p.getOurExpression() + " literal: " + literal.getFirst() + " match: " + (literal.getFirst() == p.getOurExpression()));
                Log.debug("Non-literal unit: " + uniqueNonLiteralUnits.get(0) + " us: " + literal.getSecond());
                if (literal.getFirst() == p.getOurExpression() && !uniqueNonLiteralUnits.get(0).equals(literal.getSecond()))
                {
                    return ImmutableList.of(new QuickFix<Expression>(StyledString.s(TranslationUtility.getString("fix.changeUnit", uniqueNonLiteralUnits.get(0).toString())), ImmutableList.of(), p.getOurExpression(), () -> {
                        return literal.getFirst().withUnit(uniqueNonLiteralUnits.get(0));
                    }));
                }
            }
        }
        return ImmutableList.of();
    }

    public static Expression parse(@Nullable String keyword, String src, ExpressionVersion expressionVersion, TypeManager typeManager, FunctionLookup functionLookup) throws UserException, InternalException
    {
        if (keyword != null)
        {
            src = src.trim();
            if (src.startsWith(keyword))
                src = src.substring(keyword.length());
            else
                throw new UserException("Missing keyword: " + keyword);
        }
        try
        {
            switch (expressionVersion)
            {
                case ONE:
                    return Utility.parseAsOne(src.replace("\r", "").replace("\n", ""), ExpressionLexer::new, ExpressionParser::new, p ->
                    {
                        return new CompileExpression(typeManager, functionLookup).visit(p.completeExpression().topLevelExpression());
                    });
                case TWO:
                default:
                    return Utility.parseAsOne(src.replace("\r", "").replace("\n", ""), ExpressionLexer2::new, ExpressionParser2::new, p ->
                    {
                        return new CompileExpression2(typeManager, functionLookup).visit(p.completeExpression().topLevelExpression());
                    });
            }
            
        }
        catch (RuntimeException e)
        {
            throw new UserException("Problem parsing expression \"" + src + "\"", e);
        }
    }

    @SuppressWarnings("recorded")
    private static class CompileExpression extends ExpressionParserBaseVisitor<Expression>
    {
        private final TypeManager typeManager;
        private final FunctionLookup functionLookup;

        public CompileExpression(TypeManager typeManager, FunctionLookup functionLookup)
        {
            this.typeManager = typeManager;
            this.functionLookup = functionLookup;
        }

        @Override
        public Expression visitColumnRef(ColumnRefContext ctx)
        {
            TableIdContext tableIdContext = ctx.tableId();
            if (ctx.columnId() == null)
                throw new RuntimeException("Error processing column reference");
            TableId tableName = tableIdContext == null ? null : new TableId(IdentifierUtility.fromParsed(tableIdContext.ident()));
            ColumnId columnName = new ColumnId(IdentifierUtility.fromParsed(ctx.columnId().ident()));
            // This isn't quite correct for the loading, but v1 was only
            // used during private beta, so we can live with incorrect loading:
            if (ctx.columnRefType().WHOLECOLUMN() != null && tableName != null)
                return IdentExpression.makeEntireColumnReference(tableName, columnName);
            else
                return IdentExpression.column(tableName, columnName);
        }

        @Override
        public Expression visitNumericLiteral(NumericLiteralContext ctx)
        {
            try
            {
                @Nullable @Recorded UnitExpression unitExpression;
                if (ctx.CURLIED() == null)
                {
                    unitExpression = null;
                }
                else
                {
                    String unitText = ctx.CURLIED().getText();
                    unitText = StringUtils.removeStart(StringUtils.removeEnd(unitText, "}"), "{");
                    unitExpression = UnitExpression.load(unitText);
                }
                return new NumericLiteral(Utility.parseNumber((ctx.ADD_OR_SUBTRACT() == null ? "" : ctx.ADD_OR_SUBTRACT().getText()) + ctx.NUMBER().getText()), unitExpression);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException("Error parsing unit: \"" + ctx.CURLIED().getText() + "\"", e);
            }
        }

        @Override
        public Expression visitStringLiteral(StringLiteralContext ctx)
        {
            return new StringLiteral(ctx.RAW_STRING().getText());
        }

        @Override
        public Expression visitBooleanLiteral(BooleanLiteralContext ctx)
        {
            return new BooleanLiteral(Boolean.valueOf(ctx.getText()));
        }

        @Override
        public Expression visitConstructor(ConstructorContext ctx)
        {
            @Nullable @ExpressionIdentifier String type = null;
            if (ctx.typeName() != null)
                type = IdentifierUtility.fromParsed(ctx.typeName().ident());
            @ExpressionIdentifier String tagName = IdentifierUtility.fromParsed(ctx.constructorName().ident());
            if (type != null)
                return IdentExpression.tag(type, tagName);
            else
                return IdentExpression.tag(tagName);
        }

        @Override
        public Expression visitNotEqualExpression(NotEqualExpressionContext ctx)
        {
            return new NotEqualExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public EqualExpression visitEqualExpression(ExpressionParser.EqualExpressionContext ctx)
        {
            return new EqualExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), ctx.EQUALITY_PATTERN() != null);
        }

        @Override
        public Expression visitAndExpression(AndExpressionContext ctx)
        {
            return new AndExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitOrExpression(OrExpressionContext ctx)
        {
            return new OrExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitRaisedExpression(RaisedExpressionContext ctx)
        {
            return new RaiseExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitDivideExpression(DivideExpressionContext ctx)
        {
            return new DivideExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitAddSubtractExpression(AddSubtractExpressionContext ctx)
        {
            return new AddSubtractExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), Utility.<TerminalNode, AddSubtractOp>mapList(ctx.ADD_OR_SUBTRACT(), op -> op.getText().equals("+") ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT));
        }

        @Override
        public Expression visitGreaterThanExpression(GreaterThanExpressionContext ctx)
        {
            try
            {
                return new ComparisonExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), Utility.<TerminalNode, ComparisonOperator>mapListExI(ctx.GREATER_THAN(), op -> ComparisonOperator.parse(op.getText())));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Expression visitLessThanExpression(LessThanExpressionContext ctx)
        {
            try
            {
                return new ComparisonExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), Utility.<TerminalNode, ComparisonOperator>mapListExI(ctx.LESS_THAN(), op -> ComparisonOperator.parse(op.getText())));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Expression visitTimesExpression(TimesExpressionContext ctx)
        {
            return new TimesExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitStringConcatExpression(StringConcatExpressionContext ctx)
        {
            return new StringConcatExpression(Utility.mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitIfThenElseExpression(IfThenElseExpressionContext ctx)
        {
            return IfThenElseExpression.unrecorded(visitTopLevelExpression(ctx.topLevelExpression(0)), visitTopLevelExpression(ctx.topLevelExpression(1)), visitTopLevelExpression(ctx.topLevelExpression(2)));
        }

        @Override
        public Expression visitDefineExpression(DefineExpressionContext ctx)
        {
            return visitDefinitions(ctx.definition()).either(bad -> {
                ImmutableList.Builder<Expression> b = ImmutableList.builder();
                for (Expression expression : bad)
                {
                    b.add(new InvalidIdentExpression("@define"));
                    b.add(expression);
                }
                b.add(new InvalidIdentExpression("@then"));
                b.add(visitTopLevelExpression(ctx.topLevelExpression()));
                b.add(new InvalidIdentExpression("@enddefine"));
                return new InvalidOperatorExpression(b.build());
            }, mixed -> DefineExpression.unrecorded(mixed, visitTopLevelExpression(ctx.topLevelExpression())));
        }
        
        private Either<ImmutableList<Expression>, ImmutableList<Either<HasTypeExpression, Definition>>> visitDefinitions(List<DefinitionContext> definitionContexts)
        {
            Either<ImmutableList.Builder<Expression>, ImmutableList.Builder<Either<HasTypeExpression, Definition>>> builders = Either.<ImmutableList.Builder<Expression>, ImmutableList.Builder<Either<HasTypeExpression, Definition>>>right(ImmutableList.<Either<HasTypeExpression, Definition>>builder());

            for (DefinitionContext def : definitionContexts)
            {
                if (def.expression() != null && def.expression().size() > 0)
                {
                    Expression lhs = visitExpression(def.expression(0));
                    Expression rhs = visitExpression(def.expression(1));
                    builders.either_(b -> b.add(new EqualExpression(ImmutableList.of(lhs, rhs), false)), b -> b.add(Either.right(new Definition(lhs, rhs))));
                }
                else
                {
                    Expression expression = visitHasTypeExpression(def.hasTypeExpression());
                    if (!(expression instanceof HasTypeExpression))
                    {
                        builders = Either.<ImmutableList.Builder<Expression>, ImmutableList.Builder<Either<HasTypeExpression, Definition>>>left(builders.<ImmutableList.Builder<Expression>>either(b -> b, mixed -> {
                            ImmutableList.Builder<Expression> b = ImmutableList.builder();
                            b.addAll(Utility.mapListI(mixed.build(), e -> e.either(x -> x, x -> new EqualExpression(ImmutableList.of(x.lhsPattern, x.rhsValue), false))));
                            return b;
                        }));
                    }
                                
                    builders.either_(b -> b.add(expression), b -> b.add(Either.left((HasTypeExpression)expression)));
                }
            }
            
            return builders.mapBoth(b -> b.build(), b -> b.build());
        }

        @Override
        public Expression visitHasTypeExpression(HasTypeExpressionContext ctx)
        {
            Expression customLiteralExpression = visitCustomLiteralExpression(ctx.customLiteralExpression());
            return new HasTypeExpression(IdentifierUtility.fromParsed(ctx.varRef().ident()), customLiteralExpression);
        }

        @Override
        public Expression visitLambdaExpression(LambdaExpressionContext ctx)
        {
            List<TopLevelExpressionContext> es = ctx.topLevelExpression();
            return new LambdaExpression(Utility.mapListI(es.subList(0, es.size() - 1), e -> visitTopLevelExpression(e)), visitTopLevelExpression(es.get(es.size() - 1)));
        }

        @Override
        public Expression visitCustomLiteralExpression(CustomLiteralExpressionContext ctx)
        {
            String literalContent = StringUtils.removeEnd(ctx.CUSTOM_LITERAL().getText(), "}");
            class Lit<A>
            {
                final String prefix;
                final Function<A, Expression> makeExpression;
                final ExFunction<String, A> normalLoad;
                final Function<String, A> errorLoad;

                Lit(String prefix, Function<A, Expression> makeExpression, ExFunction<String, A> normalLoad, Function<String, A> errorLoad)
                {
                    this.prefix = prefix;
                    this.makeExpression = makeExpression;
                    this.normalLoad = normalLoad;
                    this.errorLoad = errorLoad;
                }
                
                public Optional<Expression> load(String src)
                {
                    if (src.startsWith(prefix))
                    {
                        src = StringUtils.removeStart(src, prefix);
                        A value;
                        try
                        {
                            value = normalLoad.apply(src);
                        }
                        catch (InternalException | UserException e)
                        {
                            Log.log(e);
                            value = errorLoad.apply(src);
                        }
                        return Optional.of(makeExpression.apply(value));
                    }
                    return Optional.empty();
                }
            }
            ImmutableList<Lit<?>> loaders = ImmutableList.of(
                new Lit<UnitExpression>("unit{", UnitLiteralExpression::new, UnitExpression::load, InvalidSingleUnitExpression::identOrUnfinished),
                new Lit<TypeExpression>("type{", TypeLiteralExpression::new, t -> TypeExpression.parseTypeExpression(t), InvalidIdentTypeExpression::identOrUnfinished
                ),
                new Lit<String>("date{", s -> new TemporalLiteral(DateTimeType.YEARMONTHDAY, s), s -> s, s -> s),
                new Lit<String>("dateym{", s -> new TemporalLiteral(DateTimeType.YEARMONTH, s), s -> s, s -> s),
                new Lit<String>("time{", s -> new TemporalLiteral(DateTimeType.TIMEOFDAY, s), s -> s, s -> s),
                new Lit<String>("datetime{", s -> new TemporalLiteral(DateTimeType.DATETIME, s), s -> s, s -> s),
                new Lit<String>("datetimezoned{", s -> new TemporalLiteral(DateTimeType.DATETIMEZONED, s), s -> s, s -> s)
            );
            
            Optional<Expression> loaded = loaders.stream().flatMap(l -> l.load(literalContent).map(s -> Stream.of(s)).orElse(Stream.empty())).findFirst();
            
            return loaded.orElseGet(() -> InvalidIdentExpression.identOrUnfinished(literalContent.replace('{','_').replace('}', '_')));
        }

        @Override
        public Expression visitCallExpression(CallExpressionContext ctx)
        {
            Expression function = visitCallTarget(ctx.callTarget());
            
            ImmutableList<@NonNull Expression> args;
            args = Utility.<TopLevelExpressionContext, Expression>mapListI(ctx.topLevelExpression(), e -> visitTopLevelExpression(e));
            
            return new CallExpression(function, args);
        }

        @Override
        public Expression visitStandardFunction(StandardFunctionContext ctx)
        {
            @ExpressionIdentifier String functionName = IdentifierUtility.fromParsed(ctx.ident());
            return IdentExpression.function(ImmutableList.<@ExpressionIdentifier String>of(functionName));
        }
        
        /*
        @Override
        public Expression visitBinaryOpExpression(BinaryOpExpressionContext ctx)
        {
            @Nullable Op op = Op.parse(ctx.binaryOp().getText());
            if (op == null)
                throw new RuntimeException("Broken operator parse: " + ctx.binaryOp().getText());
            return new BinaryOpExpression(visitExpression(ctx.expression().get(0)), op, visitExpression(ctx.expression().get(1)));
        }
        */

        @Override
        public Expression visitMatch(MatchContext ctx)
        {
            ImmutableList.Builder<MatchClause> clauses = ImmutableList.builder();
            for (MatchClauseContext matchClauseContext : ctx.matchClause())
            {
                ImmutableList.Builder<Pattern> patterns = ImmutableList.builderWithExpectedSize(matchClauseContext.pattern().size());
                for (PatternContext patternContext : matchClauseContext.pattern())
                {
                    @Nullable TopLevelExpressionContext guardExpression = patternContext.topLevelExpression().size() < 2 ? null : patternContext.topLevelExpression(1);
                    @Nullable Expression guard = guardExpression == null ? null : visitTopLevelExpression(guardExpression);
                    patterns.add(new Pattern(visitTopLevelExpression(patternContext.topLevelExpression(0)), guard));
                }
                clauses.add(new MatchClause(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), patterns.build(), visitTopLevelExpression(matchClauseContext.topLevelExpression())));
            }
            return new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), visitTopLevelExpression(ctx.topLevelExpression()), clauses.build(), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO));
        }

        @Override
        public Expression visitArrayExpression(ArrayExpressionContext ctx)
        {
            if (ctx.topLevelExpression() == null)
                return new ArrayExpression(ImmutableList.of());
            else
                return new ArrayExpression(Utility.<TopLevelExpressionContext, Expression>mapListI(ctx.topLevelExpression(), c -> visitTopLevelExpression(c)));
        }

        @Override
        public Expression visitRecordExpression(RecordExpressionContext ctx)
        {
            ImmutableList.Builder<Pair<@ExpressionIdentifier String, Expression>> members = ImmutableList.builderWithExpectedSize(ctx.ident().size());
            for (int i = 0; i < ctx.ident().size(); i++)
            {
                members.add(new Pair<>(IdentifierUtility.fromParsed(ctx.ident(i)), visitTopLevelExpression(ctx.topLevelExpression(i))));
            }
            return new RecordExpression(members.build());
        }

        @Override
        public Expression visitFieldAccessExpression(FieldAccessExpressionContext ctx)
        {
            return new FieldAccessExpression(visitExpression(ctx.expression()), IdentifierUtility.fromParsed(ctx.ident()));
        }

        @Override
        public Expression visitBracketedExpression(BracketedExpressionContext ctx)
        {
            return visitTopLevelExpression(ctx.topLevelExpression());
        }

        @Override
        public Expression visitVarRef(VarRefContext ctx)
        {
            return IdentExpression.load(IdentifierUtility.fromParsed(ctx.ident()));
        }

        @Override
        public Expression visitUnfinished(UnfinishedContext ctx)
        {
            return new InvalidIdentExpression(GrammarUtility.processEscapes(ctx.RAW_STRING().getText(), false));
        }

        @Override
        public Expression visitImplicitLambdaParam(ImplicitLambdaParamContext ctx)
        {
            return new ImplicitLambdaArg();
        }

        @Override
        public Expression visitPlusMinusPattern(PlusMinusPatternContext ctx)
        {
            return new PlusMinusPatternExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitAny(AnyContext ctx)
        {
            return new MatchAnythingExpression();
        }

        @Override
        public Expression visitInvalidOpExpression(InvalidOpExpressionContext ctx)
        {
            return new InvalidOperatorExpression(Utility.<InvalidOpItemContext, Expression>mapListI(ctx.invalidOpItem(), 
                c -> visitExpression(c.expression())));
        }

        public Expression visitChildren(RuleNode node) {
            @Nullable Expression result = this.defaultResult();
            int n = node.getChildCount();

            for(int i = 0; i < n && this.shouldVisitNextChild(node, result); ++i) {
                ParseTree c = node.getChild(i);
                Expression childResult = c.accept(this);
                if (childResult == null)
                    break;
                result = this.aggregateResult(result, childResult);
            }
            if (result == null)
                throw new RuntimeException("No CompileExpression rules matched for " + node.getText());
            else
                return result;
        }
    }

    @SuppressWarnings("recorded")
    private static class CompileExpression2 extends ExpressionParser2BaseVisitor<Expression>
    {
        private final TypeManager typeManager;
        private final FunctionLookup functionLookup;

        public CompileExpression2(TypeManager typeManager, FunctionLookup functionLookup)
        {
            this.typeManager = typeManager;
            this.functionLookup = functionLookup;
        }

        @Override
        public Expression visitNumericLiteral(ExpressionParser2.NumericLiteralContext ctx)
        {
            try
            {
                @Nullable @Recorded UnitExpression unitExpression;
                if (ctx.CURLIED() == null)
                {
                    unitExpression = null;
                }
                else
                {
                    String unitText = ctx.CURLIED().getText();
                    unitText = StringUtils.removeStart(StringUtils.removeEnd(unitText, "}"), "{");
                    unitExpression = UnitExpression.load(unitText);
                }
                return new NumericLiteral(Utility.parseNumber((ctx.ADD_OR_SUBTRACT() == null ? "" : ctx.ADD_OR_SUBTRACT().getText()) + ctx.NUMBER().getText()), unitExpression);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException("Error parsing unit: \"" + ctx.CURLIED().getText() + "\"", e);
            }
        }

        @Override
        public Expression visitStringLiteral(ExpressionParser2.StringLiteralContext ctx)
        {
            return new StringLiteral(ctx.RAW_STRING().getText());
        }

        @Override
        public Expression visitBooleanLiteral(ExpressionParser2.BooleanLiteralContext ctx)
        {
            return new BooleanLiteral(Boolean.valueOf(ctx.getText()));
        }

        @Override
        public Expression visitNotEqualExpression(ExpressionParser2.NotEqualExpressionContext ctx)
        {
            return new NotEqualExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public EqualExpression visitEqualExpression(ExpressionParser2.EqualExpressionContext ctx)
        {
            return new EqualExpression(Utility.<ExpressionParser2.ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), ctx.EQUALITY_PATTERN() != null);
        }

        @Override
        public Expression visitAndExpression(ExpressionParser2.AndExpressionContext ctx)
        {
            return new AndExpression(Utility.<ExpressionParser2.ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitOrExpression(ExpressionParser2.OrExpressionContext ctx)
        {
            return new OrExpression(Utility.<ExpressionParser2.ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitRaisedExpression(ExpressionParser2.RaisedExpressionContext ctx)
        {
            return new RaiseExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitDivideExpression(ExpressionParser2.DivideExpressionContext ctx)
        {
            return new DivideExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitAddSubtractExpression(ExpressionParser2.AddSubtractExpressionContext ctx)
        {
            return new AddSubtractExpression(Utility.<ExpressionParser2.ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), Utility.<TerminalNode, AddSubtractOp>mapList(ctx.ADD_OR_SUBTRACT(), op -> op.getText().equals("+") ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT));
        }

        @Override
        public Expression visitGreaterThanExpression(ExpressionParser2.GreaterThanExpressionContext ctx)
        {
            try
            {
                return new ComparisonExpression(Utility.<ExpressionParser2.ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), Utility.<TerminalNode, ComparisonOperator>mapListExI(ctx.GREATER_THAN(), op -> ComparisonOperator.parse(op.getText())));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Expression visitLessThanExpression(ExpressionParser2.LessThanExpressionContext ctx)
        {
            try
            {
                return new ComparisonExpression(Utility.<ExpressionParser2.ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), Utility.<TerminalNode, ComparisonOperator>mapListExI(ctx.LESS_THAN(), op -> ComparisonOperator.parse(op.getText())));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Expression visitTimesExpression(ExpressionParser2.TimesExpressionContext ctx)
        {
            return new TimesExpression(Utility.<ExpressionParser2.ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitStringConcatExpression(ExpressionParser2.StringConcatExpressionContext ctx)
        {
            return new StringConcatExpression(Utility.mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitIfThenElseExpression(ExpressionParser2.IfThenElseExpressionContext ctx)
        {
            return IfThenElseExpression.unrecorded(visitTopLevelExpression(ctx.topLevelExpression(0)), visitTopLevelExpression(ctx.topLevelExpression(1)), visitTopLevelExpression(ctx.topLevelExpression(2)));
        }

        @Override
        public Expression visitDefineExpression(ExpressionParser2.DefineExpressionContext ctx)
        {
            return visitDefinitions(ctx.definition()).either(bad -> {
                ImmutableList.Builder<Expression> b = ImmutableList.builder();
                for (Expression expression : bad)
                {
                    b.add(new InvalidIdentExpression("@define"));
                    b.add(expression);
                }
                b.add(new InvalidIdentExpression("@then"));
                b.add(visitTopLevelExpression(ctx.topLevelExpression()));
                b.add(new InvalidIdentExpression("@enddefine"));
                return new InvalidOperatorExpression(b.build());
            }, mixed -> DefineExpression.unrecorded(mixed, visitTopLevelExpression(ctx.topLevelExpression())));
        }

        private Either<ImmutableList<Expression>, ImmutableList<Either<HasTypeExpression, Definition>>> visitDefinitions(List<ExpressionParser2.DefinitionContext> definitionContexts)
        {
            Either<ImmutableList.Builder<Expression>, ImmutableList.Builder<Either<HasTypeExpression, Definition>>> builders = Either.<ImmutableList.Builder<Expression>, ImmutableList.Builder<Either<HasTypeExpression, Definition>>>right(ImmutableList.<Either<HasTypeExpression, Definition>>builder());

            for (ExpressionParser2.DefinitionContext def : definitionContexts)
            {
                if (def.expression() != null && def.expression().size() > 0)
                {
                    Expression lhs = visitExpression(def.expression(0));
                    Expression rhs = visitExpression(def.expression(1));
                    builders.either_(b -> b.add(new EqualExpression(ImmutableList.of(lhs, rhs), false)), b -> b.add(Either.right(new Definition(lhs, rhs))));
                }
                else
                {
                    Expression expression = visitHasTypeExpression(def.hasTypeExpression());
                    if (!(expression instanceof HasTypeExpression))
                    {
                        builders = Either.<ImmutableList.Builder<Expression>, ImmutableList.Builder<Either<HasTypeExpression, Definition>>>left(builders.<ImmutableList.Builder<Expression>>either(b -> b, mixed -> {
                            ImmutableList.Builder<Expression> b = ImmutableList.builder();
                            b.addAll(Utility.mapListI(mixed.build(), e -> e.either(x -> x, x -> new EqualExpression(ImmutableList.of(x.lhsPattern, x.rhsValue), false))));
                            return b;
                        }));
                    }

                    builders.either_(b -> b.add(expression), b -> b.add(Either.left((HasTypeExpression)expression)));
                }
            }

            return builders.mapBoth(b -> b.build(), b -> b.build());
        }

        @Override
        public Expression visitHasTypeExpression(ExpressionParser2.HasTypeExpressionContext ctx)
        {
            Expression customLiteralExpression = visitCustomLiteralExpression(ctx.customLiteralExpression());
            return new HasTypeExpression(IdentifierUtility.fromParsed(ctx.ident().singleIdent(ctx.ident().singleIdent().size() - 1)), customLiteralExpression);
        }

        @Override
        public Expression visitLambdaExpression(ExpressionParser2.LambdaExpressionContext ctx)
        {
            List<ExpressionParser2.TopLevelExpressionContext> es = ctx.topLevelExpression();
            return new LambdaExpression(Utility.mapListI(es.subList(0, es.size() - 1), e -> visitTopLevelExpression(e)), visitTopLevelExpression(es.get(es.size() - 1)));
        }

        @Override
        public Expression visitCustomLiteralExpression(ExpressionParser2.CustomLiteralExpressionContext ctx)
        {
            String literalContent = StringUtils.removeEnd(ctx.CUSTOM_LITERAL().getText(), "}");
            class Lit<A>
            {
                final String prefix;
                final Function<A, Expression> makeExpression;
                final ExFunction<String, A> normalLoad;
                final Function<String, A> errorLoad;

                Lit(String prefix, Function<A, Expression> makeExpression, ExFunction<String, A> normalLoad, Function<String, A> errorLoad)
                {
                    this.prefix = prefix;
                    this.makeExpression = makeExpression;
                    this.normalLoad = normalLoad;
                    this.errorLoad = errorLoad;
                }

                public Optional<Expression> load(String src)
                {
                    if (src.startsWith(prefix))
                    {
                        src = StringUtils.removeStart(src, prefix);
                        A value;
                        try
                        {
                            value = normalLoad.apply(src);
                        }
                        catch (InternalException | UserException e)
                        {
                            Log.log(e);
                            value = errorLoad.apply(src);
                        }
                        return Optional.of(makeExpression.apply(value));
                    }
                    return Optional.empty();
                }
            }
            ImmutableList<Lit<?>> loaders = ImmutableList.of(
                    new Lit<UnitExpression>("unit{", UnitLiteralExpression::new, UnitExpression::load, InvalidSingleUnitExpression::identOrUnfinished),
                    new Lit<TypeExpression>("type{", TypeLiteralExpression::new, t -> TypeExpression.parseTypeExpression(t), InvalidIdentTypeExpression::identOrUnfinished
                    ),
                    new Lit<String>("date{", s -> new TemporalLiteral(DateTimeType.YEARMONTHDAY, s), s -> s, s -> s),
                    new Lit<String>("dateym{", s -> new TemporalLiteral(DateTimeType.YEARMONTH, s), s -> s, s -> s),
                    new Lit<String>("time{", s -> new TemporalLiteral(DateTimeType.TIMEOFDAY, s), s -> s, s -> s),
                    new Lit<String>("datetime{", s -> new TemporalLiteral(DateTimeType.DATETIME, s), s -> s, s -> s),
                    new Lit<String>("datetimezoned{", s -> new TemporalLiteral(DateTimeType.DATETIMEZONED, s), s -> s, s -> s)
            );

            Optional<Expression> loaded = loaders.stream().flatMap(l -> l.load(literalContent).map(s -> Stream.of(s)).orElse(Stream.empty())).findFirst();

            return loaded.orElseGet(() -> InvalidIdentExpression.identOrUnfinished(literalContent.replace('{','_').replace('}', '_')));
        }

        @Override
        public Expression visitCallExpression(ExpressionParser2.CallExpressionContext ctx)
        {
            Expression function = visitCallTarget(ctx.callTarget());

            ImmutableList<@NonNull Expression> args;
            args = Utility.<ExpressionParser2.TopLevelExpressionContext, Expression>mapListI(ctx.topLevelExpression(), e -> visitTopLevelExpression(e));

            return new CallExpression(function, args);
        }
        
        /*
        @Override
        public Expression visitBinaryOpExpression(BinaryOpExpressionContext ctx)
        {
            @Nullable Op op = Op.parse(ctx.binaryOp().getText());
            if (op == null)
                throw new RuntimeException("Broken operator parse: " + ctx.binaryOp().getText());
            return new BinaryOpExpression(visitExpression(ctx.expression().get(0)), op, visitExpression(ctx.expression().get(1)));
        }
        */

        @Override
        public Expression visitMatch(ExpressionParser2.MatchContext ctx)
        {
            ImmutableList.Builder<MatchClause> clauses = ImmutableList.builder();
            for (ExpressionParser2.MatchClauseContext matchClauseContext : ctx.matchClause())
            {
                ImmutableList.Builder<Pattern> patterns = ImmutableList.builderWithExpectedSize(matchClauseContext.pattern().size());
                for (ExpressionParser2.PatternContext patternContext : matchClauseContext.pattern())
                {
                    ExpressionParser2.@Nullable TopLevelExpressionContext guardExpression = patternContext.topLevelExpression().size() < 2 ? null : patternContext.topLevelExpression(1);
                    @Nullable Expression guard = guardExpression == null ? null : visitTopLevelExpression(guardExpression);
                    patterns.add(new Pattern(visitTopLevelExpression(patternContext.topLevelExpression(0)), guard));
                }
                clauses.add(new MatchClause(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), patterns.build(), visitTopLevelExpression(matchClauseContext.topLevelExpression())));
            }
            return new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), visitTopLevelExpression(ctx.topLevelExpression()), clauses.build(), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO));
        }

        @Override
        public Expression visitArrayExpression(ExpressionParser2.ArrayExpressionContext ctx)
        {
            if (ctx.topLevelExpression() == null)
                return new ArrayExpression(ImmutableList.of());
            else
                return new ArrayExpression(Utility.<ExpressionParser2.TopLevelExpressionContext, Expression>mapListI(ctx.topLevelExpression(), c -> visitTopLevelExpression(c)));
        }

        @Override
        public Expression visitRecordExpression(ExpressionParser2.RecordExpressionContext ctx)
        {
            ImmutableList.Builder<Pair<@ExpressionIdentifier String, Expression>> members = ImmutableList.builderWithExpectedSize(ctx.singleIdent().size());
            for (int i = 0; i < ctx.singleIdent().size(); i++)
            {
                members.add(new Pair<>(IdentifierUtility.fromParsed(ctx.singleIdent(i)), visitTopLevelExpression(ctx.topLevelExpression(i))));
            }
            return new RecordExpression(members.build());
        }

        @Override
        public Expression visitIdent(ExpressionParser2.IdentContext ctx)
        {
            NamespaceContext namespaceContext = ctx.namespace();
            @ExpressionIdentifier String namespace = namespaceContext == null ? null : IdentifierUtility.fromParsed(namespaceContext.singleIdent());
            
            return IdentExpression.load(namespace, Utility.<SingleIdentContext, @ExpressionIdentifier String>mapListI(ctx.singleIdent(), IdentifierUtility::fromParsed));
        }

        @Override
        public Expression visitFieldAccessExpression(ExpressionParser2.FieldAccessExpressionContext ctx)
        {
            Expression lhs = visitExpression(ctx.expression(0));
            Expression rhs = visitExpression(ctx.expression(1));
            return FieldAccessExpression.fromBinary(lhs, rhs);
        }

        @Override
        public Expression visitBracketedExpression(ExpressionParser2.BracketedExpressionContext ctx)
        {
            return visitTopLevelExpression(ctx.topLevelExpression());
        }

        @Override
        public Expression visitUnfinished(ExpressionParser2.UnfinishedContext ctx)
        {
            return new InvalidIdentExpression(GrammarUtility.processEscapes(ctx.RAW_STRING().getText(), false));
        }

        @Override
        public Expression visitImplicitLambdaParam(ExpressionParser2.ImplicitLambdaParamContext ctx)
        {
            return new ImplicitLambdaArg();
        }

        @Override
        public Expression visitPlusMinusPattern(ExpressionParser2.PlusMinusPatternContext ctx)
        {
            return new PlusMinusPatternExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitAny(ExpressionParser2.AnyContext ctx)
        {
            return new MatchAnythingExpression();
        }

        @Override
        public Expression visitInvalidOpExpression(ExpressionParser2.InvalidOpExpressionContext ctx)
        {
            return new InvalidOperatorExpression(Utility.<ExpressionParser2.InvalidOpItemContext, Expression>mapListI(ctx.invalidOpItem(),
                    c -> visitExpression(c.expression())));
        }

        public Expression visitChildren(RuleNode node) {
            @Nullable Expression result = this.defaultResult();
            int n = node.getChildCount();

            for(int i = 0; i < n && this.shouldVisitNextChild(node, result); ++i) {
                ParseTree c = node.getChild(i);
                Expression childResult = c.accept(this);
                if (childResult == null)
                    break;
                result = this.aggregateResult(result, childResult);
            }
            if (result == null)
                throw new RuntimeException("No CompileExpression rules matched for " + node.getText());
            else
                return result;
        }
    }

}
