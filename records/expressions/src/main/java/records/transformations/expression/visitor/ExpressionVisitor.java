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
import java.util.List;

public interface ExpressionVisitor<T>
{
    T notEqual(NotEqualExpression self, @Recorded Expression lhs, @Recorded Expression rhs);
    T divide(DivideExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops);

    T and(AndExpression self, ImmutableList<@Recorded Expression> expressions);
    T or(OrExpression self, ImmutableList<@Recorded Expression> expressions);

    T list(ArrayExpression self, ImmutableList<@Recorded Expression> items);

    T column(ColumnReference self, @Nullable TableId tableName, ColumnId columnName, ColumnReferenceType referenceType);

    T litBoolean(BooleanLiteral self, @Value Boolean value);

    T call(CallExpression self, @Recorded Expression callTarget, ImmutableList<@Recorded Expression> arguments);

    T comparison(ComparisonExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators);
    // Singular name to avoid clash with Object.equals
    T equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions, boolean lastIsPattern);

    T ident(IdentExpression self, @ExpressionIdentifier String text);

    T ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression);

    T invalidIdent(InvalidIdentExpression self, String text);

    T implicitLambdaArg(ImplicitLambdaArg self);

    T invalidOps(InvalidOperatorExpression self, ImmutableList<@Recorded Expression> items);

    T matchAnything(MatchAnythingExpression self);

    T litNumber(NumericLiteral self, @Value Number value, @Nullable UnitExpression unit);

    T plusMinus(PlusMinusPatternExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T raise(RaiseExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T standardFunction(StandardFunction self, StandardFunctionDefinition functionDefinition);

    T concatText(StringConcatExpression self, ImmutableList<@Recorded Expression> expressions);

    T litText(StringLiteral self, String rawValue);

    T litTemporal(TemporalLiteral self, DateTimeType literalType, String content, Either<StyledString, TemporalAccessor> value);

    T multiply(TimesExpression self, ImmutableList<@Recorded Expression> expressions);

    T litType(TypeLiteralExpression self, @Recorded TypeExpression type);

    T litUnit(UnitLiteralExpression self, @Recorded UnitExpression unitExpression);

    T constructor(ConstructorExpression self, Either<String, TagInfo> tag);

    T match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses);
    
    T define(DefineExpression self, ImmutableList<DefineExpression.DefineItem> defines, @Recorded Expression body);

    T hasType(@Recorded HasTypeExpression self, @Recorded Expression lhsVar, @Recorded Expression rhsType);

    T lambda(LambdaExpression self, ImmutableList<@Recorded Expression> parameters, @Recorded Expression body);

    T record(RecordExpression self, ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members);

    T field(FieldAccessExpression self, @Recorded Expression lhsRecord, @Recorded Expression fieldName);
}
