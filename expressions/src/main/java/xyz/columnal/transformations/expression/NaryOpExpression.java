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
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.TypeExp.TypeError;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 29/11/2016.
 */
public abstract class NaryOpExpression extends Expression
{
    protected final ImmutableList<@Recorded Expression> expressions;
    
    protected @Nullable ImmutableList<ExplanationLocation> booleanExplanation;

    public NaryOpExpression(List<@Recorded Expression> expressions)
    {
        this.expressions = ImmutableList.copyOf(expressions);
    }

    @Override
    public final @Nullable CheckedExp check(@Recorded NaryOpExpression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState> lambda = ImplicitLambdaArg.detectImplicitLambda(this, expressions, typeState, onError);
        typeState = lambda.getSecond();
        @Nullable CheckedExp checked = checkNaryOp(dataLookup, typeState, kind, onError);
        return checked == null ? null : checked.applyToType(lambda.getFirst());
    }

    public abstract @Nullable CheckedExp checkNaryOp(@Recorded NaryOpExpression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException;

    // Will be same length as expressions, if null use existing
    public final NaryOpExpression copy(List<@Nullable @Recorded Expression> replacements)
    {
        List<@Recorded Expression> newExps = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++)
        {
            @Nullable @Recorded Expression newExp = replacements.get(i);
            newExps.add(newExp != null ? newExp : expressions.get(i));
        }
        return copyNoNull(newExps);
    }

    public abstract NaryOpExpression copyNoNull(List<@Recorded Expression> replacements);

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return copyNoNull(Utility.mapList(expressions, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        StringBuilder s = new StringBuilder(surround == BracketedStatus.NEED_BRACKETS ? "(" : "");
        s.append(getSpecialPrefix());
        for (int i = 0; i < expressions.size(); i++)
        {
            if (i > 0)
                s.append(" ").append(saveOp(i - 1)).append(" ");
            s.append(expressions.get(i).save(saveDestination, BracketedStatus.NEED_BRACKETS, renames));
        }
        if (surround == BracketedStatus.NEED_BRACKETS)
            s.append(")");
        return s.toString();
    }

    @Override
    public StyledString toDisplay(DisplayType displayType, BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        StyledString.Builder s = new StyledString.Builder();
        s.append(surround == BracketedStatus.NEED_BRACKETS ? "(" : "");
        for (int i = 0; i < expressions.size(); i++)
        {
            if (i > 0)
                s.append(" ").append(saveOp(i - 1)).append(" ");
            s.append(expressions.get(i).toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler));
        }
        if (surround == BracketedStatus.NEED_BRACKETS)
            s.append(")");
        return expressionStyler.styleExpression(s.build(), this);
    }

    // Can be overridden by child classes to insert prefix before expression
    protected String getSpecialPrefix()
    {
        return "";
    }

