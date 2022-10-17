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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordExpression extends Expression
{
    // Has to be list of pairs to maintain the same order:
    // Also, duplicates are a type error not a syntax error, so duplicates are possible here.
    private final ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members;

    public RecordExpression(ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members)
    {
        this.members = members;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        HashMap<@ExpressionIdentifier String, TypeExp> fieldTypes = new HashMap<>();
        for (Pair<@ExpressionIdentifier String, @Recorded Expression> member : members)
        {
            CheckedExp checkedExp = member.getSecond().check(dataLookup, typeState, kind, LocationInfo.UNIT_DEFAULT, onError);
            if (checkedExp == null)
                return null;
            if (fieldTypes.put(member.getFirst(), checkedExp.typeExp) != null)
            {
                onError.recordError(this, StyledString.s("Duplicated field: \"" + member.getFirst() + "\""));
                return null;
            }
            typeState = checkedExp.typeState;
        }
        
        // Only complete if it's an expression; if it's a pattern then it's ok for fields to exist that we're not matching:
        CheckedExp checkedExp = new CheckedExp(onError.recordTypeNN(this, TypeExp.record(this, fieldTypes, kind == ExpressionKind.EXPRESSION)), typeState); 
        if (kind == ExpressionKind.PATTERN)
            checkedExp.requireEquatable();
        return checkedExp;
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        ImmutableList.Builder<ValueResult> valuesBuilder = ImmutableList.builderWithExpectedSize(members.size());
        // If it typechecked, assume no duplicate fields
        HashMap<@ExpressionIdentifier String, @Value Object> fieldValues = new HashMap<>();

        for (Pair<@ExpressionIdentifier String, @Recorded Expression> member : members)
        {
            fieldValues.put(member.getFirst(), fetchSubExpression(member.getSecond(), state, valuesBuilder).value);
        }
        
        return explanation(DataTypeUtility.value(new RecordMap(fieldValues)), ExecutionType.VALUE, state, valuesBuilder.build(), ImmutableList.of(), true);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, EvaluationException
    {
        @Value Record record = Utility.cast(value, Record.class);
        ImmutableList.Builder<ValueResult> itemValues = ImmutableList.builderWithExpectedSize(members.size());
        
        for (Pair<@ExpressionIdentifier String, Expression> member : members)
        {
            @Value Object fieldValue = record.getField(member.getFirst());
            ValueResult result = matchSubExpressionAsPattern(member.getSecond(), fieldValue, state, itemValues);
            if (Utility.cast(result.value, Boolean.class) == false)
                return explanation(DataTypeUtility.value(false), ExecutionType.MATCH, state, itemValues.build(), ImmutableList.of(), false);
            state = result.evaluateState;
        }

        return explanation(DataTypeUtility.value(true), ExecutionType.MATCH, state, itemValues.build(), ImmutableList.of(), true); 
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.record(this, members);
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
        RecordExpression that = (RecordExpression) o;
        return members.equals(that.members);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(members);
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
         return expressionStyler.styleExpression(StyledString.roundBracket(members.stream().map(s -> StyledString.concat(StyledString.s(s.getFirst() + ": "), s.getSecond().toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler))).collect(StyledString.joining(", "))), this);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "(" + members.stream().map(m -> m.getFirst() + ": " + m.getSecond().save(saveDestination, BracketedStatus.NEED_BRACKETS, renames)).collect(Collectors.joining(", ")) + ")";
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new RecordExpression(Utility.<Pair<@ExpressionIdentifier String, Expression>, Pair<@ExpressionIdentifier String, Expression>>mapListI(members, (Pair<@ExpressionIdentifier String, Expression> p) -> p.mapSecond(e -> e.replaceSubExpression(toReplace, replaceWith))));
    }
}
