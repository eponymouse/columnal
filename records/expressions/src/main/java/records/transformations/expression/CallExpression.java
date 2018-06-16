package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
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
import utility.ValueFunction;

import java.util.Collections;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 11/12/2016.
 */
public class CallExpression extends Expression
{
    private final Expression function;
    private final Expression param;

    public CallExpression(Expression function, @Recorded Expression arg)
    {
        this.function = function;
        this.param = arg;
    }
    
    // Used for testing, and for creating quick recipe functions:
    // Creates a call to a named function
    @SuppressWarnings("recorded")
    public CallExpression(UnitManager mgr, String functionName, Expression... args)
    {
        this(nonNullLookup(mgr, functionName), args.length == 1 ? args[0] : new TupleExpression(ImmutableList.copyOf(args)));
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
        return new IdentExpression(functionName);
    }

    @Override
    public @Nullable CheckedExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable CheckedExp paramType = param.check(dataLookup, state, onError);
        if (paramType == null)
            return null;
        @Nullable CheckedExp functionType = function.check(dataLookup, state, onError);
        if (functionType == null)
            return null;
        
        if (functionType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(this, StyledString.s("Call target cannot be a pattern"));
            return null;
        }
        // Param can only be a pattern if the function is a constructor expression:
        if (functionType.expressionKind == ExpressionKind.PATTERN && !(function instanceof ConstructorExpression))
        {
            onError.recordError(this, StyledString.s("Function parameter cannot be a pattern."));
            return null;
        }
        
        TypeExp returnType = new MutVar(this);
        TypeExp actualCallType = TypeExp.function(this, paramType.typeExp, returnType);
        
        Either<StyledString, TypeExp> temp = TypeExp.unifyTypes(functionType.typeExp, actualCallType);
        @Nullable TypeExp checked = onError.recordError(this, temp);
        if (checked == null)
        {
            // Check after unification attempted, because that will have constrained
            // to list if possible (and not, if not)
            boolean takesList = TypeExp.isList(paramType.typeExp);
            if (takesList)
            {
                TypeExp prunedParam = paramType.typeExp.prune();

                if (!TypeExp.isList(prunedParam) && param instanceof ColumnReference && ((ColumnReference)param).getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW)
                {
                    ColumnReference colRef = (ColumnReference)param;
                    // Offer to turn a this-row column reference into whole column:
                    onError.recordQuickFixes(this, Collections.singletonList(
                        new QuickFix<>("fix.wholeColumn", this, () ->
                            new CallExpression(function, new ColumnReference(colRef.getTableId(), colRef.getColumnId(), ColumnReferenceType.WHOLE_COLUMN))
                        )
                    ));
                }
                if (prunedParam instanceof TupleTypeExp && param instanceof TupleExpression)
                {
                    // Offer to turn tuple into a list:
                    Expression replacementParam = new ArrayExpression(((TupleExpression)param).getMembers());
                    onError.recordQuickFixes(this, Collections.singletonList(
                            new QuickFix<>("fix.squareBracketAs", this, () -> new CallExpression(function, replacementParam))
                    ));
                }
                // Although we may want to pass a tuple as a single-item list, it's much less likely
                // than the missing list brackets case, hence the else here:
                else if (!TypeExp.isList(prunedParam))
                {
                    // Offer to make a list:
                    @SuppressWarnings("recorded")
                    Expression replacementParam = new ArrayExpression(ImmutableList.of(param));
                    onError.recordQuickFixes(this, Collections.singletonList(
                        new QuickFix<>("fix.singleItemList", this, () -> new CallExpression(function, replacementParam))
                    ));
                }
                
                
            }
            
            return null;
        }
        return onError.recordType(this, paramType.expressionKind, paramType.typeState, returnType);
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        ValueFunction functionValue = Utility.cast(function.getValue(state).getFirst(), ValueFunction.class);
            
        return new Pair<>(functionValue.call(param.getValue(state).getFirst()), state);
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
            return param.matchAsPattern(inner, state);
        }
        
        return super.matchAsPattern(value, state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return param.allColumnReferences();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "@call " + function.save(BracketedStatus.MISC, renames) + "(" + param.save(BracketedStatus.DIRECT_ROUND_BRACKETED, renames) + ")";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        return StyledString.concat(function.toDisplay(BracketedStatus.MISC), StyledString.s("("), param.toDisplay(BracketedStatus.DIRECT_ROUND_BRACKETED), StyledString.s(")"));
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();
        r.addAll(function.loadAsConsecutive(BracketedStatus.MISC));
        roundBracket(BracketedStatus.MISC, true, r, () -> r.addAll(param.loadAsConsecutive(BracketedStatus.DIRECT_ROUND_BRACKETED)));
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
        return Stream.concat(
            function._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newExp -> new CallExpression(p.getSecond().apply(newExp), param))),
            param._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newExp -> new CallExpression(function, p.getSecond().apply(newExp))))
        );
    }

    @SuppressWarnings("recorded")
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        Expression badParam = param._test_typeFailure(r, newExpressionOfDifferentType, unitManager);
        return badParam == null ? null : new CallExpression(function, badParam);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallExpression that = (CallExpression) o;

        if (!function.equals(that.function)) return false;
        return param.equals(that.param);
    }

    @Override
    public int hashCode()
    {
        int result = function.hashCode();
        result = 31 * result + param.hashCode();
        return result;
    }

    public Expression getFunction()
    {
        return function;
    }

    public Expression getParam()
    {
        return param;
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new CallExpression(function.replaceSubExpression(toReplace, replaceWith), param.replaceSubExpression(toReplace, replaceWith));
    }
}
