package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.DataItemPosition;
import records.data.TableId;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.datatype.TypeManager.TagInfo;
import records.transformations.expression.Expression.ColumnLookup.FoundTable;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.explanation.ExplanationLocation;
import records.data.TableAndColumnRenames;
import records.transformations.expression.function.ValueFunction.ArgumentExplanation;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.transformations.expression.visitor.ExpressionVisitorFlat;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.TypeExp.TypeError;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import records.transformations.expression.function.ValueFunction;

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
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        ValueFunction functionValue = Utility.cast(function.calculateValue(state).value, ValueFunction.class);

        ArrayList<ValueResult> paramValueResults = new ArrayList<>(arguments.size()); 
        @Value Object[] paramValues = new Object[arguments.size()];
        for (int i = 0; i < arguments.size(); i++)
        {
            @Recorded Expression arg = arguments.get(i);
            ValueResult r = arg.calculateValue(state);
            paramValueResults.add(r);
            paramValues[i] = r.value;
        }
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
                        @Nullable Pair<@Nullable TableId, ColumnId> details = getColumn(arg);
                        if (details != null && details.getFirst() != null)
                            return new ExplanationLocation(details.getFirst(), details.getSecond(), DataItemPosition.row(index));
                        else
                            return null;
                    }
                });
            }
            
            return result(state, functionValue.callRecord(paramValues, paramLocations.build()));
        }
        else
        {
            return result(functionValue.call(paramValues), state);
        }
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
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
                for (int i = 0; i < arguments.size(); i++)
                {
                    Expression argument = arguments.get(i);
                    ValueResult argMatch = argument.matchAsPattern(tuple[i], curState);
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
    public String save(SaveDestination saveDestination, BracketedStatus surround, @Nullable TypeManager typeManager, TableAndColumnRenames renames)
    {
        return (saveDestination == SaveDestination.SAVE_EXTERNAL ? "@call " : "") + function.save(saveDestination, BracketedStatus.NEED_BRACKETS, typeManager, renames) + "(" + arguments.stream().map(a -> a.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, typeManager, renames)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(function.toDisplay(BracketedStatus.NEED_BRACKETS, expressionStyler), StyledString.s("("), arguments.stream().map(a -> a.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler)).collect(StyledString.joining(", ")), StyledString.s(")")), this);
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
