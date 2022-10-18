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
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorFlat;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.Record;

import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

// Not a BinaryOpExpression because the field name by itself does not have a valid type.
public class FieldAccessExpression extends Expression
{
    private final @Recorded Expression lhsRecord;
    private final @ExpressionIdentifier String fieldName;

    public FieldAccessExpression(@Recorded Expression lhsRecord, @ExpressionIdentifier String fieldName)
    {
        this.lhsRecord = lhsRecord;
        this.fieldName = fieldName;
    }

    @SuppressWarnings("recorded")
    public static @Recorded Expression fromBinary(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        return rhs.visit(new ExpressionVisitorFlat<Expression>()
        {
            @Override
            protected Expression makeDef(Expression rhs)
            {
                return new InvalidOperatorExpression(ImmutableList.<@Recorded Expression>of(lhs, new InvalidIdentExpression("#"), rhs));
            }

            @Override
            public Expression ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                return new FieldAccessExpression(lhs, idents.get(idents.size() - 1));
            }
        });
    }

    @Override
    public @Nullable CheckedExp check(@Recorded FieldAccessExpression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState> lambda = ImplicitLambdaArg.detectImplicitLambda(this, ImmutableList.of(lhsRecord), typeState, onError);
        typeState = lambda.getSecond();
        
        CheckedExp lhsChecked = lhsRecord.check(dataLookup, typeState, ExpressionKind.EXPRESSION, locationInfo, onError);
        if (lhsChecked == null)
            return null;

        if (fieldName == null)
        {
            onError.recordError(fieldName, StyledString.s("Field name must be a valid name by itself"));
            return null;
        }
        
        @Recorded TypeExp fieldType = onError.recordTypeNN(this, new MutVar(this));
        TypeExp recordType = TypeExp.record(this, ImmutableMap.<@ExpressionIdentifier String, TypeExp>of(fieldName, fieldType), false);
        
        if (onError.recordError(this, TypeExp.unifyTypes(recordType, lhsChecked.typeExp)) == null)
            return null;
        
        return new CheckedExp(fieldType, typeState).applyToType(lambda.getFirst());
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        ImmutableList.Builder<ValueResult> subResults = ImmutableList.builderWithExpectedSize(1);
        ValueResult lhsResult = fetchSubExpression(lhsRecord, state, subResults);
        @Value Record record = Utility.cast(lhsResult.value, Record.class);

        if (fieldName == null)
            throw new InternalException("Field is not single name despite being after type-check: " + fieldName.toString());
        @Value Object result = record.getField(fieldName);
        return new ValueResult(result, state)
        {
            @Override
            public Explanation makeExplanation(Explanation.@Nullable ExecutionType overrideExecutionType) throws InternalException
            {
                Explanation lhsExplanation = lhsResult.makeExplanation(overrideExecutionType);
                ExplanationLocation lhsIsLoc = lhsExplanation.getResultIsLocation();
                ImmutableList<ExplanationLocation> directLocs;
                @Nullable ExplanationLocation usIsLoc;
                if (lhsIsLoc != null && lhsIsLoc.columnId == null)
                {
                    // If it's a table...
                    usIsLoc = new ExplanationLocation(lhsIsLoc.tableId, new ColumnId(fieldName), lhsIsLoc.rowIndex);
                    directLocs = ImmutableList.of(usIsLoc);
                }
                else
                {
                    usIsLoc = null;
                    directLocs = ImmutableList.of();
                }
                
                return new Explanation(FieldAccessExpression.this, overrideExecutionType != null ? overrideExecutionType : ExecutionType.VALUE, state, result, directLocs, usIsLoc)
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public @Nullable StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
                    {
                        return FieldAccessExpression.this.describe(value,this.executionType, evaluateState, hyperlinkLocation, expressionStyler, Utility.concatI(directLocs, extraLocations), skipIfTrivial);
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
                    {
                        return ImmutableList.of(lhsExplanation);
                    }

                    @Override
                    public boolean excludeChildrenIfTrivial()
                    {
                        return true;
                    }
                };
            };
        };
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.field(this, lhsRecord, fieldName);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String content = lhsRecord.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames) + "#" + fieldName;
        if (surround == BracketedStatus.NEED_BRACKETS)
            return "(" + content + ")";
        else
            return content;
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
        FieldAccessExpression that = (FieldAccessExpression) o;
        return lhsRecord.equals(that.lhsRecord) &&
            fieldName.equals(that.fieldName);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lhsRecord, fieldName);
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(lhsRecord.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler), StyledString.s("#" + fieldName)),this);
    }

    @Override
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        return lhsRecord.visit(new ExpressionVisitorFlat<Boolean>()
        {
            @Override
            protected Boolean makeDef(Expression expression)
            {
                return false;
            }

            @Override
            public Boolean ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                return Objects.equals(namespace,"table");
            }
        });
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new FieldAccessExpression(lhsRecord.replaceSubExpression(toReplace, replaceWith), fieldName);
    }
}
