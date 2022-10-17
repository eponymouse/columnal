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
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.TypeExp.TypeError;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class OrExpression extends NaryOpShortCircuitExpression
{
    public OrExpression(List<@Recorded Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new OrExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "|";
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(ColumnLookup dataLookup, TypeState state, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return onError.recordType(this, state, checkAllOperandsSameTypeAndNotPatterns(TypeExp.bool(this), dataLookup, state, LocationInfo.UNIT_DEFAULT, onError, (typeAndExpression) -> {
            TypeExp ourType = typeAndExpression.getOurType();
            if (ourType == null || Objects.equals(ourType.prune(), TypeExp.bool(null)))
            {
                // We're fine or we have no idea.
                return ImmutableMap.<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>>of();
            }
            else
            {
                return ImmutableMap.<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>>of(typeAndExpression.getOurExpression(), new Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>(new TypeError(StyledString.concat(StyledString.s("Operands to '|' must be boolean but found "), ourType.toStyledString()), typeAndExpression.getAvailableTypesForError()), ImmutableList.of()));
            }
        }));
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueResult getValueNaryOp(EvaluateState state) throws EvaluationException, InternalException
    {
        ImmutableList.Builder<ValueResult> values = ImmutableList.builderWithExpectedSize(expressions.size());
        for (int i = 0; i < expressions.size(); i++)
        {
            Expression expression = expressions.get(i);
            Boolean b = Utility.cast(fetchSubExpression(expression, state, values).value, Boolean.class);
            if (b == true)
            {
                return result(DataTypeUtility.value(true), state, values.build());
            }
        }
        return result(DataTypeUtility.value(false), state, values.build());
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(TypeExp.bool(null))));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.or(this, expressions);
    }
}
