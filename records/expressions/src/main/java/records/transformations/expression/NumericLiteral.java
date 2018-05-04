package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.BracketedExpression;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.NumLit;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.UnitLiteralNode;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.NumTypeExp;
import records.types.TypeExp;
import records.types.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget.CURRENT;

/**
 * Created by neil on 25/11/2016.
 */
public class NumericLiteral extends Literal
{
    private final @Value Number value;
    private final @Nullable UnitExpression unit;

    public NumericLiteral(Number value, @Nullable @Recorded UnitExpression unit)
    {
        this.value = DataTypeUtility.value(value);
        this.unit = unit;
    }

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws InternalException
    {
        if (unit == null)
            return onError.recordType(this, TypeExp.plainNumber(this));

        Either<Pair<StyledString, List<UnitExpression>>, UnitExp> errOrUnit = unit.asUnit(state.getUnitManager());
        return errOrUnit.<@Nullable @Recorded TypeExp>either(err -> {
            onError.recordError(this, err.getFirst());
            onError.recordQuickFixes(this, Utility.mapList(err.getSecond(), u -> {
                @SuppressWarnings("recorded")
                NumericLiteral replacement = new NumericLiteral(value, u);
                return new QuickFix<>("quick.fix.unit", CURRENT, replacement);
            }));
            return null;
        }, u -> onError.recordType(this, new NumTypeExp(this, u)));
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
    public @Value Object getValue(EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        String num = numberAsString();
        if (unit == null || unit.equals(Unit.SCALAR))
            return num;
        else
            return num + "{" + unit.save(true) + "}";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        StyledString num = StyledString.s(numberAsString());
        if (unit == null || unit.equals(Unit.SCALAR))
            return num;
        else
            return StyledString.concat(num, StyledString.s("{"), unit.toStyledString(), StyledString.s("}"));
    }

    private String numberAsString()
    {
        return Utility.numberToString(value);
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new BracketedExpression(p, SingleLoader.withSemanticParent(loadAsConsecutive(true), s), ')');
    }

    @Override
    public Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        ImmutableList.Builder<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>> builder = ImmutableList.builder();
        builder.add((p, s) -> new GeneralExpressionEntry(new NumLit(value), p, s));
        if (unit != null)
        {
            @NonNull UnitExpression unitFinal = unit;
            builder.add((p, s) -> new UnitLiteralNode(p, unitFinal));
        }
        return new Pair<>(
            builder.build(),
            ImmutableList.of((p, s) -> new OperatorEntry<>(Expression.class, p))
        );
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

    public @Nullable UnitExpression getUnitExpression()
    {
        return unit;
    }

    public NumericLiteral withUnit(Unit unit)
    {
        return new NumericLiteral(value, UnitExpression.load(unit));
    }

    public Number getNumber()
    {
        return value;
    }
}
