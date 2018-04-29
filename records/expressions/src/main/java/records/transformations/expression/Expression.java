package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.*;
import records.grammar.ExpressionParserBaseVisitor;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.UnfinishedTypeExpression;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import records.types.ExpressionBase;
import records.types.MutVar;
import records.types.TypeCons;
import records.types.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression extends ExpressionBase implements LoadableExpression<Expression, ExpressionNodeParent>, StyledShowable
{
    public static final int MAX_STRING_SOLVER_LENGTH = 8;

    @OnThread(Tag.Simulation)
    public boolean getBoolean(int rowIndex, EvaluateState state, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        Object val = getValue(rowIndex, state);
        if (val instanceof Boolean)
            return (Boolean) val;
        else
            throw new InternalException("Expected boolean but got: " + val.getClass());
    }
    
    public static interface TableLookup
    {
        // If you pass null, you get the default table (or null if none)
        // If no such table is found, null is returned
        public @Nullable RecordSet getTable(@Nullable TableId tableId);
    }

    // Checks that all used variable names and column references are defined,
    // and that types check.  Return null if any problems
    public abstract @Recorded @Nullable TypeExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException;

    // Like check, but for patterns.  For many expressions this is same as check,
    // unless you are a new-variable declaration or can have one beneath you.
    // If you override this, you should also override matchAsPattern
    public @Nullable Pair<@Recorded TypeExp, TypeState> checkAsPattern(boolean varDeclAllowed, TableLookup data, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // By default, check as normal, and return same TypeState:
        @Nullable @Recorded TypeExp type = check(data, typeState, onError);
        if (type == null)
            return null;
        else
        {
            return new Pair<>(type, typeState);
        }
    }

    /**
     * Gets the value for this expression at the given row index
     * (zero if N/A) and evaluation state
     */
    @OnThread(Tag.Simulation)
    public abstract @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException;

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

    /**
     * Loads this expression as the contents of an outer Consecutive.
     * @param implicitlyRoundBracketed Is this implicitly in a round bracket?  True for function arguments and [round] bracketed expression, false elsewhere
     * @return
     */
    public abstract Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed);

    @Override
    public abstract SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle();

    // Vaguely similar to getValue, but instead checks if the expression matches the given value
    // For many expressions, matching means equality, but if a new-variable item is involved
    // it's not necessarily plain equality.
    // Given that the expression has type-checked, you can assume the value is of the same type
    // as the current expression (and throw an InternalException if not)
    // If you override this, you should also override checkAsPattern
    @OnThread(Tag.Simulation)
    public @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, EvaluateState state) throws InternalException, UserException
    {
        @Value Object ourValue = getValue(rowIndex, state);
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
                return new NumericLiteral(Utility.parseNumber(ctx.NUMBER().getText()), ctx.UNIT() == null ? null : UnitExpression.load(ctx.UNIT().getText()));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException("Error parsing unit: \"" + ctx.UNIT().getText() + "\"", e);
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
            return new AddSubtractExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), Utility.<TerminalNode, Op>mapList(ctx.ADD_OR_SUBTRACT(), op -> op.getText().equals("+") ? Op.ADD : Op.SUBTRACT));
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
        public Expression visitTypeExpression(TypeExpressionContext ctx)
        {
            String typeSrc = ctx.TYPE().getText();
            try
            {
                return new TypeLiteralExpression(TypeExpression.parseTypeExpression(typeManager, typeSrc));
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                return new TypeLiteralExpression(new UnfinishedTypeExpression(typeSrc));
            }
        }

        @Override
        public Expression visitUnitExpression(UnitExpressionContext ctx)
        {
            String unitSrc = ctx.UNIT().getText();
            try
            {
                return new UnitLiteralExpression(UnitExpression.load(unitSrc));
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                return new UnitLiteralExpression(new UnfinishedUnitExpression(unitSrc));
            }
        }

        @Override
        public Expression visitVarRef(VarRefContext ctx)
        {
            return new VarUseExpression(ctx.getText());
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
                return new UnfinishedExpression(functionName);
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
                        @Nullable ExpressionContext guardExpression = patternContext.expression().size() < 2 ? null : patternContext.expression(1);
                        @Nullable Expression guard = guardExpression == null ? null : visitExpression(guardExpression);
                        patterns.add(new Pattern(visitExpression(patternContext.expression(0)), guard));
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
        public Expression visitUnfinished(ExpressionParser.UnfinishedContext ctx)
        {
            return new UnfinishedExpression(ctx.STRING().getText());
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
        public Expression visitInvalidOpExpression(InvalidOpExpressionContext ctx)
        {
            return new InvalidOperatorExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), c -> visitExpression(c)), Utility.mapList(ctx.STRING(), op -> op.getText()));
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
}
