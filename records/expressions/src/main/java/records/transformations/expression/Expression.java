package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.scene.text.Text;
import log.Log;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.ListExDTV;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.AddSubtractExpressionContext;
import records.grammar.ExpressionParser.AndExpressionContext;
import records.grammar.ExpressionParser.AnyContext;
import records.grammar.ExpressionParser.ArrayExpressionContext;
import records.grammar.ExpressionParser.BooleanLiteralContext;
import records.grammar.ExpressionParser.BracketedExpressionContext;
import records.grammar.ExpressionParser.CallExpressionContext;
import records.grammar.ExpressionParser.ColumnRefContext;
import records.grammar.ExpressionParser.ConstructorContext;
import records.grammar.ExpressionParser.CustomLiteralExpressionContext;
import records.grammar.ExpressionParser.DivideExpressionContext;
import records.grammar.ExpressionParser.ExpressionContext;
import records.grammar.ExpressionParser.GreaterThanExpressionContext;
import records.grammar.ExpressionParser.IfThenElseExpressionContext;
import records.grammar.ExpressionParser.ImplicitLambdaParamContext;
import records.grammar.ExpressionParser.InvalidOpExpressionContext;
import records.grammar.ExpressionParser.InvalidOpItemContext;
import records.grammar.ExpressionParser.LessThanExpressionContext;
import records.grammar.ExpressionParser.MatchClauseContext;
import records.grammar.ExpressionParser.MatchContext;
import records.grammar.ExpressionParser.NotEqualExpressionContext;
import records.grammar.ExpressionParser.NumericLiteralContext;
import records.grammar.ExpressionParser.OrExpressionContext;
import records.grammar.ExpressionParser.PatternContext;
import records.grammar.ExpressionParser.PlusMinusPatternContext;
import records.grammar.ExpressionParser.RaisedExpressionContext;
import records.grammar.ExpressionParser.StandardFunctionContext;
import records.grammar.ExpressionParser.StringConcatExpressionContext;
import records.grammar.ExpressionParser.StringLiteralContext;
import records.grammar.ExpressionParser.TableIdContext;
import records.grammar.ExpressionParser.TimesExpressionContext;
import records.grammar.ExpressionParser.TopLevelExpressionContext;
import records.grammar.ExpressionParser.TupleExpressionContext;
import records.grammar.ExpressionParser.VarRefContext;
import records.grammar.ExpressionParserBaseVisitor;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.explanation.ExplanationLocation;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.function.ValueFunction;
import records.transformations.expression.function.ValueFunction.RecordedFunctionResult;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.typeExp.ExpressionBase;
import records.typeExp.TypeClassRequirements;
import records.typeExp.TypeExp;
import styled.StyledCSS;
import styled.StyledShowable;
import styled.StyledString;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression extends ExpressionBase implements StyledShowable, Replaceable<Expression>, Explanation.ExplanationSource
{
    public static final int MAX_STRING_SOLVER_LENGTH = 8;

    public static interface ColumnLookup
    {
        public static class FoundColumn
        {
            public final TableId tableId;
            public final DataTypeValue dataTypeValue;
            public final @Nullable StyledString information;

            public FoundColumn(TableId tableId, DataTypeValue dataTypeValue, @Nullable StyledString information)
            {
                this.tableId = tableId;
                this.dataTypeValue = dataTypeValue;
                this.information = information;
            }
        }
        
        // If you pass null for table, you get the default table (or null if none)
        // If no such table/column is found, null is returned
        // If columnReferenceType is CORRESPONDING_ROW, called getCollapsed
        // with row number should get corresponding value.  If it is
        // WHOLE_COLUMN then passing 0 to getValue should get whole column as ListEx.
        public @Nullable FoundColumn getColumn(@Nullable TableId tableId, ColumnId columnId, ColumnReferenceType columnReferenceType);

        // This is really for the editor, but it doesn't rely on any GUI
        // functionality so can be here:
        public Stream<ColumnReference> getAvailableColumnReferences();

        /**
         * Called when the column is clicked, to find out
         * which column reference to insert into the editor.  If the column is not clickable, will return empty stream.
         */
        public Stream<ColumnReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId);
    }
    
    // PATTERN infects EXPRESSION: any bit of PATTERN in an inner expression
    // causes the outer expression to either throw an error, or
    // become a PATTERN itself
    public static enum ExpressionKind { EXPRESSION, PATTERN;

        // PATTERN if this or argument is PATTERN.  Otherwise EXPRESSION
        public ExpressionKind or(ExpressionKind kind)
        {
            if (this == PATTERN || kind == PATTERN)
                return PATTERN;
            else
                return EXPRESSION;
        }
    }

    /**
     * If something is a plain expression, then all we need to know is the TypeExp.
     * 
     * If something is a pattern, we then need to know:
     *  - Its type (as above)
     *  - The resulting type state (since a pattern may modify it)
     *  - The types for which we need Equatable when this is use in a pattern match.
     *  
     *  For example, if you have the tuple:
     *    (3, @anything, existingVar, $newVar)
     *  
     *  This is a pattern.  Its type is (Num, Any, existingVar's type, Any),
     *  its resulting state assigns a type for newVar, and we require Equatable
     *  on Num and existingVar's type.
     *  
     *  This can then validly be matched against:
     *    (3, (? + 1), value of existingVar's type, (? - 1))
     *  Provided existingVar's type is Equatable, without needing Equatable for the functions.
     */

    public static class CheckedExp
    {
        public final @Recorded TypeExp typeExp;
        public final ExpressionKind expressionKind;
        public final TypeState typeState;
        // We could actually apply these immediately, because it's disallowed
        // to have a pattern outside a pattern match.  But then any creation
        // of a pattern would get that error, plus a whole load of
        // Equatable failures without any equality check in sight.
        // So we store the type that need Equatable here, and apply them once we see the equality match.
        // Always empty if type is EXPRESSION
        private final ImmutableList<TypeExp> equalityRequirements;

        private CheckedExp(@Recorded TypeExp typeExp, TypeState typeState, ExpressionKind expressionKind, ImmutableList<TypeExp> equalityRequirements)
        {
            this.typeExp = typeExp;
            this.typeState = typeState;
            this.expressionKind = expressionKind;
            this.equalityRequirements = equalityRequirements;
        }

        public CheckedExp(@Recorded TypeExp typeExp, TypeState typeState, ExpressionKind expressionKind)
        {
            this(typeExp, typeState, expressionKind, ImmutableList.of());
        }
        
        // Used for things like tuple members, list members.
        // If any of them are patterns, equality constraints are applied to
        // any non-pattern items.  The type state used is the given argument.
        public static CheckedExp combineStructural(@Recorded TypeExp typeExp, TypeState typeState, ImmutableList<CheckedExp> items)
        {
            boolean anyArePattern = items.stream().anyMatch(c -> c.expressionKind == ExpressionKind.PATTERN);
            ExpressionKind kind = anyArePattern ? ExpressionKind.PATTERN : ExpressionKind.EXPRESSION;
            ImmutableList<TypeExp> reqs;
            if (anyArePattern)
                reqs = items.stream().filter(c -> c.expressionKind != ExpressionKind.PATTERN)
                        .map(c -> c.typeExp)
                        .collect(ImmutableList.<TypeExp>toImmutableList());
            else
                reqs = ImmutableList.of();
            return new CheckedExp(typeExp, typeState, kind, reqs);
        }

        /**
         * Make sure this item is equatable.
         * @param onlyIfPattern Only apply the restrictions if this is a pattern
         */
        public void requireEquatable(boolean onlyIfPattern)
        {
            TypeClassRequirements equatable = TypeClassRequirements.require("Equatable", "<match>");
            if (expressionKind == ExpressionKind.PATTERN)
            {
                for (TypeExp t : equalityRequirements)
                {
                    
                    t.requireTypeClasses(equatable);
                }
            }
            else if (expressionKind == ExpressionKind.EXPRESSION && !onlyIfPattern)
            {
                typeExp.requireTypeClasses(equatable);
            }
        }

        // If the argument is null, just return this.
        // If non-null, check the item is an expression, and then apply the operator to our type
        public CheckedExp applyToType(@Nullable UnaryOperator<@Recorded TypeExp> changeType)
        {
            if (changeType == null)
                return this;
            return new CheckedExp(changeType.apply(typeExp), typeState, expressionKind, equalityRequirements);
        }
    }
    
    public static enum LocationInfo
    {
        // Multiply or divide:
        UNIT_MODIFYING,
        // Comparison, add or compare
        UNIT_CONSTRAINED,
        // All the rest:
        UNIT_DEFAULT
    }
    
    // Checks that all used variable names and column references are defined,
    // and that types check.  Return null if any problems.  Can be either EXPRESSION or PATTERN
    public abstract @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException;
    
    // Calls check, but makes sure it is an EXPRESSION
    public final @Nullable TypeExp checkExpression(ColumnLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable CheckedExp check = check(dataLookup, typeState, LocationInfo.UNIT_DEFAULT, onError);
        if (check == null)
            return null;
        if (check.expressionKind != ExpressionKind.EXPRESSION)
        {
            onError.recordError(this, StyledString.s("Pattern is not valid here"));
            return null;
        }
        return check.typeExp;
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult result(@Value Object value, EvaluateState state)
    {
        return result(value, state, ImmutableList.of());
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult result(@Value Object value, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations)
    {
        return result(value, state, childrenForExplanations, ImmutableList.of(), false);
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult result(@Value Object value, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations, ImmutableList<ExplanationLocation> usedLocations, boolean skipChildrenIfTrivial)
    {
        return explanation(value, ExecutionType.VALUE, state, childrenForExplanations, usedLocations, skipChildrenIfTrivial);
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult explanation(@Value Object value, ExecutionType executionType, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations, ImmutableList<ExplanationLocation> usedLocations, boolean skipChildrenIfTrivial)
    {
        if (!state.recordExplanation())
        {
            return new ValueResult(value, state)
            {
                @Override
                public Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType) throws InternalException
                {
                    throw new InternalException("Fetching explanation but did not record explanation");
                }
            };
        }
        
        return new ValueResult(value, state)
        {
            @Override
            public Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType)
            {
                return new Explanation(Expression.this, overrideExecutionType != null ? overrideExecutionType : executionType, evaluateState, value, usedLocations)
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public @Nullable StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
                    {
                        return Expression.this.describe(value,this.executionType, evaluateState, hyperlinkLocation, expressionStyler, Utility.concatI(usedLocations, extraLocations), skipIfTrivial);
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
                    {
                        return Utility.mapListInt(childrenForExplanations, e -> e.makeExplanation(null));
                    }

                    @Override
                    public boolean excludeChildrenIfTrivial()
                    {
                        return skipChildrenIfTrivial;
                    }
                };
            }

            @Override
            public ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
            {
                return usedLocations;
            }
        };
    }

    @OnThread(Tag.Simulation)
    @Nullable
    private StyledString describe(@Value Object value, ExecutionType executionType, EvaluateState evaluateState,  Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> usedLocations, boolean skipIfTrivial) throws UserException, InternalException
    {
        // Don't bother explaining literals, or trivial if we are skipping trivial:
        if (Expression.this.hideFromExplanation(skipIfTrivial))
            return null;
        
        // Or things which result in functions, as output won't be useful:
        if (value instanceof ValueFunction)
            return null;

        StyledString using = usedLocations.isEmpty() ? StyledString.s("") : StyledString.concat(StyledString.s(", using "), usedLocations.stream().filter(l -> l.rowIndex.isPresent()).map(hyperlinkLocation).collect(StyledString.joining(", ")));

        if (executionType == ExecutionType.MATCH && value instanceof Boolean)
        {
            return StyledString.concat(Expression.this.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), StyledString.s(((Boolean)value) ? " matched" : " did not match"), using);
        }
        else
        {
            return StyledString.concat(Expression.this.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), StyledString.s(" was "), StyledString.s(DataTypeUtility.valueToString(evaluateState.getTypeFor(Expression.this, executionType), value, null)), using);
        }
    }

    @OnThread(Tag.Simulation)
    protected ValueResult result(EvaluateState state, RecordedFunctionResult recordedFunctionResult)
    {
        return new ValueResult(recordedFunctionResult.result, state)
        {
            @Override
            public Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType)
            {
                return new Explanation(Expression.this, overrideExecutionType != null ? overrideExecutionType : ExecutionType.VALUE, evaluateState, value, recordedFunctionResult.usedLocations)
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public @Nullable StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
                    {
                        return Expression.this.describe(value, this.executionType, evaluateState, hyperlinkLocation, expressionStyler, Utility.concatI(recordedFunctionResult.usedLocations, extraLocations), skipIfTrivial);
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
                    {
                        return recordedFunctionResult.childExplanations;
                    }
                };
            }

            @Override
            public ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
            {
                return recordedFunctionResult.usedLocations;
            }
        };
    }

    /**
     * For convenience this is a non-static class, as the Expression reference
     * is used by the default implementation of makeExplanation.
     * If you override makeExplanation, it doesn't matter which Expression
     * instance is used to construct the object.
     */
    @OnThread(Tag.Simulation)
    public abstract static class ValueResult
    {
        public final @Value Object value;
        // State after execution:
        public final EvaluateState evaluateState;
        
        protected ValueResult(@Value Object value, EvaluateState state)
        {
            this.value = value;
            this.evaluateState = state;
        }

        public abstract Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType) throws InternalException;

        // Locations used directly by this result, not including
        // locations from child explanations
        public ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
        {
            return ImmutableList.of();
        }
    }
    
    /**
     * Gets the value for this expression at the given evaluation state
     */
    @OnThread(Tag.Simulation)
    public abstract ValueResult calculateValue(EvaluateState state) throws UserException, InternalException;

    // Note that there will be duplicates if referred to multiple times
    public abstract Stream<ColumnReference> allColumnReferences();

    // Note that there will be duplicates if referred to multiple times
    @Override
    public abstract Stream<String> allVariableReferences();

    /**
     * 
     * @param structured If true, include full keywords for things like invalid, function calls etc.
     *                   If false, give back string which could be entered direct in the GUI.
     * @param surround
     * @param renames
     * @return
     */
    public abstract String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames);

    public static Expression parse(@Nullable String keyword, String src, TypeManager typeManager, FunctionLookup functionLookup) throws UserException, InternalException
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
            return Utility.parseAsOne(src.replace("\r", "").replace("\n", ""), ExpressionLexer::new, ExpressionParser::new, p ->
            {
                return new CompileExpression(typeManager, functionLookup).visit(p.completeExpression().topLevelExpression());
            });
        }
        catch (RuntimeException e)
        {
            throw new UserException("Problem parsing expression \"" + src + "\"", e);
        }
    }

    @Pure
    public Optional<Rational> constantFold()
    {
        return Optional.empty();
    }
    
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        return false;
    }
    
    // Vaguely similar to getValue, but instead checks if the expression matches the given value
    // For many expressions, matching means equality, but if a new-variable item is involved
    // it's not necessarily plain equality.
    // Given that the expression has type-checked, you can assume the value is of the same type
    // as the current expression (and throw an InternalException if not)
    // If you override this, you should also override checkAsPattern
    // If there is a match, returns result with true value.  If no match, returns a result with false value.
    @OnThread(Tag.Simulation)
    public ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        ValueResult ourValue = calculateValue(state);
        return explanation(DataTypeUtility.value(Utility.compareValues(value, ourValue.value) == 0), ExecutionType.MATCH, state, ImmutableList.of(ourValue), ourValue.getDirectlyUsedLocations(), false);
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
            return new ColumnReference(tableName, columnName, ctx.columnRefType().WHOLECOLUMN() != null ? ColumnReferenceType.WHOLE_COLUMN : ColumnReferenceType.CORRESPONDING_ROW);
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
            return new StringLiteral(ctx.getText());
        }

        @Override
        public Expression visitBooleanLiteral(BooleanLiteralContext ctx)
        {
            return new BooleanLiteral(Boolean.valueOf(ctx.getText()));
        }

        @Override
        public Expression visitConstructor(ConstructorContext ctx)
        {
            return new ConstructorExpression(typeManager, ctx.typeName() == null ? null : IdentifierUtility.fromParsed(ctx.typeName().ident()), ctx.constructorName().getText());
        }

        @Override
        public Expression visitNotEqualExpression(NotEqualExpressionContext ctx)
        {
            return new NotEqualExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitEqualExpression(ExpressionParser.EqualExpressionContext ctx)
        {
            return new EqualExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
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
            return new IfThenElseExpression(visitTopLevelExpression(ctx.topLevelExpression(0)), visitTopLevelExpression(ctx.topLevelExpression(1)), visitTopLevelExpression(ctx.topLevelExpression(2)));
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
            String functionName = ctx.ident().getText();
            @Nullable StandardFunctionDefinition functionDefinition = null;
            try
            {
                functionDefinition = functionLookup.lookup(functionName);
            }
            catch (InternalException e)
            {
                Utility.report(e);
                // Carry on, but function is null so will count as unknown
            }
            if (functionDefinition == null)
            {
                return InvalidIdentExpression.identOrUnfinished(functionName);
            }
            else
            {
                return new StandardFunction(functionDefinition);
            }
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
                clauses.add(new MatchClause(patterns.build(), visitTopLevelExpression(matchClauseContext.topLevelExpression())));
            }
            return new MatchExpression(visitTopLevelExpression(ctx.topLevelExpression()), clauses.build());
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
        public Expression visitTupleExpression(TupleExpressionContext ctx)
        {
            return new TupleExpression(Utility.<TopLevelExpressionContext, Expression>mapListI(ctx.topLevelExpression(), c -> visitTopLevelExpression(c)));
        }

        @Override
        public Expression visitBracketedExpression(BracketedExpressionContext ctx)
        {
            return visitTopLevelExpression(ctx.topLevelExpression());
        }

        @Override
        public Expression visitVarRef(VarRefContext ctx)
        {
            return new IdentExpression(IdentifierUtility.fromParsed(ctx.ident()));
        }

        @Override
        public Expression visitUnfinished(ExpressionParser.UnfinishedContext ctx)
        {
            return new InvalidIdentExpression(ctx.STRING().getText());
        }

        @Override
        public Expression visitNewVariable(ExpressionParser.NewVariableContext ctx)
        {
            return new VarDeclExpression(IdentifierUtility.fromParsed(ctx.ident()));
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

    @Override
    public String toString()
    {
        return save(true, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY);
    }

    // This is like a zipper.  It gets a list of all expressions in the tree (i.e. all nodes)
    // and returns them, along with a function.  If you pass that function a replacement,
    // it will build you a new copy of the entire expression with that one node replaced.
    // Used for testing
    public final Stream<Pair<Expression, Function<Expression, Expression>>> _test_allMutationPoints()
    {
        return Stream.<Pair<Expression, Function<Expression, Expression>>>concat(Stream.<Pair<Expression, Function<Expression, Expression>>>of(new Pair<>(this, e -> e)), _test_childMutationPoints());
    }

    public abstract Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints();

    // If this item can't make a type failure by itself (e.g. a literal) then returns null
    // Otherwise, uses the given generator to make a copy of itself which contains a type failure
    // in this node.  E.g. an equals expression might replace the lhs or rhs with a different type
    public abstract @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException;

    // Force sub-expressions to implement equals and hashCode:
    @Override
    public abstract boolean equals(@Nullable Object o);
    @Override
    public abstract int hashCode();

    @Override
    public final StyledString toStyledString()
    {
        return toDisplay(BracketedStatus.DONT_NEED_BRACKETS, (s, e) -> s);
    }

    public static interface ExpressionStyler
    {
        public StyledString styleExpression(StyledString display, Expression src);
    }
    
    protected abstract StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler);

    // Only for testing:
    public static interface _test_TypeVary
    {
        public Expression getDifferentType(@Nullable TypeExp type) throws InternalException, UserException;
        public Expression getAnyType() throws UserException, InternalException;
        public Expression getNonNumericType() throws InternalException, UserException;

        public Expression getType(Predicate<DataType> mustMatch) throws InternalException, UserException;
        public List<Expression> getTypes(int amount, ExFunction<List<DataType>, Boolean> mustMatch) throws InternalException, UserException;

        public Expression makeArrayExpression(ImmutableList<Expression> items);

        public TypeManager getTypeManager();
    }

    /**
     * Lookup for filter and calculate.  Available columns are:
     *  - per-row columns (with null table id) from
     *    the source table (equivalent to  our table if the column
     *    is unaltered)
     *  - @entire columns from the source table (equivalent to our table if the column
     *     is unaltered) 
     *  -  @entire columns from all tables that
     *     are behind this in the dependency tree,
     *     with non-null table id.
     */
    public static class MultipleTableLookup implements ColumnLookup
    {
        // Only null during testing
        private final @Nullable TableId us;
        private final TableManager tableManager;
        private final @Nullable Table srcTable;
        private final @Nullable ColumnId editing;

        public MultipleTableLookup(@Nullable TableId us, TableManager tableManager, @Nullable TableId srcTableId, @Nullable ColumnId editing)
        {
            this.us = us;
            this.tableManager = tableManager;
            this.srcTable = srcTableId == null ? null : tableManager.getSingleTableOrNull(srcTableId);
            this.editing = editing;
        }

        @Override
        public Stream<ColumnReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
        {
            // Handle us and source table specially:
            if (us != null && tableId.equals(us))
            {
                // Can't refer to an edited column
                if (columnId.equals(editing))
                    return Stream.empty();
                
                Table usTable = tableManager.getSingleTableOrNull(us);
                if (usTable == null)
                    return Stream.empty();
                try
                {
                    Column column = usTable.getData().getColumnOrNull(columnId);
                    if (column == null || column.isAltered())
                        return Stream.empty();
                    return Stream.of(new ColumnReference(columnId, ColumnReferenceType.CORRESPONDING_ROW), new ColumnReference(columnId, ColumnReferenceType.WHOLE_COLUMN));
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                    return Stream.empty();
                }
            }
            if (srcTable != null && tableId.equals(srcTable.getId()))
            {
                try
                {
                    Column column = srcTable.getData().getColumnOrNull(columnId);
                    if (column == null)
                        return Stream.empty();
                    return Stream.of(new ColumnReference(columnId, ColumnReferenceType.CORRESPONDING_ROW), new ColumnReference(columnId, ColumnReferenceType.WHOLE_COLUMN));
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                    return Stream.empty();
                }
            }
            // For everything else fall back to usual:
            return getAvailableColumnReferences().filter(c -> tableId.equals(c.getTableId()) && columnId.equals(c.getColumnId()));
        }

        public Stream<ColumnReference> getAvailableColumnReferences()
        {
            return tableManager.getAllTablesAvailableTo(srcTable == null ? null : srcTable.getId()).stream().flatMap(t -> {
                try
                {
                    boolean isUsOrSrc = Objects.equals(us, t.getId()) || (srcTable != null && Objects.equals(t.getId(), srcTable.getId()));
                    return t.getData().getColumns().stream().flatMap(c -> {
                        Stream<ColumnReferenceType> possRefTypes = isUsOrSrc ? Arrays.stream(ColumnReferenceType.values()) : Stream.of(ColumnReferenceType.WHOLE_COLUMN);
                        return possRefTypes.map(rt -> new ColumnReference(isUsOrSrc ? null : t.getId(), c.getName(), rt));
                    });
                }
                catch (UserException e)
                {
                    return Stream.empty();
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return Stream.empty();
                }
            }).distinct();
        }

        @Override
        public @Nullable FoundColumn getColumn(@Nullable TableId tableId, ColumnId columnId, ColumnReferenceType columnReferenceType)
        {
            try
            {
                @Nullable Pair<TableId, RecordSet> rs = null;
                if (tableId == null)
                {
                    if (srcTable != null)
                        rs = new Pair<>(srcTable.getId(), srcTable.getData());
                }
                else
                {
                    Table table = tableManager.getSingleTableOrNull(tableId);
                    if (table != null)
                        rs = new Pair<>(table.getId(), table.getData());
                }
                
                if (rs != null)
                {
                    Column column = rs.getSecond().getColumn(columnId);
                    DataTypeValue columnType = column.getType();
                    switch (columnReferenceType)
                    {
                        case CORRESPONDING_ROW:
                            return new FoundColumn(rs.getFirst(), columnType, checkRedefined(tableId, columnId));
                        case WHOLE_COLUMN:
                            return new FoundColumn(rs.getFirst(), DataTypeValue.array(columnType.getType(), (i, prog) -> DataTypeUtility.value(new ListExDTV(column))), checkRedefined(tableId, columnId));
                        default:
                            throw new InternalException("Unknown reference type: " + columnReferenceType);
                    }
                    
                }
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
            }
            return null;
        }

        // If column is redefined in this table, issue a warning
        private @Nullable StyledString checkRedefined(@Nullable TableId tableId, ColumnId columnId)
        {
            if (tableId == null && us != null)
            {
                try
                {
                    Table ourTable = tableManager.getSingleTableOrNull(us);
                    if (ourTable == null)
                        return null;
                    RecordSet rs = ourTable.getData();
                    Column c = rs.getColumnOrNull(columnId);
                    if (c != null && c.isAltered())
                    {
                        return StyledString.concat(StyledString.s("Note: column "), StyledString.styled(c.getName().getRaw(), new StyledCSS("column-reference")), StyledString.s(" is re-calculated in this table, but this reference will use the value from the source table."));
                        // TODO could add quick fix here to split into separate calculate.
                    }
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                }
            }
            return null;
        }

    }
    
    // Styles the string to look like a user-typed part of the expression
    protected static StyledString styledExpressionInput(String s)
    {
        return StyledString.styled(s, new ExpressionInputStyle());
    }

    private static class ExpressionInputStyle extends Style<ExpressionInputStyle>
    {
        protected ExpressionInputStyle()
        {
            super(ExpressionInputStyle.class);
        }

        @Override
        protected @OnThread(Tag.FXPlatform) void style(Text t)
        {
            t.getStyleClass().add("expression-input");
        }

        @Override
        protected ExpressionInputStyle combine(ExpressionInputStyle with)
        {
            return this;
        }

        @Override
        protected boolean equalsStyle(ExpressionInputStyle item)
        {
            return true;
        }
    }
}
