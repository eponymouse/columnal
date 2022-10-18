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
import javafx.scene.text.Text;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.styled.StyledString;
import xyz.columnal.styled.StyledString.Builder;
import xyz.columnal.styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An expression with mixed operators, which make it invalid.  Can't be run, but may be
 * used while editing and for loading/saving invalid expressions.
 */
public class InvalidOperatorExpression extends NonOperatorExpression
{
    private final ImmutableList<@Recorded Expression> items;

    public InvalidOperatorExpression(ImmutableList<@Recorded Expression> operands)
    {
        this.items = operands;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Error should have been recorded elsewhere
        //onError.recordError(this, StyledString.s("Mixed or invalid operators in expression"));
        return null; // Invalid expressions can't type check
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws InternalException
    {
        throw new InternalException("Cannot get value for invalid expression");
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        if (saveDestination.needKeywords())
            return "@invalidops(" + items.stream().map(x -> x.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames)).collect(Collectors.joining(", "))+ ")";
        else
            return items.stream().map(x -> x.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames)).collect(Collectors.joining(""));
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        InvalidOperatorExpression that = (InvalidOperatorExpression) o;

        return items.equals(that.items);
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        Builder r = StyledString.builder();

        for (Expression item : items)
        {
            r.append(item.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler));
        }
        
        class ErrorStyle extends Style<ErrorStyle>
        {
            protected ErrorStyle()
            {
                super(ErrorStyle.class);
            }

            @Override
            protected @OnThread(Tag.FXPlatform) void style(Text t)
            {
                t.getStyleClass().add("expression-error");
            }

            @Override
            protected ErrorStyle combine(ErrorStyle with)
            {
                return this;
            }

            @Override
            protected boolean equalsStyle(ErrorStyle item)
            {
                return item instanceof ErrorStyle;
            }
        }
        
        return expressionStyler.styleExpression(r.build(StyledString.s(" ")).withStyle(new ErrorStyle()), this);
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new InvalidOperatorExpression(Utility.mapListI(items, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }

    public ImmutableList<@Recorded Expression> _test_getItems()
    {
        return items;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.invalidOps(this, items);
    }
}
