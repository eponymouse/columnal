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
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.transformations.expression.Expression.ColumnLookup.FoundTable;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.transformations.expression.function.ValueFunction.ArgumentExplanation;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.function.StandardFunctionDefinition;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorFlat;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.TypeExp.TypeError;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 11/12/2016.
 */
public class CallExpression extends Expression
{
    private final @Recorded Expression function;
    private final ImmutableList<@Recorded Expression> arguments;

    public CallExpression(@Recorded Expression function, ImmutableList<@Recorded Expression> args)
    {
        this.function = function;
        this.arguments = args;
    }
    
    // Used for testing, and for creating quick recipe functions:
    // Creates a call to a named function
    @SuppressWarnings("recorded")
    public CallExpression(FunctionLookup functionLookup, String functionName, Expression... args)
    {
        this(nonNullLookup(functionLookup, functionName), ImmutableList.copyOf(args));
    }

    private static Expression nonNullLookup(FunctionLookup functionLookup, String functionName)
    {
        try
        {
            StandardFunctionDefinition functionDefinition = functionLookup.lookup(functionName);
            if (functionDefinition != null)
                return IdentExpression.function(functionDefinition.getFullName());
        }
        catch (InternalException e)
        {
            Log.log(e);
        }
        return InvalidIdentExpression.identOrUnfinished(functionName);
    }

