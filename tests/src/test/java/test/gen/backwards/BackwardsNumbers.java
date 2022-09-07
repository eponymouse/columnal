package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.AddSubtractExpression;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.DivideExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.NumericLiteral;
import test.TestUtil;
import xyz.columnal.utility.Utility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsNumbers extends BackwardsProvider
{
    public BackwardsNumbers(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of();
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType type, @Value Object targetValue) throws InternalException, UserException
    {
        if (!DataTypeUtility.isNumber(type))
            return ImmutableList.of();
        Unit unit = TestUtil.getUnit(type);
        return ImmutableList.of(
            () -> {
                // We just make up a bunch of numbers, and at the very end we add one more to correct the difference
                int numMiddle = r.nextInt(0, 6);
                List<Expression> expressions = new ArrayList<>();
                List<AddSubtractOp> addSubtractOps = new ArrayList<>();
                BigDecimal curTotal = BigDecimal.valueOf(parent.genInt());
                expressions.add(parent.make(type, curTotal, maxLevels - 1));
                for (int i = 0; i < numMiddle; i++)
                {
                    //System.err.println("Exp Cur: " + curTotal.toPlainString() + " after " + expressions.get(i));
                    long next = parent.genInt();
                    expressions.add(parent.make(type, next, maxLevels - 1));
                    BigDecimal prevTotal = curTotal;
                    if (r.nextBoolean())
                    {
                        curTotal = curTotal.add(BigDecimal.valueOf(next), MathContext.DECIMAL128);
                        if (prevTotal.add(BigDecimal.valueOf(next)).compareTo(curTotal) != 0)
                            throw new RuntimeException("Error building expression +");
                        addSubtractOps.add(AddSubtractOp.ADD);
                    }
                    else
                    {
                        curTotal = curTotal.subtract(BigDecimal.valueOf(next), MathContext.DECIMAL128);
                        if (prevTotal.subtract(BigDecimal.valueOf(next)).compareTo(curTotal) != 0)
                            throw new RuntimeException("Error building expression +");
                        addSubtractOps.add(AddSubtractOp.SUBTRACT);
                    }
                }
                //System.err.println("Exp Cur: " + curTotal.toPlainString() + " after " + expressions.get(expressions.size() - 1));
                // Now add one more to make the difference:
                BigDecimal diff = (targetValue instanceof BigDecimal ? (BigDecimal)targetValue : BigDecimal.valueOf(((Number)targetValue).longValue())).subtract(curTotal, MathContext.DECIMAL128);
                boolean add = r.nextBoolean();
                expressions.add(parent.make(type, DataTypeUtility.value(add ? diff : diff.negate()), maxLevels - 1));
                addSubtractOps.add(add ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT);
                //System.err.println("Exp Result: " + Utility.toBigDecimal((Number)targetValue).toPlainString() + " after " + expressions.get(expressions.size() - 1) + " diff was: " + diff.toPlainString());
                return new AddSubtractExpression(expressions, addSubtractOps);
            }, () -> {
                    // A few options; keep units and value in numerator and divide by 1
                    // Or make random denom, times that by target to get num, and make up crazy units which work
                    if (r.nextInt(0, 4) == 0)
                        return new DivideExpression(parent.make(type, targetValue, maxLevels - 1), new NumericLiteral(1, parent.makeUnitExpression(Unit.SCALAR)));
                    else
                    {
                        long denominator;
                        do
                        {
                            denominator = parent.genInt();
                        }
                        while (Utility.compareNumbers(denominator, 0) == 0);
                        @Value Number numerator = Utility.multiplyNumbers((Number) targetValue, DataTypeUtility.value(denominator));
                        if (Utility.compareNumbers(Utility.divideNumbers(numerator, DataTypeUtility.value(denominator)), (Number)targetValue) != 0)
                        {
                            // Divide won't come out right: just divide by 1:
                            return new DivideExpression(parent.make(type, targetValue, maxLevels - 1), new NumericLiteral(1, parent.makeUnitExpression(Unit.SCALAR)));
                        }

                        // Either just use numerator, or make up crazy one
                        Unit numUnit = r.nextBoolean() ? unit : makeUnit();
                        Unit denomUnit = calculateRequiredMultiplyUnit(numUnit, unit).reciprocal();
                        // TODO test division by zero behaviour (test errors generally)
                        return new DivideExpression(parent.make(DataType.number(new NumberInfo(numUnit)), numerator, maxLevels - 1), parent.make(DataType.number(new NumberInfo(denomUnit)), denominator, maxLevels - 1));
                    }
                }
        );
    }

    // What unit do you have to multiply src by to get dest?
    private Unit calculateRequiredMultiplyUnit(Unit src, Unit dest)
    {
        // So we have src * x = dest
        // This can be rearranged to x = dest/src
        return dest.divideBy(src);
    }

    private Unit makeUnit() throws InternalException, UserException
    {
        return r.<@NonNull Unit>choose(Arrays.asList(
            getUnit("m"),
            getUnit("cm"),
            getUnit("inch"),
            getUnit("g"),
            getUnit("kg"),
            getUnit("deg"),
            getUnit("s"),
            getUnit("hour"),
            getUnit("USD")
        ));
    }
}
