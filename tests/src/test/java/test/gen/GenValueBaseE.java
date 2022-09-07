package test.gen;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.KeyFor;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitDivideExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import records.transformations.expression.UnitRaiseExpression;
import records.transformations.expression.UnitTimesExpression;
import records.transformations.function.FunctionList;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

public abstract class GenValueBaseE<T> extends GenValueBase<T>
{
    protected GenValueBaseE(Class<T> type)
    {
        super(type);
    }

    @SuppressWarnings("recorded")
    public UnitExpression makeUnitExpression(Unit unit)
    {
        // TODO make more varied unit expressions which cancel out

        if (unit.getDetails().isEmpty())
            return new UnitExpressionIntLiteral(1);

        // TODO add UnitRaiseExpression more (don't always split units into single powers)

        // Flatten into a list of units, true for numerator, false for denom:
        List<Pair<SingleUnit, Boolean>> singleUnits = unit.getDetails().entrySet().stream().flatMap((Entry<@KeyFor("unit.getDetails()") SingleUnit, Integer> e) -> Utility.replicate(Math.abs(e.getValue()), new Pair<>((SingleUnit)e.getKey(), e.getValue() > 0)).stream()).collect(Collectors.toList());

        // Now shuffle them and form a compound expression:
        Collections.shuffle(singleUnits, new Random(r.nextLong()));

        // If just one, return it:

        UnitExpression u;

        if (singleUnits.get(0).getSecond())
            u = new SingleUnitExpression(singleUnits.get(0).getFirst().getName());
        else
        {
            if (r.nextBoolean())
                u = new UnitRaiseExpression(new SingleUnitExpression(singleUnits.get(0).getFirst().getName()), new UnitExpressionIntLiteral(-1));
            else
                u = new UnitDivideExpression(new UnitExpressionIntLiteral(1), new SingleUnitExpression(singleUnits.get(0).getFirst().getName()));
        }

        for (int i = 1; i < singleUnits.size(); i++)
        {
            Pair<SingleUnit, Boolean> s = singleUnits.get(i);
            SingleUnitExpression sExp = new SingleUnitExpression(s.getFirst().getName());
            if (s.getSecond())
            {
                // Times.  Could join it to existing one:
                if (u instanceof UnitTimesExpression && r.nextBoolean())
                {
                    List<@Recorded UnitExpression> prevOperands = new ArrayList<>(((UnitTimesExpression)u).getOperands());

                    prevOperands.add(sExp);
                    u = new UnitTimesExpression(ImmutableList.copyOf(prevOperands));
                }
                else
                {
                    // Make a new one:
                    ImmutableList<UnitExpression> operands;
                    if (r.nextBoolean())
                        operands = ImmutableList.of(u, sExp);
                    else
                        operands = ImmutableList.of(sExp, u);
                    u = new UnitTimesExpression(operands);
                }
            }
            else
            {
                // Divide.  Could join it to existing:
                if (u instanceof UnitDivideExpression && r.nextBoolean())
                {
                    UnitDivideExpression div = (UnitDivideExpression) u;
                    ImmutableList<UnitExpression> newDenom = ImmutableList.of(div.getDenominator(), sExp);
                    u = new UnitDivideExpression(div.getNumerator(), new UnitTimesExpression(newDenom));
                }
                else
                {
                    u = new UnitDivideExpression(u, sExp);
                }
            }
        }

        return u;
    }

    protected final CallExpression call(String name, Expression... args)
    {
        try
        {
            return new CallExpression(FunctionList.getFunctionLookup(new UnitManager()), name, args);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
