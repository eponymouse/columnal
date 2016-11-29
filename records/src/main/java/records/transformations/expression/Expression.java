package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.DataTypeValue.SpecificDataTypeVisitorGet;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.ColumnRefContext;
import records.grammar.ExpressionParser.ExpressionContext;
import records.grammar.ExpressionParser.MatchClauseContext;
import records.grammar.ExpressionParser.MatchContext;
import records.grammar.ExpressionParser.NumericLiteralContext;
import records.grammar.ExpressionParser.PatternContext;
import records.grammar.ExpressionParser.PatternMatchContext;
import records.grammar.ExpressionParser.TableIdContext;
import records.grammar.ExpressionParserBaseVisitor;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.MatchExpression.PatternMatch;
import records.transformations.expression.MatchExpression.PatternMatchConstructor;
import records.transformations.expression.MatchExpression.PatternMatchExpression;
import records.transformations.expression.MatchExpression.PatternMatchVariable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression
{
    @OnThread(Tag.Simulation)
    public boolean getBoolean(RecordSet data, int rowIndex, EvaluateState state, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        return getTypeValue(data, state).applyGet(new SpecificDataTypeVisitorGet<Boolean>(new UserException("Type must be boolean")) {
            @Override
            @OnThread(Tag.Simulation)
            public Boolean bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                return g.getWithProgress(rowIndex, prog);
            }
        });
    }

    // Checks that all used variable names and column references are defined,
    // and that types check.  Return null if any problems
    public abstract @Nullable DataType check(RecordSet data, TypeState state, BiConsumer<Expression, String> onError) throws UserException, InternalException;

    public abstract DataTypeValue getTypeValue(RecordSet data, EvaluateState state) throws UserException, InternalException;

    // Note that there will be duplicates if referred to multiple times
    public abstract Stream<ColumnId> allColumnNames();

    @OnThread(Tag.FXPlatform)
    public abstract String save();

    public static Expression parse(@Nullable String keyword, String src) throws UserException, InternalException
    {
        if (keyword != null)
        {
            src = src.trim();
            if (src.startsWith(keyword))
                src = src.substring(keyword.length());
            else
                throw new UserException("Missing keyword: " + keyword);
        }
        return Utility.parseAsOne(src.replace("\r", "").replace("\n", ""), ExpressionLexer::new, ExpressionParser::new, p -> {
            return new CompileExpression().visit(p.expression());
        });
    }

    public abstract Formula toSolver(FormulaManager formulaManager, RecordSet src) throws InternalException, UserException;

    private static class CompileExpression extends ExpressionParserBaseVisitor<Expression>
    {
        @Override
        public Expression visitColumnRef(ColumnRefContext ctx)
        {
            TableIdContext tableIdContext = ctx.tableId();
            if (ctx.columnId() == null)
                throw new RuntimeException("Error processing column reference");
            return new ColumnReference(tableIdContext == null ? null : new TableId(tableIdContext.getText()), new ColumnId(ctx.columnId().getText()));
        }

        @Override
        public Expression visitNumericLiteral(NumericLiteralContext ctx)
        {
            return new NumericLiteral(Utility.parseNumber(ctx.getText()));
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
                        List<Expression> guards = Utility.<ExpressionContext, Expression>mapList(patternContext.expression(), this::visitExpression);
                        patterns.add(new Pattern(processPatternMatch(me, patternContext.patternMatch()), guards));
                    }
                    return me.new MatchClause(patterns, visitExpression(matchClauseContext.expression()));
                });
            }
            return new MatchExpression(visitExpression(ctx.expression()), clauses);
        }

        private PatternMatch processPatternMatch(MatchExpression me, PatternMatchContext ctx)
        {
            if (ctx.constructor() != null)
            {
                PatternMatchContext subPattern = ctx.patternMatch();
                return me.new PatternMatchConstructor(ctx.constructor().getText(), subPattern == null ? null : processPatternMatch(me, subPattern));
            }
            else if (ctx.variable() != null)
            {
                return me.new PatternMatchVariable(ctx.variable().UNQUOTED_IDENT().getText());
            }
            else if (ctx.expression() != null)
            {
                return new PatternMatchExpression(visitExpression(ctx.expression()));
            }
            throw new RuntimeException("Unknown case in processPatternMatch");
        }
    }
}
