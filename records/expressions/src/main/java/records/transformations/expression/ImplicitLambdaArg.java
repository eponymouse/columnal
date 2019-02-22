package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import records.transformations.expression.function.ValueFunction;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * The question mark that makes an implicit lambda, e.g. in ? < 5
 */
public class ImplicitLambdaArg extends NonOperatorExpression
{
    private static final AtomicInteger nextId = new AtomicInteger(0);
    
    private final int id;
    
    public ImplicitLambdaArg()
    {
        this.id = nextId.incrementAndGet();
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        ImmutableList<TypeExp> questTypes = typeState.findVarType(getVarName());
        if (questTypes == null || questTypes.isEmpty())
            throw new UserException("? is not a valid expression by itself");
        // Pick last one in case of nested definitions:
        return onError.recordType(this, ExpressionKind.EXPRESSION, typeState, questTypes.get(questTypes.size() - 1));
    }

    protected String getVarName()
    {
        return "?" + id;
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        return new Pair<>(state.get(getVarName()), state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "?";
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(GeneralExpressionEntry.load(Keyword.QUEST));
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        return o instanceof ImplicitLambdaArg;
    }

    @Override
    public int hashCode()
    {
        return 1;
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s("?");
    }

    // If any of the list are implicit lambda args ('?'), returns a new type state
    // with a type for '?' and a wrap function which will turn the item into a function.
    // If none are, returns null and unaltered type state.
    protected static Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState> detectImplicitLambda(Expression src, ImmutableList<@Recorded Expression> args, TypeState typeState, ErrorAndTypeRecorder errorAndTypeRecorder)
    {
        ImmutableList<@Recorded ImplicitLambdaArg> lambdaArgs = getLambdaArgsFrom(args);
        
        if (!lambdaArgs.isEmpty())
        {
            ImmutableList<TypeExp> argTypes = Utility.mapListI(lambdaArgs, arg -> new MutVar(arg));
            return new Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState>(t -> errorAndTypeRecorder.recordTypeNN(src, TypeExp.function(src, argTypes, t)), typeState.addImplicitLambdas(lambdaArgs, argTypes));
        }
        else
        {
            return new Pair<>(null, typeState);
        }
    }
    
    /**
     * Given a list which may contain one or more implicit
     * arguments, form a function which takes a tuple of
     * them as arguments and stores them in evaluate state
     * before calling the given body.
     * If no lambda args present, calls body directly.
     * 
     * @param possibleArgs A list, some of which may be implicitlambdaarg (but not all)
     * @param body
     * @return
     */
    @OnThread(Tag.Simulation)
    public static @Value Object makeImplicitFunction(ImmutableList<@Recorded Expression> possibleArgs, EvaluateState state, SimulationFunction<EvaluateState, @Value Object> body) throws UserException, InternalException
    {
        ImmutableList<@Recorded ImplicitLambdaArg> lambdaArgs = getLambdaArgsFrom(possibleArgs);
        if (lambdaArgs.size() == 0)
        {
            // Not an implicit lambda
            return body.apply(state);
        }
        else if (lambdaArgs.size() == 1)
        {
            // Takes non-tuple parameter:
            return ValueFunction.value(new ValueFunction()
            {
                @Override
                public @OnThread(Tag.Simulation) @Value Object call() throws InternalException, UserException
                {
                    EvaluateState argState = state.add(lambdaArgs.get(0).getVarName(), arg(0));
                    return body.apply(argState);
                }
            });
        }
        else
        {
            // Takes tuple parameter:
            return ValueFunction.value(new ValueFunction()
            {
                @Override
                public @OnThread(Tag.Simulation) @Value Object call() throws InternalException, UserException
                {
                    EvaluateState argState = state;
                    for (int i = 0; i < lambdaArgs.size(); i++)
                    {
                        argState = argState.add(lambdaArgs.get(i).getVarName(), arg(i));
                    }
                    return body.apply(argState);
                }
            });
        }
    }

    public static ImmutableList<@Recorded ImplicitLambdaArg> getLambdaArgsFrom(ImmutableList<@Recorded Expression> possibleArgs)
    {
        return Utility.<@Recorded Expression, @Recorded ImplicitLambdaArg>filterClass(possibleArgs.stream(), (Class<@Recorded ImplicitLambdaArg>)ImplicitLambdaArg.class).collect(ImmutableList.<@Recorded ImplicitLambdaArg>toImmutableList());
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }
}