    @Override
    public @Nullable CheckedExp check(@Recorded CallExpression this, ColumnLookup dataLookup, TypeState state, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        ImmutableList.Builder<CheckedExp> paramTypesBuilder = ImmutableList.builderWithExpectedSize(arguments.size());
        
        if (!(function instanceof IdentExpression))
        {
            onError.recordError(this, StyledString.concat(StyledString.s("Invalid call target: "), function.toStyledString()));
        }

        @Nullable CheckedExp functionType = function.check(dataLookup, state, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (functionType == null)
            return null;
        
        for (@Recorded Expression argument : arguments)
        {
            // Pattern can go through constructors, but arguments of functions must be expressions:
            ExpressionKind argKind = ((IdentExpression)function).getResolvedConstructor() != null ? kind : ExpressionKind.EXPRESSION;
            @Nullable CheckedExp checkedExp = argument.check(dataLookup, state, argKind, LocationInfo.UNIT_DEFAULT, onError);
            if (checkedExp == null)
                return null;
            state = checkedExp.typeState;
            paramTypesBuilder.add(checkedExp);
        }
        ImmutableList<CheckedExp> paramTypes = paramTypesBuilder.build();
        
        
        TypeExp returnType = new MutVar(this);

        @Nullable TypeExp checked = null;
        
        boolean doneIndivCheck = false;
        
        // If the function type is already known to be a function (which should be true for calling any standard function)
        // we can individually compare the parameters to give more localised type errors on each parameter:
        if (TypeExp.isFunction(functionType.typeExp))
        {
            @Nullable ImmutableList<TypeExp> functionArgTypeExp = TypeExp.getFunctionArg(functionType.typeExp);
            if (functionArgTypeExp != null)
            {
                if (functionArgTypeExp.size() == paramTypes.size())
                {
                    // Set to null if any fail
                    @Nullable ArrayList<@NonNull TypeExp> paramTypeExps = new ArrayList<>();
                    for (int i = 0; i < paramTypes.size(); i++)
                    {
                        Either<TypeError, TypeExp> paramOutcome = TypeExp.unifyTypes(functionArgTypeExp.get(i), paramTypes.get(i).typeExp);
                        TypeExp t = onError.recordError(arguments.get(i), paramOutcome);
                        if (t != null && paramTypeExps != null)
                            paramTypeExps.add(t);
                        else
                            paramTypeExps = null;
                        // We don't short circuit, so that the user sees all type errors in params.
                    }

                    @Nullable TypeExp functionResultTypeExp = TypeExp.getFunctionResult(functionType.typeExp);
                    if (functionResultTypeExp != null)
                    {
                        // Can't fail because unifying with blank MutVar:
                        TypeExp.unifyTypes(returnType, functionResultTypeExp);

                        // If success:
                        if (paramTypeExps != null)
                        {
                            checked = TypeExp.function(this, ImmutableList.copyOf(paramTypeExps), returnType);
                        }
                    }

                    doneIndivCheck = true;
                }
                else
                {
                    // Wrong number of parameters; say that explicitly:
                    onError.recordError(this, StyledString.s("Wrong number of parameters: expected " + functionArgTypeExp.size() + " but found " + arguments.size()));
                    doneIndivCheck = true;
                }
            }
        }

        if (!doneIndivCheck)
        {
            TypeExp actualCallType = TypeExp.function(this, Utility.<CheckedExp, TypeExp>mapListI(paramTypes, p -> p.typeExp), returnType);
            Either<TypeError, TypeExp> temp = TypeExp.unifyTypes(functionType.typeExp, actualCallType);
            checked = onError.recordError(this, temp);
        }
        
        if (checked == null)
        {
            // Check after unification attempted, because that will have constrained
            // to list if possible (and not, if not)
            @Nullable ImmutableList<TypeExp> functionArgTypeExp = TypeExp.getFunctionArg(functionType.typeExp);
            boolean takesList = functionArgTypeExp != null && functionArgTypeExp.size() == 1 && TypeExp.isList(functionArgTypeExp.get(0));
            if (takesList && arguments.size() == 1)
            {
                @Recorded Expression param = arguments.get(0);
                TypeExp prunedParam = paramTypes.get(0).typeExp.prune();

                if (!TypeExp.isList(prunedParam))
                {
                    @Nullable Pair<@Nullable TableId, ColumnId> columnDetails = getColumn(param);
                    if (columnDetails != null)
                    {
                        FoundTable table = dataLookup.getTable(columnDetails.getFirst());
                        if (table != null)
                        {
                            FoundTable tableNN = table;
                            // Offer to turn a this-row column reference into whole column:
                            onError.recordQuickFixes(this, Collections.<QuickFix<Expression>>singletonList(
                                    new QuickFix<>("fix.wholeColumn", this, () -> {
                                        @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
                                                CallExpression newCall = new CallExpression(function, ImmutableList.of(IdentExpression.makeEntireColumnReference(tableNN.getTableId(), columnDetails.getSecond())));
                                        return newCall;
                                    }
                                    )
                            ));
                        }
                    }
                }
            }
            else if (takesList && arguments.size() > 1)
            {
                // Offer to turn tuple into a list:
                Expression replacementParam = new ArrayExpression(arguments);
                onError.recordQuickFixes(this, Collections.<QuickFix<Expression>>singletonList(
                        new QuickFix<>("fix.squareBracketAs", this, () -> {
                            @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
                            CallExpression newCall = new CallExpression(function, ImmutableList.of(replacementParam));
                            return newCall;
                        })
                ));
            }
            
            return null;
        }
        
        return onError.recordType(this, state, returnType);
    }

    private @Nullable Pair<@Nullable TableId, ColumnId> getColumn(@Recorded Expression expression)
    {
        return expression.visit(new ExpressionVisitorFlat<@Nullable Pair<@Nullable TableId, ColumnId>>()
        {
            @Override
            protected @Nullable Pair<@Nullable TableId, ColumnId> makeDef(Expression expression)
            {
                return null;
            }

            @Override
            public @Nullable Pair<@Nullable TableId, ColumnId> ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                if (Objects.equals(namespace, "column"))
                {
                    if (idents.size() == 2)
                        return new Pair<>(new TableId(idents.get(0)), new ColumnId(idents.get(1)));
                    else if (idents.size() == 1)
                        return new Pair<>(null, new ColumnId(idents.get(0)));
                }
                
                return null;
            }
        });
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        ValueFunction functionValue = Utility.cast(fetchSubExpression(function, state, ImmutableList.builder()).value, ValueFunction.class);

        ImmutableList.Builder<ValueResult> paramValueResultsBuilder = ImmutableList.builderWithExpectedSize(arguments.size()); 
        @Value Object[] paramValues = new Object[arguments.size()];
        for (int i = 0; i < arguments.size(); i++)
        {
            @Recorded Expression arg = arguments.get(i);
            paramValues[i] = fetchSubExpression(arg, state, paramValueResultsBuilder).value;
        }
        ImmutableList<ValueResult> paramValueResults = paramValueResultsBuilder.build();
        if (state.recordExplanation())
        {
            ImmutableList.Builder<ArgumentExplanation> paramLocations = ImmutableList.builderWithExpectedSize(arguments.size());
            for (int i = 0; i < arguments.size(); i++)
            {
                int iFinal = i;
                @Recorded Expression arg = arguments.get(i);
                paramLocations.add(new ArgumentExplanation()
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public Explanation getValueExplanation() throws InternalException
                    {
                        return paramValueResults.get(iFinal).makeExplanation(null);
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public @Nullable ExplanationLocation getListElementLocation(int index) throws InternalException
                    {
                        ExplanationLocation details = paramValueResults.get(iFinal).makeExplanation(null).getResultIsLocation();
                        if (details != null && details.rowIndex == null)
                            return new ExplanationLocation(details.tableId, details.columnId, DataItemPosition.row(index));
                        else
                            return null;
                    }
                });
            }
            
            try
            {
                return result(state, functionValue.callRecord(paramValues, paramLocations.build()));
            }
            catch (UserException e)
            {
                throw new EvaluationException(e, this, ExecutionType.VALUE, state, paramValueResults);
            }
        }
        else
        {
            try
            {
                return result(functionValue.call(paramValues), state);
            }
            catch (UserException e)
            {
                throw new EvaluationException(e, this, ExecutionType.VALUE, state, ImmutableList.of());
            }
        }
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, EvaluationException
    {
        if (!(function instanceof IdentExpression))
            throw new InternalException("Matching invalid call target as pattern: " + function.toString());
        TagInfo tag = ((IdentExpression)function).getResolvedConstructor();
        if (tag != null)
        {
            
            @Value TaggedValue taggedValue = Utility.cast(value, TaggedValue.class);
            if (taggedValue.getTagIndex() != tag.tagIndex)
                return explanation(DataTypeUtility.value(false), ExecutionType.MATCH, state, ImmutableList.of(), ImmutableList.of(), false);
            // If we do match, go to the inner:
            @Nullable @Value Object inner = taggedValue.getInner();
            if (inner == null)
                throw new InternalException("Matching missing value against tag with inner pattern");
            if (value instanceof Object[])
            {
                @Value Object[] tuple = Utility.castTuple(value, arguments.size());
                EvaluateState curState = state;
                ImmutableList.Builder<ValueResult> argResults = ImmutableList.builderWithExpectedSize(arguments.size());
                for (int i = 0; i < arguments.size(); i++)
                {
                    Expression argument = arguments.get(i);
                    ValueResult argMatch = matchSubExpressionAsPattern(argument, tuple[i], curState, argResults);
                    if (Utility.cast(argMatch.value, Boolean.class) == false)
                        return explanation(DataTypeUtility.value(false), ExecutionType.MATCH, state, ImmutableList.of(), ImmutableList.of(), false);
                    curState = argMatch.evaluateState;
                }
                return explanation(DataTypeUtility.value(true), ExecutionType.MATCH, curState, ImmutableList.of(), ImmutableList.of(), false);
            }
            else if (arguments.size() == 1)
            {
                return arguments.get(0).matchAsPattern(inner, state);
            }
            else
            {
                throw new InternalException("Matching tag argument (size > 1) against non-tuple");
            }
        }
        
        return super.matchAsPattern(value, state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return (saveDestination.needKeywords() ? "@call " : "") + function.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames) + "(" + arguments.stream().map(a -> a.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public StyledString toDisplay(DisplayType displayType, BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(function.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler), StyledString.s("("), arguments.stream().map(a -> a.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler)).collect(StyledString.joining(", ")), StyledString.s(")")), this);
    }

