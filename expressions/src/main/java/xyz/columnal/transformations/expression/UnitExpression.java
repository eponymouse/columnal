/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.UnitLexer;
import xyz.columnal.grammar.UnitParser;
import xyz.columnal.grammar.UnitParser.SingleContext;
import xyz.columnal.grammar.UnitParser.UnbracketedUnitContext;
import xyz.columnal.grammar.UnitParser.UnitContext;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.ComparableEither;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Utility;

import java.util.Map.Entry;
import java.util.stream.Stream;

public abstract class UnitExpression implements StyledShowable, Replaceable<UnitExpression>
{
    @SuppressWarnings("recorded")
    public static @Recorded UnitExpression load(Unit unit)
    {
        ImmutableList<UnitExpression> top = unit.getDetails().entrySet().stream()
            .filter((Entry<@KeyFor("unit.getDetails()") SingleUnit, Integer> p) -> p.getValue() > 0)
            .<UnitExpression>map((Entry<@KeyFor("unit.getDetails()") SingleUnit, Integer> p) -> {
                SingleUnitExpression single = new SingleUnitExpression(p.getKey().getName());
                return p.getValue().intValue() == 1 ? single : new UnitRaiseExpression(single, new UnitExpressionIntLiteral(p.getValue().intValue()));
            }).collect(ImmutableList.<UnitExpression>toImmutableList());
        
        UnitExpression r = top.isEmpty() ? new UnitExpressionIntLiteral(1) : (top.size() == 1 ? top.get(0) : new UnitTimesExpression(top));
        
        ImmutableList<UnitExpression> bottom = unit.getDetails().entrySet().stream()
            .filter((Entry<@KeyFor("unit.getDetails()")SingleUnit, Integer> p) -> p.getValue() < 0)
            .<UnitExpression>map((Entry<@KeyFor("unit.getDetails()")SingleUnit, Integer> p) -> {
                SingleUnitExpression single = new SingleUnitExpression(p.getKey().getName());
                return p.getValue().intValue() == -1 ? single : new UnitRaiseExpression(single, new UnitExpressionIntLiteral(0 - p.getValue().intValue()));
            }).collect(ImmutableList.<UnitExpression>toImmutableList());
        
        if (bottom.isEmpty())
            return r;
        else if (bottom.size() == 1)
            return new UnitDivideExpression(r, bottom.get(0));
        else
            return new UnitDivideExpression(r, new UnitTimesExpression(bottom));
    }

    @SuppressWarnings("recorded") // Don't record when loading from Jelly
    public static @Recorded UnitExpression load(JellyUnit unit)
    {
        ImmutableList<UnitExpression> top = unit.getDetails().entrySet().stream()
            .filter((Entry<@KeyFor("unit.getDetails()") ComparableEither<String, SingleUnit>, Integer> p) -> p.getValue() > 0)
            .<UnitExpression>map((Entry<@KeyFor("unit.getDetails()") ComparableEither<String, SingleUnit>, Integer> p) -> {
                UnitExpression single = p.getKey().either(n -> InvalidSingleUnitExpression.identOrUnfinished(n), u -> new SingleUnitExpression(u.getName()));
                return p.getValue().intValue() == 1 ? single : new UnitRaiseExpression(single, new UnitExpressionIntLiteral(p.getValue().intValue()));
            }).collect(ImmutableList.<UnitExpression>toImmutableList());

        UnitExpression r = top.isEmpty() ? new UnitExpressionIntLiteral(1) : (top.size() == 1 ? top.get(0) : new UnitTimesExpression(top));

        ImmutableList<UnitExpression> bottom = unit.getDetails().entrySet().stream()
            .filter((Entry<@KeyFor("unit.getDetails()")ComparableEither<String, SingleUnit>, Integer> p) -> p.getValue() < 0)
            .<UnitExpression>map((Entry<@KeyFor("unit.getDetails()")ComparableEither<String, SingleUnit>, Integer> p) -> {
                UnitExpression single = p.getKey().either(n -> InvalidSingleUnitExpression.identOrUnfinished(n), u -> new SingleUnitExpression(u.getName()));
                return p.getValue().intValue() == -1 ? single : new UnitRaiseExpression(single, new UnitExpressionIntLiteral(0 - p.getValue().intValue()));
            }).collect(ImmutableList.<UnitExpression>toImmutableList());

        if (bottom.isEmpty())
            return r;
        else if (bottom.size() == 1)
            return new UnitDivideExpression(r, bottom.get(0));
        else
            return new UnitDivideExpression(r, new UnitTimesExpression(bottom));
    }
    
