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

package xyz.columnal.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.AddSubtractExpression;
import xyz.columnal.transformations.expression.AndExpression;
import xyz.columnal.transformations.expression.ArrayExpression;
import xyz.columnal.transformations.expression.BracketedStatus;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.ExpressionLexer.Keyword;
import xyz.columnal.gui.lexeditor.ExpressionLexer.Op;
import xyz.columnal.gui.lexeditor.ExpressionSaver.BracketContent;
import xyz.columnal.gui.lexeditor.TopLevelEditor.DisplayType;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.ComparisonExpression;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.DefineExpression;
import xyz.columnal.transformations.expression.DefineExpression.DefineItem;
import xyz.columnal.transformations.expression.DefineExpression.Definition;
import xyz.columnal.transformations.expression.DivideExpression;
import xyz.columnal.transformations.expression.EqualExpression;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorder;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.ExpressionUtil;
import xyz.columnal.transformations.expression.FieldAccessExpression;
import xyz.columnal.transformations.expression.HasTypeExpression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.IfThenElseExpression;
import xyz.columnal.transformations.expression.ImplicitLambdaArg;
import xyz.columnal.transformations.expression.InvalidIdentExpression;
import xyz.columnal.transformations.expression.InvalidOperatorExpression;
import xyz.columnal.transformations.expression.LambdaExpression;
import xyz.columnal.transformations.expression.MatchExpression;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.expression.MatchExpression.Pattern;
import xyz.columnal.transformations.expression.NotEqualExpression;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.transformations.expression.OrExpression;
import xyz.columnal.transformations.expression.PlusMinusPatternExpression;
import xyz.columnal.transformations.expression.QuickFix;
import xyz.columnal.transformations.expression.RaiseExpression;
import xyz.columnal.transformations.expression.RecordExpression;
import xyz.columnal.transformations.expression.StringConcatExpression;
import xyz.columnal.transformations.expression.TimesExpression;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.UnitLiteralExpression;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.function.StandardFunctionDefinition;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorFlat;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.styled.StyledString.Builder;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorStream;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.Clickable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExpressionSaver extends SaverBase<Expression, ExpressionSaver, Op, Keyword, BracketContent>
{
    public class BracketContent implements StyledShowable
    {
        private final ImmutableList<@Recorded Expression> expressions;
        private final ImmutableList<Pair<Op, CanonicalSpan>> commas;

        public BracketContent(ImmutableList<@Recorded Expression> expressions, ImmutableList<Pair<Op, CanonicalSpan>> commas)
        {
            this.expressions = expressions;
            this.commas = commas;
        }

        @Override
        @OnThread(Tag.Any)
        public StyledString toStyledString()
        {
            return interleave(expressions, commas).stream().map(e -> e.either(x -> x.getFirst().toStyledString(), x -> x.toStyledString())).collect(StyledString.joining(""));
        }
    }
    
    private final FunctionLookup functionLookup;

    public ExpressionSaver(TypeManager typeManager, FunctionLookup functionLookup, InsertListener insertListener)
    {
        super(typeManager, insertListener);
        this.functionLookup = functionLookup;
    }

    @Override
    protected BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression> expectSingle(@UnknownInitialization(Object.class)ExpressionSaver this, EditorLocationAndErrorRecorder locationRecorder, CanonicalSpan location)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, Expression, Expression>()
        {
            @Override
            public @Nullable @Recorded Expression apply(@NonNull BracketContent items)
            {
                if (items.expressions.size() == 1)
                    return items.expressions.get(0);
                else
                    return null;
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        }, location, ImmutableList.of(tupleBracket(locationRecorder, location), makeList(locationRecorder, location)));
    }
    
    private ApplyBrackets<BracketContent, Expression, Expression> makeList(@UnknownInitialization(Object.class)ExpressionSaver this, EditorLocationAndErrorRecorder locationRecorder, CanonicalSpan location)
    {
        return new ApplyBrackets<BracketContent, Expression, Expression>()
        {
            @Override
            public @Nullable @Recorded Expression apply(@NonNull BracketContent items)
            {
                return locationRecorder.record(location, new ArrayExpression(items.expressions));
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return locationRecorder.record(location, new ArrayExpression(ImmutableList.<@Recorded Expression>of(singleItem)));
            }
        };
    }

    // Note: if we are copying to clipboard, callback will not be called
    public void saveKeyword(Keyword keyword, CanonicalSpan errorDisplayer)
    {
        Supplier<ImmutableList<@Recorded Expression>> prefixKeyword = () -> ImmutableList.<@Recorded Expression>of(this.<Expression>record(errorDisplayer, new InvalidIdentExpression(keyword.getContent())));
        
        if (keyword == Keyword.QUEST)
        {
            saveOperand(new ImplicitLambdaArg(), errorDisplayer);
        }
        else if (keyword == Keyword.OPEN_ROUND)
        {
            Supplier<ImmutableList<@Recorded Expression>> invalidPrefix = prefixKeyword;
            Function<CanonicalSpan, ApplyBrackets<BracketContent, Expression, Expression>> applyBrackets = c -> tupleBracket(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, c));
            ArrayList<Either<@Recorded Expression, OpAndNode>> precedingItems = currentScopes.peek().items;
            // Function calls are a special case:
            if (precedingItems.size() >= 1 && precedingItems.get(precedingItems.size() - 1).either(ExpressionUtil::isCallTarget, op -> false))
            {
                @Nullable @Recorded Expression callTarget = precedingItems.remove(precedingItems.size() - 1).<@Nullable @Recorded Expression>either(e -> e, op -> null);
                // Shouldn't ever be null:
                if (callTarget != null)
                {
                    applyBrackets = c -> new ApplyBrackets<BracketContent, Expression, Expression>()
                    {
                        @Override
                        public @NonNull @Recorded Expression apply(@NonNull BracketContent args)
                        {
                            CanonicalSpan span = CanonicalSpan.fromTo(locationRecorder.recorderFor(callTarget), c);
                            
                            if (callTarget instanceof IdentExpression)
                            {
                                StandardFunctionDefinition function = ((IdentExpression)callTarget).getFunctionDefinition();
                                if (function != null)
                                {
                                    ImmutableList<String> paramNames = function.getParamNames();
                                    String funcDocURL = ExpressionLexer.makeFuncDocURL(function);
                                    if (args.expressions.isEmpty())
                                    {
                                        // No params yet, need to add in the empty bit between brackets
                                        locationRecorder.recordEntryPrompt(c, makeParamPrompt(function.getName(), paramNames, 0, funcDocURL, span));
                                    }
                                    else
                                    {
                                        for (int i = 0; i < args.expressions.size() && i < paramNames.size(); i++)
                                        {
                                            locationRecorder.recordEntryPrompt(args.expressions.get(i), makeParamPrompt(function.getName(), paramNames, i, funcDocURL, span));
                                        }
                                    }
                                }
                            }

                            return locationRecorder.record(span, new CallExpression(callTarget, args.expressions));
                        }

                        private FXPlatformFunction<Node, StyledString> makeParamPrompt(String functionName, ImmutableList<String> paramNames, int paramIndex, String docURL, CanonicalSpan span)
                        {
                            StyledString.Builder r = new Builder();
                            r.append(functionName).append("(");
                            for (int i = 0; i < paramNames.size(); i++)
                            {
                                if (i > 0)
                                    r.append(", ");
                                if (i == paramIndex)
                                {
                                    r.append(StyledString.styled(paramNames.get(i), new StyledCSS("entry-prompt-bold")));
                                }
                                else
                                {
                                    r.append(paramNames.get(i));
                                }
                            }
                            r.append(")");
                            return toRightOf -> StyledString.concat(r.build().withStyle(new StyledCSS("entry-prompt")), StyledString.s("  "), StyledString.s("show doc").withStyle(new Clickable(null, "show-doc-link")
                            {
                                @Override
                                protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                                {
                                    try
                                    {
                                        new DocWindow(functionName + " function", docURL, toRightOf, locationRecorder.getInsertListener(), toRightOf.sceneProperty()).show();
                                    }
                                    catch (InternalException e)
                                    {
                                        Log.log(e);
                                    }
                                }
                            }));
                        }

                        @Override
                        public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
                        {
                            return apply(new BracketContent(ImmutableList.<@Recorded Expression>of(singleItem), ImmutableList.of()));
                        }
                    };
                    invalidPrefix = () -> {
                        return Utility.prependToList(callTarget, prefixKeyword.get());
                    };
                }
            }
            Function<CanonicalSpan, ApplyBrackets<BracketContent, Expression, Expression>> applyBracketsFinal = applyBrackets;
            currentScopes.push(new Scope(errorDisplayer, expect(ImmutableList.of(Keyword.CLOSE_ROUND), close -> new BracketAndNodes<>(applyBracketsFinal.apply(close), CanonicalSpan.fromTo(errorDisplayer, close), ImmutableList.of()), (bracketed, bracketEnd) -> {
                return Either.<@Recorded Expression, Terminator>left(bracketed);
            }, invalidPrefix, null, true)));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer,
                expect(ImmutableList.of(Keyword.CLOSE_SQUARE),
                    close -> new BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression>(makeList(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, close)), CanonicalSpan.fromTo(errorDisplayer, close), ImmutableList.of()),
                    (e, c) -> Either.<@Recorded Expression, Terminator>left(e), prefixKeyword, null, true)));
        }
        else if (keyword == Keyword.IF)
        {
            ImmutableList.Builder<@Recorded Expression> invalid = ImmutableList.builder();
            invalid.addAll(prefixKeyword.get());
            currentScopes.push(new Scope(errorDisplayer, expect(ImmutableList.of(Keyword.THEN, Keyword.ELSE, Keyword.ENDIF), miscBracketsFrom(errorDisplayer), (condition, conditionEnd) -> {
                invalid.add(condition);
                invalid.add(record(conditionEnd, new InvalidIdentExpression(Keyword.THEN.getContent())));
                return Either.right(expect(ImmutableList.of(Keyword.ELSE, Keyword.ENDIF), miscBracketsFrom(conditionEnd), (thenPart, thenEnd) -> {
                    invalid.add(thenPart);
                    invalid.add(record(thenEnd, new InvalidIdentExpression(Keyword.ELSE.getContent())));
                    return Either.right(expect(ImmutableList.of(Keyword.ENDIF), miscBracketsFrom(thenEnd), (elsePart, elseEnd) -> {
                        IfThenElseExpression ifThenElseExpression = new IfThenElseExpression(errorDisplayer, condition, conditionEnd, thenPart, thenEnd, elsePart, elseEnd);
                        return Either.<@Recorded Expression, Terminator>left(locationRecorder.<Expression>record(CanonicalSpan.fromTo(errorDisplayer, elseEnd), ifThenElseExpression));
                    }, invalid::build, ifPrompt(2), false));
                }, invalid::build, ifPrompt(1), false));
            }, invalid::build, ifPrompt(0), false)));
        }
        else if (keyword == Keyword.MATCH)
        {            
            currentScopes.push(new Scope(errorDisplayer, expectOneOf(errorDisplayer, ImmutableList.of(new Case(errorDisplayer)), Stream.<Supplier<@Recorded Expression>>of(() -> record(errorDisplayer, new InvalidIdentExpression(keyword.getContent()))))));
        }
        else if (keyword == Keyword.DEFINE)
        {
            currentScopes.push(new Scope(errorDisplayer, new DefineThen(errorDisplayer)));
        }
        else if (keyword == Keyword.FUNCTION)
        {
            currentScopes.push(new Scope(errorDisplayer, new FunctionParamThen(errorDisplayer)));
        }
        else
        {
            boolean consumed = false;
            // Will terminate because addTopLevelScope always returns true
            while (!consumed)
            {
                // Should be a terminator:
                Scope cur = currentScopes.pop();
                if (currentScopes.size() == 0)
                {
                    addTopLevelScope();
                }
                consumed = cur.terminator.terminate(new FetchContent<Expression, ExpressionSaver, BracketContent>()
                {
                    @Override
                    public <R extends StyledShowable> @Recorded R fetchContent(BracketAndNodes<Expression, ExpressionSaver, BracketContent, R> brackets)
                    {
                        return ExpressionSaver.this.makeExpression(cur.items, brackets, cur.openingNode.end, cur.terminator.terminatorDescription);
                    }
                }, keyword, errorDisplayer);
            }
        }
    }

    private StyledString ifPrompt(int partToBold)
    {
        ArrayList<StyledString> parts = new ArrayList<>();
        parts.add(StyledString.s(" condition "));
        parts.add(StyledString.s(" value if true "));
        parts.add(StyledString.s(" value if false "));
        
        parts.set(partToBold, parts.get(partToBold).withStyle(new StyledCSS("entry-prompt-bold")));
        return StyledString.concat(
            StyledString.s(Keyword.IF.getContent()),
            parts.get(0),
            StyledString.s(Keyword.THEN.getContent()),
            parts.get(1),
            StyledString.s(Keyword.ELSE.getContent()),
            parts.get(2),
            StyledString.s(Keyword.ENDIF.getContent())
        );
    }

    @Override
    protected BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression> unclosedBrackets(BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression> closed)
    {
        return new BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression>(new ApplyBrackets<BracketContent, Expression, Expression>()
        {
            @Override
            public @Recorded @Nullable Expression apply(@NonNull BracketContent items)
            {
                return record(closed.location, items.expressions.size() == 1 ? items.expressions.get(0) : new InvalidOperatorExpression(items.expressions));
            }
            
            @Override
            public @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        }, closed.location, ImmutableList.of(closed.applyBrackets));
    }

    private ApplyBrackets<BracketContent, Expression, Expression> tupleBracket(@UnknownInitialization(Object.class)ExpressionSaver this, EditorLocationAndErrorRecorder locationRecorder, CanonicalSpan location)
    {
        return new ApplyBrackets<BracketContent, Expression, Expression>()
        {
            @Override
            public @Recorded @Nullable Expression apply(@NonNull BracketContent items)
            {
                if (items.expressions.isEmpty())
                    return null;
                if (items.expressions.stream().allMatch(e -> e instanceof KeyValueExpression))
                {
                    boolean allOk = true;
                    ArrayList<Pair<@ExpressionIdentifier String, @Recorded Expression>> pairs = new ArrayList<>();
                    for (Expression expression : items.expressions)
                    {
                        Pair<@ExpressionIdentifier String, @Recorded Expression> p = ((KeyValueExpression)expression).extractPair();
                        if (p == null)
                        {
                            allOk = false;
                            break;
                        }
                        else
                            pairs.add(p);
                    }
                    if (allOk)
                        return locationRecorder.record(location, new RecordExpression(ImmutableList.copyOf(pairs)));
                }
                
                if (items.expressions.size() == 1)
                    return items.expressions.get(0);
                else
                {
                    ImmutableList.Builder<@Recorded Expression> invalidOps = ImmutableList.builder();
                    for (int i = 0; i < items.expressions.size(); i++)
                    {
                        @Recorded Expression expression = items.expressions.get(i);
                        if (expression instanceof KeyValueExpression)
                        {
                            KeyValueExpression keyValueExpression = (KeyValueExpression) expression;
                            invalidOps.addAll(ImmutableList.of(keyValueExpression.lhs, keyValueExpression.opAsExpression(locationRecorder), keyValueExpression.rhs));
                        }
                        else
                            invalidOps.add(expression);
                        if (i < items.commas.size())
                            invalidOps.add(locationRecorder.record(items.commas.get(i).getSecond(), new InvalidIdentExpression(items.commas.get(i).getFirst().getContent())));
                    }
                    return locationRecorder.record(location, new InvalidOperatorExpression(invalidOps.build()));
                }
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        };
    }

    @Override
    protected <R extends StyledShowable> @Recorded R makeExpression(List<Either<@Recorded Expression, OpAndNode>> content, BracketAndNodes<Expression, ExpressionSaver, BracketContent, R> brackets, @CanonicalLocation int innerContentLocation, @Nullable String terminatorDescription)
    {
        if (content.isEmpty())
        {
            @Nullable @Recorded R bracketedEmpty = brackets.applyBrackets.apply(new BracketContent(ImmutableList.of(), ImmutableList.of()));
            if (bracketedEmpty != null)
                return bracketedEmpty;
            else
            {
                if (terminatorDescription != null)
                    locationRecorder.addErrorAndFixes(new CanonicalSpan(innerContentLocation, innerContentLocation), StyledString.s("Missing expression before " + terminatorDescription), ImmutableList.of());
                return brackets.applyBrackets.applySingle(locationRecorder.record(new CanonicalSpan(innerContentLocation, innerContentLocation), new InvalidOperatorExpression(ImmutableList.of())));
            }
        }
        CanonicalSpan location = CanonicalSpan.fromTo(getLocationForEither(content.get(0)), getLocationForEither(content.get(content.size() - 1))); 

        CollectedItems collectedItems = processItems(content);

        @Nullable @Recorded R e = null;
        if (collectedItems.isValid())
        {
            ArrayList<@Recorded Expression> validOperands = collectedItems.getValidOperands();
            ArrayList<OpAndNode> validOperators = collectedItems.getValidOperators();

            ArrayList<@Recorded Expression> beforePrevCommas = new ArrayList<>();
            ArrayList<OpAndNode> prevCommas = new ArrayList<>();
            ArrayList<@Recorded Expression> sinceLastCommaOperands = new ArrayList<>();
            ArrayList<OpAndNode> sinceLastCommaOperators = new ArrayList<>();
            // Split by commas
            for (int i = 0; i < validOperands.size(); i++)
            {
                sinceLastCommaOperands.add(validOperands.get(i));
                if (i < validOperators.size() && validOperators.get(i).op == Op.COMMA)
                {
                    BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression> unbracketed = unbracketed(sinceLastCommaOperands);
                    @Recorded Expression made = makeExpressionWithOperators(OPERATORS, locationRecorder, (ImmutableList<Either<OpAndNode, @Recorded Expression>> es) -> makeInvalidOp(location, es), ImmutableList.copyOf(sinceLastCommaOperands), ImmutableList.copyOf(sinceLastCommaOperators), unbracketed);
                    if (made != null)
                        beforePrevCommas.add(made);
                    else
                        beforePrevCommas.add(makeInvalidOp(unbracketed.location, this.<OpAndNode>interleave(ImmutableList.copyOf(sinceLastCommaOperands), ImmutableList.<OpAndNode>copyOf(sinceLastCommaOperators))));
                    prevCommas.add(validOperators.get(i));
                    sinceLastCommaOperands.clear();
                    sinceLastCommaOperators.clear();
                }
                else if (i < validOperators.size())
                {
                    sinceLastCommaOperators.add(validOperators.get(i));
                }
            }
            
            // Now we need to check the operators can work together as one group:
            BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression> unbracketed = unbracketed(sinceLastCommaOperands);
            @Recorded Expression made = makeExpressionWithOperators(OPERATORS, locationRecorder, (ImmutableList<Either<OpAndNode, @Recorded Expression>> es) -> makeInvalidOp(location, es), ImmutableList.copyOf(sinceLastCommaOperands), ImmutableList.copyOf(sinceLastCommaOperators), unbracketed);
            if (made != null)
                beforePrevCommas.add(made);
            else
                beforePrevCommas.add(makeInvalidOp(unbracketed.location, this.<OpAndNode>interleave(ImmutableList.copyOf(sinceLastCommaOperands), ImmutableList.<OpAndNode>copyOf(sinceLastCommaOperators))));

            BracketContent bracketContent = new BracketContent(ImmutableList.copyOf(beforePrevCommas), Utility.mapListI(prevCommas, c -> new Pair<>(c.op, c.sourceNode)));
            e = brackets.applyBrackets.apply(bracketContent);
            if (e == null)
            {
                List<@Recorded R> possibles = new ArrayList<>();
                for (BracketAndNodes<Expression, ExpressionSaver, BracketContent, R> alternateBracket : brackets.alternateBrackets())
                {
                    @Nullable @Recorded R possible = alternateBracket.applyBrackets.apply(bracketContent);
                    if (possible != null)
                    {
                        possibles.add(possible);
                    }
                }
                if (!possibles.isEmpty())
                {
                    @Recorded R invalidOpExpression = brackets.applyBrackets.applySingle(collectedItems.makeInvalid(location, InvalidOperatorExpression::new));
                    locationRecorder.getRecorder().recordError(invalidOpExpression, StyledString.s("Surrounding brackets required"));
                    locationRecorder.getRecorder().<R>recordQuickFixes(invalidOpExpression, Utility.<@Recorded R, QuickFix<R>>mapList(possibles, fixed -> new QuickFix<R>("fix.bracketAs", invalidOpExpression, () -> fixed)));
                    return invalidOpExpression;
                }
            }
        }
        
        if (e == null)
        {
            @Recorded Expression invalid = collectedItems.makeInvalid(location, InvalidOperatorExpression::new);
            e = brackets.applyBrackets.applySingle(invalid);
        }
        
        return e;
    }

    private BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression> unbracketed(List<@Recorded Expression> operands)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, Expression, Expression>()
        {
            @Override
            public @Recorded @Nullable Expression apply(@NonNull BracketContent items)
            {
                return null;
            }

            @Override
            public @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        }, CanonicalSpan.fromTo(recorderFor(operands.get(0)), recorderFor(operands.get(operands.size() - 1))), ImmutableList.of());
    }

    @Override
    protected Expression opToInvalid(Op op)
    {
        return new InvalidIdentExpression(op.getContent());
    }

    @Override
    protected @Recorded Expression makeInvalidOp(CanonicalSpan location, ImmutableList<Either<OpAndNode, @Recorded Expression>> items)
    {
        InvalidOperatorExpression invalidOperatorExpression = new InvalidOperatorExpression(Utility.<Either<OpAndNode, @Recorded Expression>, @Recorded Expression>mapListI(items, x -> x.<@Recorded Expression>either(op -> locationRecorder.record(op.sourceNode, new InvalidIdentExpression(op.op.getContent())), y -> y)));
        return locationRecorder.record(location, invalidOperatorExpression);
    }

    @Override
    protected Expression keywordToInvalid(Keyword keyword)
    {
        return new InvalidIdentExpression(keyword.getContent());
    }

    private @Recorded Expression keywordToInvalid(Keyword keyword, CanonicalSpan location)
    {
        return locationRecorder.record(location, keywordToInvalid(keyword));
    }
    
    // Looks for a keyword, then takes the expression before the keyword and gives next step.
    private abstract class Choice
    {
        public final Keyword keyword;

        protected Choice(Keyword keyword)
        {
            this.keyword = keyword;
        }

        public abstract Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid);
    }
    
    // Looks for @case
    private class Case extends Choice
    {
        private final CanonicalSpan matchKeyword;
        // If null, we are the first case.  Otherwise we are a later case,
        // in which case we are Right with the given patterns
        private final @Nullable Pair<CanonicalSpan, Pair<@Recorded Expression, ImmutableList<Pattern>>> matchAndPatterns;
        // Previous complete clauses
        private final ImmutableList<MatchClause> previousClauses;
        
        // Looks for first @case after a @match
        public Case(CanonicalSpan matchKeyword)
        {
            super(Keyword.CASE);
            this.matchKeyword = matchKeyword;
            matchAndPatterns = null;
            previousClauses = ImmutableList.of();
        }
        
        // Matches a later @case, meaning we follow a @then and an outcome
        public Case(CanonicalSpan matchKeyword, CanonicalSpan caseLocation, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> patternsForCur)
        {
            super(Keyword.CASE);
            this.matchKeyword = matchKeyword;
            matchAndPatterns = new Pair<>(caseLocation, new Pair<>(matchFrom, patternsForCur));
            this.previousClauses = previousClauses;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            final @Recorded Expression m;
            final ImmutableList<MatchClause> newClauses;
            // If we are first case, use the expression as the match expression:
            if (matchAndPatterns == null)
            {
                m = expressionBefore;
                newClauses = previousClauses;
            }
            else
            {
                // Otherwise this is the outcome for the most recent clause:
                m = matchAndPatterns.getSecond().getFirst();
                ImmutableList<Pattern> patterns = matchAndPatterns.getSecond().getSecond();
                newClauses = Utility.appendToList(previousClauses, new MatchClause(matchAndPatterns.getFirst(), patterns, expressionBefore));
            }
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Then(matchKeyword, m, newClauses, ImmutableList.of(), node, Keyword.CASE),
                new Given(matchKeyword, m, newClauses, ImmutableList.of(), node),
                new OrCase(matchKeyword, m, newClauses, ImmutableList.of(), node, null)
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @given to add a guard
    private class Given extends Choice
    {
        private final CanonicalSpan matchKeyword;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousCases;
        private final CanonicalSpan caseLocation;

        public Given(CanonicalSpan matchKeyword, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousCases, CanonicalSpan caseLocation)
        {
            super(Keyword.GIVEN);
            this.matchKeyword = matchKeyword;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
            this.caseLocation = caseLocation;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            // Expression here is the pattern, which comes before the guard:
            return Either.right(expectOneOf(node, ImmutableList.of(
                new OrCase(matchKeyword, matchFrom, previousClauses, previousCases, caseLocation, expressionBefore),
                new Then(matchKeyword, matchFrom, previousClauses, Utility.appendToList(previousCases, new Pattern(expressionBefore, null)), caseLocation, Keyword.GIVEN)
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @orcase
    private class OrCase extends Choice
    {
        private final CanonicalSpan matchKeyword;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousCases;
        private final CanonicalSpan caseLocation;
        private final @Nullable @Recorded Expression curMatch; // if null, nothing so far, if non-null we are a guard

        private OrCase(CanonicalSpan matchKeyword, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousCases, CanonicalSpan caseLocation, @Nullable @Recorded Expression curMatch)
        {
            super(Keyword.ORCASE);
            this.matchKeyword = matchKeyword;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
            this.curMatch = curMatch;
            this.caseLocation = caseLocation;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            ImmutableList<Pattern> newCases = Utility.appendToList(previousCases, curMatch == null ?
                // We are the pattern:
                new Pattern(expressionBefore, null) :
                // We are the guard:    
                new Pattern(curMatch, expressionBefore)
            );
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Given(matchKeyword, matchFrom, previousClauses, newCases, caseLocation),
                new OrCase(matchKeyword, matchFrom, previousClauses, newCases, caseLocation, null),
                new Then(matchKeyword, matchFrom, previousClauses, newCases, caseLocation, Keyword.ORCASE)
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @then (in match expressions; if-then-else is handled separately) 
    private class Then extends Choice
    {
        private final CanonicalSpan matchKeywordNode;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousPatterns;
        private final CanonicalSpan caseLocation;
        private final Keyword precedingKeyword;
        

        // Preceding keyword may be CASE, GIVEN or ORCASE:
        private Then(CanonicalSpan matchKeywordNode, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousPatterns, CanonicalSpan caseLocation, Keyword precedingKeyword)
        {
            super(Keyword.THEN);
            this.matchKeywordNode = matchKeywordNode;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousPatterns = previousPatterns;
            this.caseLocation = caseLocation;
            this.precedingKeyword = precedingKeyword;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            final ImmutableList<Pattern> newPatterns;
            if (precedingKeyword == Keyword.GIVEN)
            {
                // The expression is a guard for the most recent pattern:
                newPatterns = Utility.appendToList(previousPatterns.subList(0, previousPatterns.size() - 1), new Pattern(previousPatterns.get(previousPatterns.size() - 1).getPattern(), expressionBefore));
            }
            else //if (precedingKeyword == Keyword.ORCASE || precedingKeyword == Keyword.CASE)
            {
                // The expression is a pattern:
                newPatterns = Utility.appendToList(previousPatterns, new Pattern(expressionBefore, null));
            }
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Case(matchKeywordNode, caseLocation, matchFrom, previousClauses, newPatterns),
                new Choice(Keyword.ENDMATCH) {
                    @Override
                    public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression lastExpression, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
                    {
                        MatchExpression matchExpression = new MatchExpression(matchKeywordNode, matchFrom, Utility.appendToList(previousClauses, new MatchClause(caseLocation, newPatterns, lastExpression)), node);
                        return Either.<@Recorded Expression, Terminator>left(locationRecorder.<Expression>record(CanonicalSpan.fromTo(matchKeywordNode, node), matchExpression));
                    }
                }
            ), prefixIfInvalid));
        }
    }

    private boolean isEqualDefinition(Expression expression)
    {
        if (expression instanceof EqualExpression)
        {
            EqualExpression equalExpression = (EqualExpression) expression;
            return equalExpression.getOperands().size() == 2 && !equalExpression.lastIsPattern();
        }
        return false;
    }


    public Terminator expectOneOf(CanonicalSpan start, ImmutableList<Choice> choices, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
    {
        return new Terminator(choices.stream().map(c -> c.keyword.getContent()).collect(Collectors.joining(" or ")))
        {
        @Override
        public boolean terminate(FetchContent<Expression, ExpressionSaver, BracketContent> makeContent, @Nullable Keyword terminator, CanonicalSpan keywordErrorDisplayer)
        {
            BracketAndNodes<Expression, ExpressionSaver, BracketContent, Expression> brackets = miscBrackets(CanonicalSpan.fromTo(start, keywordErrorDisplayer));
            
            for (Choice choice : choices)
            {
                if (choice.keyword.equals(terminator))
                {
                    // All is well:
                    @Recorded Expression expressionBefore = makeContent.fetchContent(brackets);
                    Either<@Recorded Expression, Terminator> result = choice.foundKeyword(expressionBefore, keywordErrorDisplayer, Stream.<Supplier<@Recorded Expression>>concat(prefixIfInvalid, Stream.<Supplier<@Recorded Expression>>of(() -> expressionBefore, () -> record(keywordErrorDisplayer, new InvalidIdentExpression(choice.keyword.getContent())))));
                    result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
                    return true;
                }
            }
            
            // Error!
            ImmutableList<TextQuickFix> fixes = Utility.mapListI(choices, c -> new TextQuickFix(StyledString.s("Add missing " + c.keyword.getContent()), ImmutableList.of(), keywordErrorDisplayer.lhs(), () -> new Pair<>(c.keyword.getContent(), StyledString.s(c.keyword.getContent()))));
            locationRecorder.addErrorAndFixes(keywordErrorDisplayer, StyledString.concat(StyledString.s("Missing "), choices.stream().map(e -> e.keyword.toStyledString()).collect(StyledString.joining(" or ")), StyledString.s(" before "), (terminator == null ? StyledString.s("end") : terminator.toStyledString())), fixes);
            // Important to call makeContent before adding to scope on the next line:
            ImmutableList.Builder<@Recorded Expression> items = ImmutableList.builder();
            items.addAll(prefixIfInvalid.<@Recorded Expression>map(s -> s.get()).collect(Collectors.<@Recorded Expression>toList()));
            items.add(makeContent.fetchContent(brackets));
            //if (terminator != null)
                //items.add(locationRecorder.record(keywordErrorDisplayer, new InvalidIdentExpression(terminator.getContent())));
            @Recorded InvalidOperatorExpression invalid = locationRecorder.<InvalidOperatorExpression>record(CanonicalSpan.fromTo(start, keywordErrorDisplayer), new InvalidOperatorExpression(items.build()));
            currentScopes.peek().items.add(Either.left(invalid));
            return false;
        }};
    }

    public boolean lastWasNumber()
    {
        ArrayList<Either<@Recorded Expression, OpAndNode>> curItems = currentScopes.peek().items;
        return curItems.size() >= 1 && curItems.get(curItems.size() - 1).either(e -> e instanceof NumericLiteral, op -> false);
    }

    @Override
    public void saveOperand(@UnknownIfRecorded Expression singleItem, CanonicalSpan location)
    {
        ArrayList<Either<@Recorded Expression, OpAndNode>> curItems = currentScopes.peek().items;
        if (singleItem instanceof UnitLiteralExpression && curItems.size() >= 1)
        {
            Either<@Recorded Expression, OpAndNode> recent = curItems.get(curItems.size() - 1);
            @Nullable @Recorded NumericLiteral num = recent.<@Nullable @Recorded NumericLiteral>either(e -> e instanceof NumericLiteral ? (NumericLiteral)e : null, o -> null);
            if (num != null && num.getUnitExpression() == null)
            {
                CanonicalSpan recorder = locationRecorder.recorderFor(num);
                curItems.set(curItems.size() - 1, Either.left(locationRecorder.<Expression>record(CanonicalSpan.fromTo(recorder, location), new NumericLiteral(num.getNumber(), ((UnitLiteralExpression)singleItem).getUnit()))));
                return;
            }
        }
        
        super.saveOperand(singleItem, location);
    }

    @Override
    protected @Nullable Supplier<@Recorded Expression> canBeUnary(OpAndNode operator, @Recorded Expression followingOperand)
    {
        if (ImmutableList.of(Op.ADD, Op.SUBTRACT).contains(operator.op)
                && followingOperand instanceof NumericLiteral)
        {
            @Recorded NumericLiteral numericLiteral = (NumericLiteral) followingOperand;
            if (operator.op == Op.SUBTRACT)
                return () -> record(CanonicalSpan.fromTo(operator.sourceNode, locationRecorder.recorderFor(numericLiteral)), new NumericLiteral(Utility.negate(numericLiteral.getNumber()), numericLiteral.getUnitExpression()));
            else
                return () -> numericLiteral; // No change needed for unary plus
        }
        else
            return null;
    }
    
    /**
     * Get likely types and completions for given child.  For example,
     * if the expression is column Name = _ (where the RHS
     * is the child in question) we might offer Text and most frequent values
     * of the Name column.
     *
     * The completions are not meant to be all possible values of the given
     * type (e.g. literals, available columns, etc), as that can be figured out
     * from the type regardless of context.  This is only items which make
     * particular sense in this particular context, e.g. a commonly passed argument
     * to a function.
     */
    //List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException;
    
    // Remember: earlier means more likely to be inner-bracketed.  Outer list is groups of operators
    // with equal bracketing likelihood/precedence.
    @SuppressWarnings("recorded")
    final ImmutableList<ImmutableList<OperatorExpressionInfo>> OPERATORS = ImmutableList.<ImmutableList<OperatorExpressionInfo>>of(
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.FIELD_ACCESS, (lhs, _n, rhs) -> FieldAccessExpression.fromBinary(lhs, rhs))
        ),
            
            
        // Raise does come above arithmetic, because I think it is more likely that 1 * 2 ^ 3 is actually 1 * (2 ^ 3)
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.RAISE, (lhs, _n, rhs) -> new RaiseExpression(lhs, rhs))
        ),

        // Arithmetic operators are all one group.  I know we could separate +- from */, but if you see
        // an expression like 1 + 2 * 3, I'm not sure either bracketing is obviously more likely than the other.
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(Op.ADD, Op.SUBTRACT), ExpressionSaver::makeAddSubtract),
            new OperatorExpressionInfo(ImmutableList.of(Op.MULTIPLY), ExpressionSaver::makeTimes),
            new OperatorExpressionInfo(Op.DIVIDE, (lhs, _n, rhs) -> new DivideExpression(lhs, rhs))
        ),

        // String concatenation lower than arithmetic.  If you write "val: (" ; 1 * 2; ")" then what you meant
        // is "val: (" ; to.string(1 * 2); ")" which requires an extra function call, but bracketing the arithmetic
        // will be the first step, and much more likely than ("val: (" ; 1) * (2; ")")
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(Op.STRING_CONCAT), ExpressionSaver::makeStringConcat)
        ),

        // It's moot really whether this is before or after string concat, but feels odd putting them in same group:
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.PLUS_MINUS, (lhs, _n, rhs) -> new PlusMinusPatternExpression(lhs, rhs))
        ),

        // Equality and comparison operators:
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(Op.EQUALS), ExpressionSaver::makeEqual),
            new OperatorExpressionInfo(Op.EQUALS_PATTERN, (lhs, _n, rhs) -> new EqualExpression(ImmutableList.of(lhs, rhs), true)),
            new OperatorExpressionInfo(Op.NOT_EQUAL, (lhs, _n, rhs) -> new NotEqualExpression(lhs, rhs)),
            new OperatorExpressionInfo(ImmutableList.of(Op.LESS_THAN, Op.LESS_THAN_OR_EQUAL), ExpressionSaver::makeComparisonLess),
            new OperatorExpressionInfo(ImmutableList.of(Op.GREATER_THAN, Op.GREATER_THAN_OR_EQUAL), ExpressionSaver::makeComparisonGreater)
        ),

        // Boolean and, or expressions come near-last.  If you see a = b & c = d, it's much more likely you wanted (a = b) & (c = d) than
        // a = (b & c) = d.
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(Op.AND), ExpressionSaver::makeAnd),
            new OperatorExpressionInfo(ImmutableList.of(Op.OR), ExpressionSaver::makeOr)
        ),

        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.HAS_TYPE, (lhs, opLoc, rhs) -> makeHasType(lhs, rhs))
        ),
        
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.COLON, (lhs, opLoc, rhs) -> new KeyValueExpression(lhs, new OpAndNode(Op.COLON, opLoc), rhs))
        ),

        // But the very last is the comma separator.  If you see (a & b, c | d), almost certain that you want a tuple
        // like that, rather than a & (b, c) | d.  Especially since tuples can't be fed to any binary operators besides comparison!
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.COMMA, (lhs, _n, rhs) -> /* Dummy, see below: */ lhs)
            {
                @Override
                public OperatorSection makeOperatorSection(EditorLocationAndErrorRecorder locationRecorder, int operatorSetPrecedence, OpAndNode initialOperator, int initialIndex)
                {
                    return new NaryOperatorSection(locationRecorder, operators, operatorSetPrecedence, new MakeNary<Expression, ExpressionSaver, Op, BracketContent>()
                    {
                        @Override
                        public <R extends StyledShowable> @Nullable @Recorded R makeNary(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> ops, BracketAndNodes<Expression, ExpressionSaver, BracketContent, R> brackets, EditorLocationAndErrorRecorder locationRecorder)
                        {
                            return brackets.applyBrackets.apply(new BracketContent(args, ImmutableList.copyOf(ops)));
                        }
                    }, initialIndex, initialOperator);

                }
            }
        )
    );

    private static Expression makeAddSubtract(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> ops)
    {
        return new AddSubtractExpression(args, Utility.mapList(ops, op -> op.getFirst().equals(Op.ADD) ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT));
    }

    private static Expression makeTimes(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new TimesExpression(args);
    }

    private static Expression makeStringConcat(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new StringConcatExpression(args);
    }

    private static Expression makeEqual(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> ops)
    {
        return new EqualExpression(args, ops.get(ops.size() - 1).getFirst() == Op.EQUALS_PATTERN);
    }

    private static Expression makeComparisonLess(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> ops)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.getFirst().equals(Op.LESS_THAN) ? ComparisonOperator.LESS_THAN : ComparisonOperator.LESS_THAN_OR_EQUAL_TO));
    }

    private static Expression makeComparisonGreater(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> ops)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.getFirst().equals(Op.GREATER_THAN) ? ComparisonOperator.GREATER_THAN : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO));
    }

    private static Expression makeAnd(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new AndExpression(args);
    }

    private static Expression makeOr(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new OrExpression(args);
    }
    
    private static Expression makeHasType(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        return lhs.visit(new ExpressionVisitorFlat<Expression>()
        {
            @Override
            public Expression ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                if (namespace == null || namespace.equals("var"))
                    return new HasTypeExpression(idents.get(idents.size() - 1), rhs);
                return makeDef(self);
            }

            @Override
            @SuppressWarnings("recorded")
            protected Expression makeDef(Expression expression)
            {
                return new InvalidOperatorExpression(ImmutableList.of(lhs, new InvalidIdentExpression("::"), rhs));
            }
        });
    }

    @Override
    protected CanonicalSpan recorderFor(@Recorded Expression expression)
    {
        return locationRecorder.recorderFor(expression);
    }

    @Override
    protected ImmutableList<TextQuickFix> fixesForAdjacentOperands(@Recorded Expression first, @Recorded Expression second)
    {
        // We look for things where people have tried to use
        // C/Java style array indexing of lists.  Because it's invalid
        // we can't know types, so we use syntax.
        // For the list we look for column references
        // (most likely item by far to be a list).
        /* TODO
        if (first instanceof IdentExpression && second instanceof ArrayExpression)
        {
            ArrayExpression arrayExpression = (ArrayExpression) second;
            
            if (arrayExpression.getElements().size() == 1)
            {
                first.visit(TODO);
                CanonicalSpan location = new CanonicalSpan(locationRecorder.recorderFor(first).start, locationRecorder.recorderFor(second).end);
                
                return ImmutableList.of(new <Expression>TextQuickFix("fix.useElement",location, () -> {
                    Expression callElement = new CallExpression(functionLookup, "element", first, arrayExpression.getElements().get(0));
                    return new Pair<>(callElement.save(SaveDestination.EDITOR, BracketedStatus.DONT_NEED_BRACKETS, null, TableAndColumnRenames.EMPTY), callElement.toStyledString());
                }));
            }
        }
        */
        
        return super.fixesForAdjacentOperands(first, second);
    }

    public ImmutableMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>> getDisplayFor(@CanonicalLocation int canonIndex, Node toRightOf)
    {
        return locationRecorder.getDisplayFor(canonIndex, toRightOf);
    }

    @OnThread(Tag.Any)
    private static class KeyValueExpression extends Expression
    {
        private final @Recorded Expression lhs;
        private final OpAndNode colon;
        private final @Recorded Expression rhs;

        public KeyValueExpression(@Recorded Expression lhs, OpAndNode opAndNode, @Recorded Expression rhs)
        {
            this.lhs = lhs;
            this.colon = opAndNode;
            this.rhs = rhs;
        }

        @Override
        public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
        {
            // If we are type-checked directly, we are in the wrong place:
            onError.recordError(this, StyledString.s("Colon not a valid operator here"));
            return null;
        }

        @Override
        public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws InternalException
        {
            // If we are executed directly, we are in the wrong place:
            throw new InternalException("Executing KeyValueExpression despite failed typecheck");
        }

        @SuppressWarnings({"nullness", "unchecked"})
        @Override
        public <T> T visit(ExpressionVisitor<T> visitor)
        {
            // Bit of a hack all round:
            if (visitor instanceof ExpressionVisitorStream)
                return (T)Stream.<Object>of();
            
            Log.logStackTrace("KeyValueExpression.visit");
            return null;
        }

        @Override
        public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
        {
            return lhs.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames) + ": " + rhs.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames);
        }

        @Override
        public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
        {
            return Stream.of();
        }

        @Override
        public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
        {
            return null;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            return false;
        }

        @Override
        public int hashCode()
        {
            return 0;
        }

        @Override
        protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
        {
            return StyledString.concat(lhs.toStyledString(), StyledString.s(" : "), rhs.toStyledString());
        }

        @SuppressWarnings("recorded")
        @Override
        public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            if (this == toReplace)
                return replaceWith;
            else
                return new KeyValueExpression(lhs.replaceSubExpression(toReplace, replaceWith), colon, rhs.replaceSubExpression(toReplace, replaceWith));
        }

        public @Nullable Pair<@ExpressionIdentifier String, @Recorded Expression> extractPair()
        {
            @ExpressionIdentifier String lhsIdent = lhs.visit(new ExpressionVisitorFlat<@Nullable @ExpressionIdentifier String>()
            {
                @Override
                protected @Nullable @ExpressionIdentifier String makeDef(Expression expression)
                {
                    return null;
                }

                @Override
                public @Nullable @ExpressionIdentifier String ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
                {
                    if (namespace == null && idents.size() == 1)
                        return idents.get(0);
                    return super.ident(self, namespace, idents, isVariable);
                }
            });
            
            if (lhsIdent != null)
                return new Pair<>(lhsIdent, rhs);
            else
                return null;
        }

        @OnThread(Tag.FXPlatform)
        public @Recorded Expression opAsExpression(EditorLocationAndErrorRecorder locationRecorder)
        {
            return locationRecorder.record(colon.sourceNode, new InvalidIdentExpression(colon.op.getContent()));
        }
    }
    
    private class FunctionParamThen extends Terminator
    {
        private final CanonicalSpan initialFunction;

        public FunctionParamThen(CanonicalSpan initialFunction)
        {
            super("@then");
            this.initialFunction = initialFunction;
        }

        @Override
        public boolean terminate(FetchContent<Expression, ExpressionSaver, BracketContent> makeContent, @Nullable Keyword terminator, CanonicalSpan keywordErrorDisplayer)
        {
            final ArrayList<@Recorded Expression> itemsIfInvalid = new ArrayList<>();
            @SuppressWarnings("recorded") // We can't actually record BracketContent
            BracketContent bracketContent = makeContent.fetchContent(new BracketAndNodes<Expression, ExpressionSaver, BracketContent, BracketContent>(new ApplyBrackets<BracketContent, Expression, BracketContent>()
            {
                @Override
                public @Recorded @Nullable BracketContent apply(@NonNull BracketContent items)
                {
                    return items;
                }

                @Override
                public @Recorded BracketContent applySingle(@NonNull @Recorded Expression singleItem)
                {
                    return new BracketContent(ImmutableList.of(singleItem), ImmutableList.of());
                }
            }, keywordErrorDisplayer, ImmutableList.of()));

            boolean foundThen = (terminator == Keyword.THEN);

            @Nullable ArrayList<@Recorded Expression> params = foundThen ? new ArrayList<>() : null;
            for (int i = 0; i < bracketContent.expressions.size(); i++)
            {
                @Recorded Expression e = bracketContent.expressions.get(i);
                itemsIfInvalid.add(e);
                if (i < bracketContent.commas.size())
                    itemsIfInvalid.add(locationRecorder.record(bracketContent.commas.get(i).getSecond(), new InvalidIdentExpression(bracketContent.commas.get(i).getFirst().getContent())));
                if (params != null)
                    params.add(e);
            }

            if (foundThen)
                itemsIfInvalid.add(keywordToInvalid(Keyword.THEN, keywordErrorDisplayer));

            if (params != null)
            {
                ImmutableList<@Recorded Expression> args = ImmutableList.<@Recorded Expression>copyOf(params);
                currentScopes.push(new Scope(keywordErrorDisplayer, expect(ImmutableList.of(Keyword.ENDFUNCTION), s -> expectSingle(locationRecorder, s), (e, s) -> Either.<@Recorded Expression, Terminator>left(locationRecorder.<Expression>record(new CanonicalSpan(initialFunction.start, s.end), new LambdaExpression(args, e))), () -> ImmutableList.copyOf(itemsIfInvalid), null, false)));
            }
            else
            {
                // Invalid
                currentScopes.peek().items.add(Either.<@Recorded Expression, OpAndNode>left(locationRecorder.<Expression>record(new CanonicalSpan(initialFunction.start, itemsIfInvalid.isEmpty() ? initialFunction.end : locationRecorder.recorderFor(itemsIfInvalid.get(itemsIfInvalid.size() - 1)).end), new InvalidOperatorExpression(Utility.<@Recorded Expression>prependToList(keywordToInvalid(Keyword.FUNCTION, initialFunction), ImmutableList.<@Recorded Expression>copyOf(itemsIfInvalid))))));
            }
            return foundThen;
        }
    }

    private class DefineThen extends Terminator
    {
        private final CanonicalSpan initialDefine;

        public DefineThen(CanonicalSpan initialDefine)
        {
            super("@then");
            this.initialDefine = initialDefine;
        }

        @Override
        public boolean terminate(FetchContent<Expression, ExpressionSaver, BracketContent> makeContent, @Nullable Keyword terminator, CanonicalSpan keywordErrorDisplayer)
        {
            final ArrayList<@Recorded Expression> itemsIfInvalid = new ArrayList<>();
            itemsIfInvalid.add(keywordToInvalid(Keyword.DEFINE, initialDefine));
            @SuppressWarnings("recorded") // We can't actually record BracketContent
            BracketContent bracketContent = makeContent.fetchContent(new BracketAndNodes<Expression, ExpressionSaver, BracketContent, BracketContent>(new ApplyBrackets<BracketContent, Expression, BracketContent>()
            {
                @Override
                public @Recorded @Nullable BracketContent apply(@NonNull BracketContent items)
                {
                    return items;
                }

                @Override
                public @Recorded BracketContent applySingle(@NonNull @Recorded Expression singleItem)
                {
                    return new BracketContent(ImmutableList.of(singleItem), ImmutableList.of());
                }
            }, keywordErrorDisplayer, ImmutableList.of()));
            
            boolean foundThen = (terminator == Keyword.THEN);
            
            @Nullable ArrayList<DefineItem> definitions = foundThen ? new ArrayList<>() : null;
            for (int i = 0; i < bracketContent.expressions.size(); i++)
            {
                @Recorded Expression e = bracketContent.expressions.get(i);
                itemsIfInvalid.add(e);
                if (i < bracketContent.commas.size())
                    itemsIfInvalid.add(locationRecorder.record(bracketContent.commas.get(i).getSecond(), new InvalidIdentExpression(bracketContent.commas.get(i).getFirst().getContent())));
                if (definitions != null)
                    definitions = e.visit(new VisitDefinitions(definitions, i < bracketContent.commas.size() ? bracketContent.commas.get(i).getSecond() : keywordErrorDisplayer));
            }

            if (foundThen)
                itemsIfInvalid.add(keywordToInvalid(Keyword.THEN, keywordErrorDisplayer));
            if (definitions != null)
            {
                ImmutableList<DefineItem> defs = ImmutableList.<DefineItem>copyOf(definitions);
                
                currentScopes.push(new Scope(keywordErrorDisplayer, expect(ImmutableList.of(Keyword.ENDDEFINE), s -> expectSingle(locationRecorder, s), (e, s) -> {
                    DefineExpression defineExpression = new DefineExpression(initialDefine, defs, e, s);
                    return Either.<@Recorded Expression, Terminator>left(locationRecorder.<Expression>record(new CanonicalSpan(initialDefine.start, s.end), defineExpression));
                }, () -> ImmutableList.copyOf(itemsIfInvalid), null, false)));
            }
            else
            {
                // Invalid
                currentScopes.peek().items.add(Either.<@Recorded Expression, OpAndNode>left(locationRecorder.<Expression>record(new CanonicalSpan(initialDefine.start, itemsIfInvalid.isEmpty() ? initialDefine.end : locationRecorder.recorderFor(itemsIfInvalid.get(itemsIfInvalid.size() - 1)).end), new InvalidOperatorExpression(ImmutableList.<@Recorded Expression>copyOf(itemsIfInvalid)))));
            }
            return foundThen;
        }

        // Returns null or the original array reference passed to constructor
        @OnThread(Tag.Any)
        private class VisitDefinitions extends ExpressionVisitorFlat<@Nullable ArrayList<DefineItem>>
        {
            private final ArrayList<DefineItem> definitions;
            private final CanonicalSpan terminatorLocation; // of either comma or @then

            public VisitDefinitions(ArrayList<DefineItem> definitions, CanonicalSpan terminatorLocation)
            {
                this.definitions = definitions;
                this.terminatorLocation = terminatorLocation;
            }

            @Override
            protected @Nullable ArrayList<DefineItem> makeDef(Expression expression)
            {
                return null;
            }

            @Override
            public @Nullable ArrayList<DefineItem> hasType(@Recorded HasTypeExpression self, @ExpressionIdentifier String lhsVar, @Recorded Expression rhsType)
            {
                definitions.add(new DefineItem(Either.left(self), terminatorLocation));
                return definitions;
            }

            @Override
            public @Nullable ArrayList<DefineItem> equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions, boolean lastIsPattern)
            {
                if (expressions.size() == 2 && !lastIsPattern)
                {
                    definitions.add(new DefineItem(Either.right(new Definition(expressions.get(0), expressions.get(1))), terminatorLocation));
                    return definitions;
                }
                else
                    return null;
            }
        }
    }
}