    protected abstract String saveOp(int index);

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, expressions.size()).mapToObj(i ->
            expressions.get(i)._test_allMutationPoints().map(p -> new Pair<Expression, Function<Expression, Expression>>(p.getFirst(), (Expression exp) -> copy(makeNullList(i, p.getSecond().apply(exp)))))).flatMap(s -> s);
    }

    protected List<@Nullable Expression> makeNullList(int index, Expression newExp)
    {
        if (index < 0 || index >= expressions.size())
            throw new RuntimeException("makeNullList invalid " + index + " compared to " + expressions.size());
        ArrayList<@Nullable Expression> r = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++)
        {
            r.add(i == index ? newExp : null);
        }
        return r;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        NaryOpExpression that = (NaryOpExpression) o;

        if (!getOpList().equals(that.getOpList())) return false;
        return expressions.equals(that.expressions);
    }

    @Override
    public int hashCode()
    {
        return expressions.hashCode() + 31 * getOpList().hashCode();
    }

    public List<Expression> getChildren()
    {
        return Collections.unmodifiableList(expressions);
    }

    // Can be overriden by subclasses if needed:
    public String _test_getOperatorEntry(int index)
    {
        return saveOp(index);
    }

    private List<String> getOpList()
    {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < expressions.size() - 1; i++)
        {
            r.add(saveOp(i));
        }
        return r;
    }
    
    public static class TypeProblemDetails
    {
        // Same length as expressions.
        private final ImmutableList<Optional<TypeExp>> expressionTypes;
        public final ImmutableList<@Recorded Expression> expressions;
        final int index;

        TypeProblemDetails(ImmutableList<Optional<TypeExp>> expressionTypes, ImmutableList<@Recorded Expression> expressions, int index)
        {
            this.expressionTypes = expressionTypes;
            this.expressions = expressions;
            this.index = index;
        }

        @Pure
        public @Nullable TypeExp getOurType()
        {
            return getType(index);
        }
        
        public @Nullable TypeExp getType(int index)
        {
            return expressionTypes.get(index).orElse(null);
        }

        public @Recorded Expression getOurExpression()
        {
            return expressions.get(index);
        }
        
        public ImmutableList<TypeExp> getAvailableTypesForError()
        {
            return expressionTypes.stream().flatMap(t -> Utility.streamNullable(t.orElse(null))).collect(ImmutableList.<TypeExp>toImmutableList());
        }
    }
    
    protected interface CustomError
    {
        ImmutableMap<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>> getCustomErrorAndFix(TypeProblemDetails typeProblemDetails);
    }
    
    public @Nullable TypeExp checkAllOperandsSameTypeAndNotPatterns(TypeExp target, ColumnLookup data, TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError, CustomError getCustomErrorAndFix) throws InternalException, UserException
    {
        boolean allValid = true;
        ArrayList<@Nullable Pair<@Nullable TypeError, TypeExp>> unificationOutcomes = new ArrayList<>(expressions.size());
        for (@Recorded Expression expression : expressions)
        {
            @Nullable CheckedExp type = expression.check(data, state, ExpressionKind.EXPRESSION, locationInfo, onError);
            
            // Make sure to execute always (don't use short-circuit and with allValid):
            if (type == null)
            {
                allValid = false;
                unificationOutcomes.add(null);
            }
            else
            {
                Either<TypeError, TypeExp> unified = TypeExp.unifyTypes(target, type.typeExp);
                // We have to recreate either to add nullable constraint:
                unificationOutcomes.add(new Pair<>(unified.<@Nullable TypeError>either(err -> err, u -> null), type.typeExp));
                if (unified.isLeft())
                    allValid = false;
            }
        }

        ImmutableList<Optional<TypeExp>> expressionTypes = unificationOutcomes.stream().<Optional<TypeExp>>map((@Nullable Pair<@Nullable TypeError, TypeExp> p) -> p == null ? Optional.<TypeExp>empty() : Optional.<TypeExp>of(p.getSecond())).collect(ImmutableList.<Optional<TypeExp>>toImmutableList());

        if (!allValid)
        {
            for (int i = 0; i < expressions.size(); i++)
            {
                Expression expression = expressions.get(i);
                ImmutableMap<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>> customErrors = getCustomErrorAndFix.getCustomErrorAndFix(new TypeProblemDetails(expressionTypes, expressions, i));
                @Nullable TypeError unifyError = unificationOutcomes.get(i) != null ? unificationOutcomes.get(i).getFirst() : null;
                boolean recordedCustomError = false;
                for (Entry<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>> entry : customErrors.entrySet())
                {
                    if (entry.getValue().getFirst() != null)
                    {
                        onError.recordError(entry.getKey(), Either.left(entry.getValue().getFirst()));
                        recordedCustomError = true;
                    }
                    else if (!entry.getValue().getSecond().isEmpty() && (entry.getKey() != expression || unifyError != null))
                    {
                        // Hack to ensure there is an error:
                        onError.recordError(entry.getKey(), StyledString.s(" "));
                        recordedCustomError = true;
                    }
                    if (!entry.getValue().getSecond().isEmpty())
                        onError.recordQuickFixes(entry.getKey(), entry.getValue().getSecond());
                }
                
                if (!recordedCustomError && unifyError != null)
                    onError.recordError(expression, unifyError.getMessage());
            }
        }
        
        return allValid ? target : null;
    }
}
