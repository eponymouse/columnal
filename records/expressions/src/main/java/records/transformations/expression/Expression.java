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
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.*;
import records.grammar.ExpressionParserBaseVisitor;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.UnfinishedTypeExpression;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import records.typeExp.ExpressionBase;
import records.typeExp.TypeClassRequirements;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.StreamTreeBuilder;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression extends ExpressionBase implements LoadableExpression<Expression, ExpressionSaver>, StyledShowable
{
    public static final int MAX_STRING_SOLVER_LENGTH = 8;

    public static interface TableLookup
    {
        // If you pass null, you get the default table (or null if none)
        // If no such table is found, null is returned
        public @Nullable RecordSet getTable(@Nullable TableId tableId);
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
                        .collect(ImmutableList.toImmutableList());
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
    
    // Checks that all used variable names and column references are defined,
    // and that types check.  Return null if any problems.  Can be either EXPRESSION or PATTERN
    public abstract @Nullable CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException;
    
    // Calls check, but makes sure it is an EXPRESSION
    public final @Nullable TypeExp checkExpression(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable CheckedExp check = check(dataLookup, typeState, onError);
        if (check == null)
            return null;
        if (check.expressionKind != ExpressionKind.EXPRESSION)
        {
            onError.recordError(this, StyledString.s("Pattern is not valid here"));
            return null;
        }
        return check.typeExp;
    }
    
    /**
     * Gets the value for this expression at the given evaluation state
     */
    @OnThread(Tag.Simulation)
    public abstract Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException;

    // Note that there will be duplicates if referred to multiple times
    public abstract Stream<ColumnReference> allColumnReferences();

    public abstract String save(BracketedStatus surround, TableAndColumnRenames renames);

    public static Expression parse(@Nullable String keyword, String src, TypeManager typeManager) throws UserException, InternalException
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
                return new CompileExpression(typeManager).visit(p.completeExpression().topLevelExpression());
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

    // Vaguely similar to getValue, but instead checks if the expression matches the given value
    // For many expressions, matching means equality, but if a new-variable item is involved
    // it's not necessarily plain equality.
    // Given that the expression has type-checked, you can assume the value is of the same type
    // as the current expression (and throw an InternalException if not)
    // If you override this, you should also override checkAsPattern
    // If there is a match, returns non-null.  If no match, returns null.
    @OnThread(Tag.Simulation)
    public @Nullable EvaluateState matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        @Value Object ourValue = getValue(state).getFirst();
        return Utility.compareValues(value, ourValue) == 0 ? state : null;
    }

    @SuppressWarnings("recorded")
    private static class CompileExpression extends ExpressionParserBaseVisitor<Expression>
    {
        private final TypeManager typeManager;

        public CompileExpression(TypeManager typeManager)
        {
            this.typeManager = typeManager;
        }

        @Override
        public Expression visitColumnRef(ColumnRefContext ctx)
        {
            TableIdContext tableIdContext = ctx.tableId();
            if (ctx.columnId() == null)
                throw new RuntimeException("Error processing column reference");
            return new ColumnReference(tableIdContext == null ? null : new TableId(tableIdContext.getText()), new ColumnId(ctx.columnId().getText()), ctx.columnRefType().WHOLECOLUMN() != null ? ColumnReferenceType.WHOLE_COLUMN : ColumnReferenceType.CORRESPONDING_ROW);
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
            return new ConstructorExpression(typeManager, ctx.typeName() == null ? null : ctx.typeName().getText(), ctx.constructorName().getText());
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
            return new IfThenElseExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)), visitExpression(ctx.expression(2)));
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
                new Lit<UnitExpression>("unit{", UnitLiteralExpression::new, UnitExpression::load, SingleUnitExpression::new),
                new Lit<TypeExpression>("type{", TypeLiteralExpression::new, t -> TypeExpression.parseTypeExpression(typeManager, t), UnfinishedTypeExpression::new),
                new Lit<String>("date{", s -> new TemporalLiteral(DateTimeType.YEARMONTHDAY, s), s -> s, s -> s),
                new Lit<String>("dateym{", s -> new TemporalLiteral(DateTimeType.YEARMONTH, s), s -> s, s -> s),
                new Lit<String>("time{", s -> new TemporalLiteral(DateTimeType.TIMEOFDAY, s), s -> s, s -> s),
                new Lit<String>("datetime{", s -> new TemporalLiteral(DateTimeType.DATETIME, s), s -> s, s -> s),
                new Lit<String>("datetimezoned{", s -> new TemporalLiteral(DateTimeType.DATETIMEZONED, s), s -> s, s -> s)
            );
            
            Optional<Expression> loaded = loaders.stream().flatMap(l -> l.load(literalContent).map(Stream::of).orElse(Stream.empty())).findFirst();
            
            return loaded.orElseGet(() -> new IdentExpression(literalContent.replace('{','_').replace('}', '_')));
        }

        @Override
        public Expression visitCallExpression(CallExpressionContext ctx)
        {
            Expression function = visitCallTarget(ctx.callTarget());
            
            @NonNull Expression args;
            if (ctx.topLevelExpression() != null)
                args = visitTopLevelExpression(ctx.topLevelExpression());
            else
                args = new TupleExpression(ImmutableList.copyOf(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), e -> visitExpression(e))));
            
            return new CallExpression(function, args);
        }

        @Override
        public Expression visitStandardFunction(StandardFunctionContext ctx)
        {
            String functionName = ctx.ident().getText();
            @Nullable FunctionDefinition functionDefinition = null;
            try
            {
                functionDefinition = FunctionList.lookup(typeManager.getUnitManager(), functionName);
            }
            catch (InternalException e)
            {
                Utility.report(e);
                // Carry on, but function is null so will count as unknown
            }
            if (functionDefinition == null)
            {
                return new IdentExpression(functionName);
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
            List<Function<MatchExpression, MatchClause>> clauses = new ArrayList<>();
            for (MatchClauseContext matchClauseContext : ctx.matchClause())
            {
                clauses.add(me -> {
                    List<Pattern> patterns = new ArrayList<>();
                    for (PatternContext patternContext : matchClauseContext.pattern())
                    {
                        @Nullable TopLevelExpressionContext guardExpression = patternContext.topLevelExpression().size() < 2 ? null : patternContext.topLevelExpression(1);
                        @Nullable Expression guard = guardExpression == null ? null : visitTopLevelExpression(guardExpression);
                        patterns.add(new Pattern(visitTopLevelExpression(patternContext.topLevelExpression(0)), guard));
                    }
                    return me.new MatchClause(patterns, visitExpression(matchClauseContext.expression()));
                });
            }
            return new MatchExpression(visitExpression(ctx.expression()), clauses);
        }

        @Override
        public Expression visitArrayExpression(ArrayExpressionContext ctx)
        {
            if (ctx.compoundExpression() != null)
                return new ArrayExpression(ImmutableList.of(visitCompoundExpression(ctx.compoundExpression())));
            else
                return new ArrayExpression(ImmutableList.copyOf(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), c -> visitExpression(c))));
        }

        @Override
        public Expression visitTupleExpression(TupleExpressionContext ctx)
        {
            return new TupleExpression(ImmutableList.copyOf(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), c -> visitExpression(c))));
        }

        @Override
        public Expression visitBracketedExpression(BracketedExpressionContext ctx)
        {
            return visitTopLevelExpression(ctx.topLevelExpression());
        }

        @Override
        public Expression visitVarRef(VarRefContext ctx)
        {
            return new IdentExpression(ctx.getText());
        }

        @Override
        public Expression visitUnfinished(ExpressionParser.UnfinishedContext ctx)
        {
            return new IdentExpression(ctx.STRING().getText());
        }

        @Override
        public Expression visitNewVariable(ExpressionParser.NewVariableContext ctx)
        {
            return new VarDeclExpression(ctx.ident().getText());
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
            return new InvalidOperatorExpression(Utility.<InvalidOpItemContext, Either<String, Expression>>mapListI(ctx.invalidOpItem(), 
                c -> c.expression() != null ? Either.<String, Expression>right(visitExpression(c.expression())) : Either.<String, Expression>left(c.STRING().getText())));
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
        return save(BracketedStatus.TOP_LEVEL, TableAndColumnRenames.EMPTY);
    }

    // This is like a zipper.  It gets a list of all expressions in the tree (i.e. all nodes)
    // and returns them, along with a function.  If you pass that function a replacement,
    // it will build you a new copy of the entire expression with that one node replaced.
    // Used for testing
    public final Stream<Pair<Expression, Function<Expression, Expression>>> _test_allMutationPoints()
    {
        return Stream.<Pair<Expression, Function<Expression, Expression>>>concat(Stream.of(new Pair<>(this, e -> e)), _test_childMutationPoints());
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
        return toDisplay(BracketedStatus.TOP_LEVEL);
    }

    protected abstract StyledString toDisplay(BracketedStatus bracketedStatus);

    // Only for testing:
    public static interface _test_TypeVary extends FunctionDefinition._test_TypeVary<Expression>
    {
        /*
        public Expression getDifferentType(@Nullable TypeExp type) throws InternalException, UserException;
        public Expression getAnyType() throws UserException, InternalException;
        public Expression getNonNumericType() throws InternalException, UserException;

        public Expression getType(Predicate<DataType> mustMatch) throws InternalException, UserException;
        public List<Expression> getTypes(int amount, ExFunction<List<DataType>, Boolean> mustMatch) throws InternalException, UserException;
        */
    }

    public static class SingleTableLookup implements TableLookup
    {
        private final @Nullable Table srcTable;

        private SingleTableLookup(Table srcTable)
        {
            this.srcTable = srcTable;
        }

        @Override
        public @Nullable RecordSet getTable(@Nullable TableId tableName)
        {
            try
            {
                if (srcTable == null)
                    return null;
                else if (tableName == null || tableName.equals(srcTable.getId()))
                    return srcTable.getData();
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
            }
            return null;
        }
    }
    
    public static class MultipleTableLookup implements TableLookup
    {
        private final TableManager tableManager;
        private final @Nullable Table srcTable;

        public MultipleTableLookup(TableManager tableManager, @Nullable Table srcTable)
        {
            this.tableManager = tableManager;
            this.srcTable = srcTable;
        }

        @Override
        public @Nullable RecordSet getTable(@Nullable TableId tableId)
        {
            try
            {
                if (tableId == null)
                {
                    if (srcTable != null)
                        return srcTable.getData();
                }
                else
                {
                    Table table = tableManager.getSingleTableOrNull(tableId);
                    if (table != null)
                        return table.getData();
                }
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
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
    
    // Round brackets if needed
    @OnThread(Tag.FXPlatform)
    protected static void roundBracket(BracketedStatus bracketedStatus, boolean evenAtTopLevel, StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> builder, FXPlatformRunnable buildContent)
    {
        if (bracketedStatus == BracketedStatus.DIRECT_ROUND_BRACKETED || (bracketedStatus == BracketedStatus.TOP_LEVEL && !evenAtTopLevel))
        {
            buildContent.run();
        }
        else
        {
            builder.add(GeneralExpressionEntry.load(Keyword.OPEN_ROUND));
            buildContent.run();
            builder.add(GeneralExpressionEntry.load(Keyword.CLOSE_ROUND));
        }
    }
}
