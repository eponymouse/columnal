package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.gui.expressioneditor.UnitLiteralExpressionNode;
import records.jellytype.JellyUnit;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Created by neil on 25/11/2016.
 */
public class NumericLiteral extends Literal
{
    private final @Value Number value;
    private final @Nullable @Recorded UnitExpression unit;

    public NumericLiteral(Number value, @Nullable @Recorded UnitExpression unit)
    {
        this.value = DataTypeUtility.value(value);
        this.unit = unit;
    }

    @Override
    public Either<StyledString, TypeExp> checkType(TypeState state) throws InternalException
    {
        if (unit == null)
            return Either.right(new NumTypeExp(this, new UnitExp(new MutUnitVar())));

        Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> errOrUnit = unit.asUnit(state.getUnitManager());
        return errOrUnit.<Either<StyledString, TypeExp>>eitherInt(err -> {
            /*
            onError.recordQuickFixes(this, Utility.mapList(err.getSecond(), u -> {
                @SuppressWarnings("recorded")
                NumericLiteral replacement = new NumericLiteral(value, u);
                return new QuickFix<>("quick.fix.unit", CURRENT, replacement);
            }));
            */
            return Either.left(err.getFirst());
        }, u -> Either.right(new NumTypeExp(this, u.makeUnitExp(ImmutableMap.of()))));
    }

    @Override
    public Optional<Rational> constantFold()
    {
        if (value instanceof BigDecimal)
            return Optional.of(Rational.ofBigDecimal((BigDecimal) value));
        else
            return Optional.of(Rational.of(value.longValue()));
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        return new Pair<>(value, state);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String num = numberAsString();
        if (unit == null)
            return num;
        else
            return num + "{" + unit.save(structured, true) + "}";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        StyledString num = StyledString.s(numberAsString());
        if (unit == null)
            return num;
        else
            return StyledString.concat(num, StyledString.s("{"), unit.toStyledString(), StyledString.s("}"));
    }

    private String numberAsString()
    {
        return Utility.numberToString(value);
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        ImmutableList.Builder<SingleLoader<Expression, ExpressionSaver>> builder = ImmutableList.builder();
        String valueStr = Utility.numberToString(this.value);
        if (valueStr.startsWith("-"))
        {
            valueStr = valueStr.substring(1);
            builder.add(GeneralExpressionEntry.load(Op.SUBTRACT));
        }
        builder.add(GeneralExpressionEntry.load(valueStr));
        if (unit != null)
        {
            @NonNull UnitExpression unitFinal = unit;
            builder.add(p -> new UnitLiteralExpressionNode(p, unitFinal));
        }
        return builder.build().stream();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericLiteral that = (NumericLiteral) o;

        if (unit == null ? that.unit != null : !unit.equals(that.unit)) return false;
        return Utility.compareNumbers(value, that.value) == 0;
    }

    @Override
    public int hashCode()
    {
        int result = value.hashCode();
        result = 31 * result + (unit != null ? unit.hashCode() : 0);
        return result;
    }

    @Override
    public String editString()
    {
        return numberAsString();
    }

    public @Nullable @Recorded UnitExpression getUnitExpression()
    {
        return unit;
    }

    public NumericLiteral withUnit(Unit unit)
    {
        return new NumericLiteral(value, UnitExpression.load(unit));
    }

    public @Value Number getNumber()
    {
        return value;
    }
}
