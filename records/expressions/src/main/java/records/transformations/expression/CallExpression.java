package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataItemPosition;
import records.data.explanation.ExplanationLocation;
import records.data.TableAndColumnRenames;
import records.data.ValueFunction.ArgumentLocation;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import records.typeExp.MutVar;
import records.typeExp.TupleTypeExp;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.StreamTreeBuilder;
import utility.TaggedValue;
import utility.Utility;
import records.data.ValueFunction;

import java.util.ArrayList;
import java.util.Collections;
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
    private @Nullable ValueFunction functionValue;

    public CallExpression(@Recorded Expression function, ImmutableList<@Recorded Expression> args)
    {
        this.function = function;
        this.arguments = args;
    }
    
    // Used for testing, and for creating quick recipe functions:
    // Creates a call to a named function
    @SuppressWarnings("recorded")
    public CallExpression(UnitManager mgr, String functionName, Expression... args)
    {
        this(nonNullLookup(mgr, functionName), ImmutableList.copyOf(args));
    }

    private static Expression nonNullLookup(UnitManager mgr, String functionName)
    {
        try
        {
            FunctionDefinition functionDefinition = FunctionList.lookup(mgr, functionName);
            if (functionDefinition != null)
                return new StandardFunction(functionDefinition);
        }
        catch (InternalException e)
        {
            Log.log(e);
        }
        return InvalidIdentExpression.identOrUnfinished(functionName);
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        ImmutableList.Builder<CheckedExp> paramTypesBuilder = ImmutableList.builderWithExpectedSize(arguments.size());
        for (Expression argument : arguments)
        {
            @Nullable CheckedExp checkedExp = argument.check(dataLookup, state, LocationInfo.UNIT_DEFAULT, onError);
            if (checkedExp == null)
                return null;
            state = checkedExp.typeState;
            paramTypesBuilder.add(checkedExp);
        }
        ImmutableList<CheckedExp> paramTypes = paramTypesBuilder.build();
        @Nullable CheckedExp functionType = function.check(dataLookup, state, LocationInfo.UNIT_DEFAULT, onError);
        if (functionType == null)
            return null;
        
        if (functionType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(this, StyledString.s("Call target cannot be a pattern"));
            return null;
        }
        // Param can only be a pattern if the function is a constructor expression:
        ExpressionKind expressionKind = ExpressionKind.EXPRESSION;
        for (CheckedExp paramType : paramTypes)
        {
            if (paramType.expressionKind == ExpressionKind.PATTERN)
            {
                if (!(function instanceof ConstructorExpression))
                {
                    onError.recordError(this, StyledString.s("Function parameter cannot be a pattern."));
                    return null;
                }
                expressionKind = ExpressionKind.PATTERN;
            }
        }
        
        TypeExp returnType = new MutVar(this);
        
        TypeExp actualCallType = TypeExp.function(this, Utility.<CheckedExp, TypeExp>mapListI(paramTypes, p -> p.typeExp), returnType);
        
        Either<StyledString, TypeExp> temp = TypeExp.unifyTypes(functionType.typeExp, actualCallType);
        @Nullable TypeExp checked = onError.recordError(this, temp);
        if (checked == null)
        {
            // Check after unification attempted, because that will have constrained
            // to list if possible (and not, if not)
            @Nullable ImmutableList<TypeExp> functionArgTypeExp = TypeExp.getFunctionArg(functionType.typeExp);
            boolean takesList = functionArgTypeExp != null && functionArgTypeExp.size() == 1 && TypeExp.isList(functionArgTypeExp.get(0));
            if (takesList)
            {
                Expression param = arguments.get(0);
                TypeExp prunedParam = paramTypes.get(0).typeExp.prune();

                if (!TypeExp.isList(prunedParam) && param instanceof ColumnReference && ((ColumnReference)param).getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW)
                {
                    ColumnReference colRef = (ColumnReference)param;
                    // Offer to turn a this-row column reference into whole column:
                    onError.recordQuickFixes(this, Collections.<QuickFix<Expression, ExpressionSaver>>singletonList(
                        new QuickFix<>("fix.wholeColumn", this, () -> {
                            @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
                            CallExpression newCall = new CallExpression(function, ImmutableList.of(new ColumnReference(colRef.getTableId(), colRef.getColumnId(), ColumnReferenceType.WHOLE_COLUMN)));
                            return newCall;
                        }
                        )
                    ));
                }
                if (prunedParam instanceof TupleTypeExp && param instanceof TupleExpression)
                {
                    // Offer to turn tuple into a list:
                    Expression replacementParam = new ArrayExpression(((TupleExpression)param).getMembers());
                    onError.recordQuickFixes(this, Collections.<QuickFix<Expression, ExpressionSaver>>singletonList(
                            new QuickFix<>("fix.squareBracketAs", this, () -> {
                                @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
                                CallExpression newCall = new CallExpression(function, ImmutableList.of(replacementParam));
                                return newCall;
                            })
                    ));
                }
                // Although we may want to pass a tuple as a single-item list, it's much less likely
                // than the missing list brackets case, hence the else here:
                else if (!TypeExp.isList(prunedParam))
                {
                    // Offer to make a list:
                    @SuppressWarnings("recorded")
                    Expression replacementParam = new ArrayExpression(ImmutableList.of(param));
                    onError.recordQuickFixes(this, Collections.<QuickFix<Expression, ExpressionSaver>>singletonList(
                        new QuickFix<>("fix.singleItemList", this, () -> {
                            @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
                            CallExpression newCall = new CallExpression(function, ImmutableList.of(replacementParam));
                            return newCall;
                        })
                    ));
                }
                
                
            }
            
            return null;
        }
        
        return onError.recordType(this, expressionKind, state, returnType);
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        ValueFunction functionValue = Utility.cast(function.getValue(state).getFirst(), ValueFunction.class);

        @Value Object[] paramValues = new Object[arguments.size()];
        for (int i = 0; i < arguments.size(); i++)
        {
            @Recorded Expression arg = arguments.get(i);
            paramValues[i] = arg.getValue(state).getFirst();
        }
        ArgumentLocation[] paramLocations;
        if (state.recordExplanation())
        {
            this.functionValue = functionValue;
            this.functionValue.setRecordExplanation(true);
            
            paramLocations = new ArgumentLocation[arguments.size()];
            for (int i = 0; i < arguments.size(); i++)
            {
                @Recorded Expression arg = arguments.get(i);
                if (arg instanceof ColumnReference)
                {
                    paramLocations[i] = new ArgumentLocation()
                    {
                        @Override
                        public @Nullable ImmutableList<ExplanationLocation> getValueLocation() throws InternalException
                        {
                            return arg.getBooleanExplanation();
                        }

                        @Override
                        public @Nullable ImmutableList<ExplanationLocation> getListElementLocation(int index)
                        {
                            ExplanationLocation location = ((ColumnReference) arg).getElementLocation(DataItemPosition.row(index));
                            if (location == null)
                                return null;
                            else
                                return ImmutableList.of(location);
                        }
                    };
                }
                else
                {
                    paramLocations[i] = new ArgumentLocation()
                    {
                        @Override
                        public @Nullable ImmutableList<ExplanationLocation> getValueLocation() throws InternalException
                        {
                            return arg.getBooleanExplanation();
                        }

                        @Override
                        public @Nullable ImmutableList<ExplanationLocation> getListElementLocation(int index)
                        {
                            return null;
                        }
                    };
                }
            }
        }
        else
        {
            paramLocations = null;
        }
        return new Pair<>(functionValue.call(paramValues, paramLocations), state);
    }

    @Override
    public @Nullable ImmutableList<ExplanationLocation> getBooleanExplanation() throws InternalException
    {
        return functionValue == null ? null : functionValue.getExplanation();
    }

    @Override
    public @OnThread(Tag.Simulation) @Nullable EvaluateState matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        if (function instanceof ConstructorExpression)
        {
            ConstructorExpression constructor = (ConstructorExpression) function;
            TaggedValue taggedValue = Utility.cast(value, TaggedValue.class);
            if (taggedValue.getTagIndex() != constructor.getTagIndex())
                return null;
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
                    curState = argument.matchAsPattern(tuple[i], curState);
                    if (curState == null)
                        return null;
                }
                return curState;
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
    public Stream<ColumnReference> allColumnReferences()
    {
        return arguments.stream().flatMap(a -> a.allColumnReferences());
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return (structured ? "@call " : "") + function.save(structured, BracketedStatus.MISC, renames) + "(" + arguments.stream().map(a -> a.save(structured, BracketedStatus.TOP_LEVEL, renames)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        return StyledString.concat(function.toDisplay(BracketedStatus.MISC), StyledString.s("("), arguments.stream().map(a -> a.toDisplay(BracketedStatus.TOP_LEVEL)).collect(StyledString.joining(", ")), StyledString.s(")"));
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();
        r.addAll(function.loadAsConsecutive(BracketedStatus.MISC));
        roundBracket(BracketedStatus.MISC, true, r, () -> r.addAll(new TupleExpression(arguments).loadAsConsecutive(BracketedStatus.DIRECT_ROUND_BRACKETED)));
        return r.stream();
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
            return function._test_allMutationPoints().<Pair<Expression, Function<Expression, Expression>>>map(p -> new Pair<>(p.getFirst(), newExp -> new CallExpression(p.getSecond().apply(newExp), arguments)));
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
}