    /*
        @Override
        public SingleLoader<Expression, ExpressionSaver, OperandNode<Expression, ExpressionSaver>> loadAsSingle()
        {
            // If successfully type-checked:
            if (definition != null)
            {
                @NonNull FunctionDefinition definitionFinal = definition;
                return (p, s) -> new FunctionNode(Either.right(definitionFinal), s, param, p);
            }
            else
                return (p, s) -> new FunctionNode(Either.left(functionName), s, param, p);
            @Override
            
        }
    */
    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        //return Stream.<Pair<Expression, Function<Expression, Expression>>>concat(
            return function._test_allMutationPoints().<Pair<Expression, Function<Expression, Expression>>>map(p -> new Pair<Expression, Function<Expression, Expression>>(p.getFirst(), newExp -> new CallExpression(p.getSecond().apply(newExp), arguments)));
            //param._test_allMutationPoints().<Pair<Expression, Function<Expression, Expression>>>map(p -> new Pair<>(p.getFirst(), newExp -> new CallExpression(function, p.getSecond().apply(newExp))))
        //);
    }

    @SuppressWarnings("recorded")
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int paramIndex = r.nextInt(arguments.size());
        Expression badParam = arguments.get(paramIndex)._test_typeFailure(r, newExpressionOfDifferentType, unitManager);
        if (badParam == null)
            return null;
        ArrayList<Expression> badCopy = new ArrayList<>(arguments);
        badCopy.set(paramIndex, badParam);
        return badParam == null ? null : new CallExpression(function, ImmutableList.copyOf(badCopy));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallExpression that = (CallExpression) o;

        if (!function.equals(that.function)) return false;
        return arguments.equals(that.arguments);
    }

    @Override
    public int hashCode()
    {
        int result = function.hashCode();
        result = 31 * result + arguments.hashCode();
        return result;
    }

    public Expression getFunction()
    {
        return function;
    }

    public ImmutableList<@Recorded Expression> getParams()
    {
        return arguments;
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new CallExpression(function.replaceSubExpression(toReplace, replaceWith), Utility.mapListI(arguments, a -> a.replaceSubExpression(toReplace, replaceWith)));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.call(this, function, arguments);
    }
}
