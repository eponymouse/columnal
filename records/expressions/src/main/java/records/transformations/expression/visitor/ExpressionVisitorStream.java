package records.transformations.expression.visitor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager.TagInfo;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.AndExpression;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.ConstructorExpression;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.ImplicitLambdaArg;
import records.transformations.expression.InvalidIdentExpression;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.expression.MatchAnythingExpression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.OrExpression;
import records.transformations.expression.PlusMinusPatternExpression;
import records.transformations.expression.RaiseExpression;
import records.transformations.expression.StandardFunction;
import records.transformations.expression.StringConcatExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TemporalLiteral;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitLiteralExpression;
import records.transformations.expression.VarDeclExpression;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.type.TypeExpression;
import styled.StyledString;
import utility.Either;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.stream.Stream;

/**
 * By default, concatenates streams from visiting all children expressions.
 * Returns empty stream for all terminal nodes.
 */
public class ExpressionVisitorStream<T> implements ExpressionVisitor<Stream<T>>
{
    @Override
    public Stream<T> notEqual(NotEqualExpression self, Expression lhs, Expression rhs)
    {
        return apply(lhs, rhs);
    }

    private Stream<T> apply(Expression... expressions)
    {
        return apply(ImmutableList.copyOf(expressions));
    }
    
    private Stream<T> apply(ImmutableList<Expression> expressions)
    {
        return expressions.stream().flatMap(e -> e.visit(this));
    }

    @Override
    public Stream<T> divide(DivideExpression self, Expression lhs, Expression rhs)
    {
        return apply(lhs, rhs);
    }

    @Override
    public Stream<T> addSubtract(AddSubtractExpression self, ImmutableList<Expression> expressions, ImmutableList<AddSubtractOp> ops)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> and(AndExpression self, ImmutableList<Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> or(OrExpression self, ImmutableList<Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> list(ArrayExpression self, ImmutableList<Expression> items)
    {
        return apply(items);
    }

    @Override
    public Stream<T> column(ColumnReference self, @Nullable TableId tableName, ColumnId columnName, ColumnReferenceType referenceType)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> litBoolean(BooleanLiteral self, @Value Boolean value)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> call(CallExpression self, @Recorded Expression callTarget, ImmutableList<Expression> arguments)
    {
        return apply(Utility.prependToList(callTarget, arguments));
    }

    @Override
    public Stream<T> comparison(ComparisonExpression self, ImmutableList<Expression> expressions, ImmutableList<ComparisonOperator> operators)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> equal(EqualExpression self, ImmutableList<Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> ident(IdentExpression self, @ExpressionIdentifier String text)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
    {
        return apply(condition, thenExpression, elseExpression);
    }

    @Override
    public Stream<T> invalidIdent(InvalidIdentExpression self, String text)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> implicitLambdaArg(ImplicitLambdaArg self)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> invalidOps(InvalidOperatorExpression self, ImmutableList<Expression> items)
    {
        return apply(items);
    }

    @Override
    public Stream<T> matchAnything(MatchAnythingExpression self)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> litNumber(NumericLiteral self, @Value Number value, @Nullable UnitExpression unit)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> plusMinus(PlusMinusPatternExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return apply(lhs, rhs);
    }

    @Override
    public Stream<T> raise(RaiseExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return apply(lhs, rhs);
    }

    @Override
    public Stream<T> standardFunction(StandardFunction self, StandardFunctionDefinition functionDefinition)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> concatText(StringConcatExpression self, ImmutableList<Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> litText(StringLiteral self, @Value String value)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> litTemporal(TemporalLiteral self, DateTimeType literalType, String content, Either<StyledString, TemporalAccessor> value)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> multiply(TimesExpression self, ImmutableList<Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> tuple(TupleExpression self, ImmutableList<Expression> members)
    {
        return apply(members);
    }

    @Override
    public Stream<T> litType(TypeLiteralExpression self, TypeExpression type)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> litUnit(UnitLiteralExpression self, @Recorded UnitExpression unitExpression)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> varDecl(VarDeclExpression self, @ExpressionIdentifier String varName)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> constructor(ConstructorExpression self, Either<String, TagInfo> tag)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses)
    {
        Stream<T> expStream = expression.visit(this);
        return Stream.<T>concat(expStream, clauses.stream().<T>flatMap(clause -> {
            // Must call patterns first:
            Stream<T> patternsStream = Utility.mapListI(clause.getPatterns(), p -> visitPattern(p)).stream().flatMap(s -> s);
            Stream<T> outcomeStream = clause.getOutcome().visit(this);
            return Stream.<T>concat(patternsStream, outcomeStream);
        }));
    }

    protected Stream<T> visitPattern(Pattern pattern)
    {
        Stream<T> patStream = pattern.getPattern().visit(this);
        return Stream.<T>concat(patStream, Utility.streamNullable(pattern.getGuard()).<T>flatMap(g -> g.visit(this)));
    }
}