    // We mark as Recorded because constructors require
    // that, even though it's not actually reached GUI yet:
    @SuppressWarnings("recorded")
    public static @Recorded UnitExpression load(String text) throws InternalException, UserException
    {
        return loadUnbracketed(Utility.<UnbracketedUnitContext, UnitParser>parseAsOne(text, UnitLexer::new, UnitParser::new, p -> p.unitUse().unbracketedUnit()));
    }

    @SuppressWarnings("recorded")
    private static UnitExpression loadUnit(UnitContext ctx)
    {
        if (ctx.unbracketedUnit() != null)
        {
            return loadUnbracketed(ctx.unbracketedUnit());
        }
        else
        {
            SingleContext singleItem = ctx.single();
            if (singleItem.singleUnit() == null && singleItem.NUMBER() != null)
            {
                try
                {
                    return new UnitExpressionIntLiteral(Integer.parseInt(singleItem.NUMBER().getText()));
                }
                catch (NumberFormatException e)
                {
                    // Zero is guaranteed to be an error, so best default:
                    return new UnitExpressionIntLiteral(0);
                }
            }
            else
            {
                // TODO we need to support unit variables here.
                SingleUnitExpression singleUnit = new SingleUnitExpression(IdentifierUtility.fromParsed(singleItem.singleUnit()));
                if (ctx.single().NUMBER() != null)
                {
                    try
                    {
                        return new UnitRaiseExpression(singleUnit, new UnitExpressionIntLiteral(Integer.parseInt(ctx.single().NUMBER().getText())));
                    }
                    catch (NumberFormatException e)
                    {
                        return new UnitRaiseExpression(singleUnit, new InvalidSingleUnitExpression(ctx.single().NUMBER().getText()));
                    }
                }
                else
                {
                    return singleUnit;
                }
            }
        }
    }

    @SuppressWarnings("recorded")
    private static UnitExpression loadUnbracketed(UnbracketedUnitContext ctx)
    {
        if (ctx.divideBy() != null)
        {
            return new UnitDivideExpression(loadUnit(ctx.unit()), loadUnit(ctx.divideBy().unit()));
        }
        else if (ctx.timesBy() != null && ctx.timesBy().size() > 0)
        {
            return new UnitTimesExpression(Stream.<UnitContext>concat(Stream.<UnitContext>of(ctx.unit()), ctx.timesBy().stream().<UnitContext>map(t -> t.unit())).<UnitExpression>map(c -> loadUnit(c)).collect(ImmutableList.<UnitExpression>toImmutableList()));
        }
        else
        {
            return loadUnit(ctx.unit());
        }
    }
    
    public static class UnitLookupException extends Exception
    {
        public final @Nullable StyledString errorMessage;
        public final @Recorded UnitExpression errorItem;
        public final ImmutableList<QuickFix<UnitExpression>> quickFixes;

        public UnitLookupException(@Nullable StyledString errorMessage, @Recorded UnitExpression errorItem, ImmutableList<QuickFix<UnitExpression>> quickFixes)
        {
            this.errorMessage = errorMessage;
            this.errorItem = errorItem;
            this.quickFixes = quickFixes;
        }
    }

    public abstract JellyUnit asUnit(@Recorded UnitExpression this, UnitManager unitManager) throws UnitLookupException;
    
    public abstract String save(SaveDestination saveDestination, boolean topLevel);

    @Override
    public final StyledString toStyledString()
    {
        return StyledString.s(save(SaveDestination.TO_STRING, true));
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(@Nullable Object obj);

    public abstract boolean isEmpty();

    /**
     * Is this the integer literal 1 ?
     */
    public abstract boolean isScalar();

    @Override
    public String toString()
    {
        return save(SaveDestination.TO_STRING, true);
    }
}
