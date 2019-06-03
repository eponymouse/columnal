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
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DefineExpression.Definition;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.type.TypeExpression;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.time.temporal.TemporalAccessor;

public abstract class ExpressionVisitorFlat<T> implements ExpressionVisitor<T>
{
    protected abstract T makeDef(Expression expression);

    @Override
    public T notEqual(NotEqualExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return makeDef(self);
    }

    @Override
    public T divide(DivideExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return makeDef(self);
    }

    @Override
    public T addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops)
    {
        return makeDef(self);
    }

    @Override
    public T and(AndExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }

    @Override
    public T or(OrExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }

    @Override
    public T list(ArrayExpression self, ImmutableList<@Recorded Expression> items)
    {
        return makeDef(self);
    }

    @Override
    public T column(ColumnReference self, @Nullable TableId tableName, ColumnId columnName, ColumnReferenceType referenceType)
    {
        return makeDef(self);
    }

    @Override
    public T litBoolean(BooleanLiteral self, @Value Boolean value)
    {
        return makeDef(self);
    }

    @Override
    public T call(CallExpression self, @Recorded Expression callTarget, ImmutableList<@Recorded Expression> arguments)
    {
        return makeDef(self);
    }

    @Override
    public T comparison(ComparisonExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators)
    {
        return makeDef(self);
    }

    @Override
    public T equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }

    @Override
    public T ident(IdentExpression self, @ExpressionIdentifier String text)
    {
        return makeDef(self);
    }

    @Override
    public T ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
    {
        return makeDef(self);
    }

    @Override
    public T invalidIdent(InvalidIdentExpression self, String text)
    {
        return makeDef(self);
    }

    @Override
    public T implicitLambdaArg(ImplicitLambdaArg self)
    {
        return makeDef(self);
    }

    @Override
    public T invalidOps(InvalidOperatorExpression self, ImmutableList<@Recorded Expression> items)
    {
        return makeDef(self);
    }

    @Override
    public T matchAnything(MatchAnythingExpression self)
    {
        return makeDef(self);
    }

    @Override
    public T litNumber(NumericLiteral self, @Value Number value, @Nullable UnitExpression unit)
    {
        return makeDef(self);
    }

    @Override
    public T plusMinus(PlusMinusPatternExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return makeDef(self);
    }

    @Override
    public T raise(RaiseExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return makeDef(self);
    }

    @Override
    public T standardFunction(StandardFunction self, StandardFunctionDefinition functionDefinition)
    {
        return makeDef(self);
    }

    @Override
    public T concatText(StringConcatExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }

    @Override
    public T litText(StringLiteral self, @Value String value)
    {
        return makeDef(self);
    }

    @Override
    public T litTemporal(TemporalLiteral self, DateTimeType literalType, String content, Either<StyledString, TemporalAccessor> value)
    {
        return makeDef(self);
    }

    @Override
    public T multiply(TimesExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }


    @Override
    public T record(RecordExpression self, ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members)
    {
        return makeDef(self);
    }

    @Override
    public T litType(TypeLiteralExpression self, TypeExpression type)
    {
        return makeDef(self);
    }

    @Override
    public T litUnit(UnitLiteralExpression self, @Recorded UnitExpression unitExpression)
    {
        return makeDef(self);
    }

    @Override
    public T constructor(ConstructorExpression self, Either<String, TagInfo> tag)
    {
        return makeDef(self);
    }

    @Override
    public T match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses)
    {
        return makeDef(self);
    }

    @Override
    public T define(DefineExpression self, ImmutableList<Either<@Recorded HasTypeExpression, Definition>> defines, @Recorded Expression body)
    {
        return makeDef(self);
    }

    @Override
    public T hasType(HasTypeExpression self, @ExpressionIdentifier String varName, @Recorded TypeLiteralExpression type)
    {
        return makeDef(self);
    }

    @Override
    public T lambda(LambdaExpression self, ImmutableList<@Recorded Expression> parameters, @Recorded Expression body)
    {
        return makeDef(self);
    }

    @Override
    public T field(FieldAccessExpression self, Expression lhsRecord, @ExpressionIdentifier String fieldName)
    {
        return makeDef(self);
    }
}
