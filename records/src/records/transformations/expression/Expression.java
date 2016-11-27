package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.GetValue;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.SpecificDataTypeVisitorGet;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.BinaryOpExpressionContext;
import records.grammar.ExpressionParser.ColumnRefContext;
import records.grammar.ExpressionParser.ExpressionContext;
import records.grammar.ExpressionParser.NumericLiteralContext;
import records.grammar.ExpressionParser.TableIdContext;
import records.grammar.ExpressionParser.TerminalContext;
import records.grammar.ExpressionParserBaseVisitor;
import records.grammar.ExpressionParserVisitor;
import records.transformations.expression.BinaryOpExpression.Op;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression
{

    @OnThread(Tag.Simulation)
    public boolean getBoolean(RecordSet data, int rowIndex, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        return getType(data).apply(new SpecificDataTypeVisitorGet<Boolean>(new UserException("Type must be boolean")) {
            @Override
            @OnThread(Tag.Simulation)
            protected Boolean bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                return g.getWithProgress(rowIndex, prog);
            }
        });
    }

    public abstract DataType getType(RecordSet data) throws UserException, InternalException;

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

    private static class CompileExpression extends ExpressionParserBaseVisitor<Expression>
    {
        @Override
        public Expression visitColumnRef(ColumnRefContext ctx)
        {
            TableIdContext tableIdContext = ctx.tableId();
            if (ctx.columnId() == null)
                throw new RuntimeException("WTF, yo? " + ctx.getText());
            return new ColumnReference(tableIdContext == null ? null : new TableId(tableIdContext.getText()), new ColumnId(ctx.columnId().getText()));
        }

        @Override
        public Expression visitNumericLiteral(NumericLiteralContext ctx)
        {
            return new NumericLiteral(Utility.parseNumber(ctx.getText()));
        }

        @Override
        public Expression visitBinaryOpExpression(BinaryOpExpressionContext ctx)
        {
            @Nullable Op op = Op.parse(ctx.binaryOp().getText());
            if (op == null)
                throw new RuntimeException("Broken operator parse: " + ctx.binaryOp().getText());
            return new BinaryOpExpression(visitExpression(ctx.expression().get(0)), op, visitExpression(ctx.expression().get(1)));
        }

    }
}
